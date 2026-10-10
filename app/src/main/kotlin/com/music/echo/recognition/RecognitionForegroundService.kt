package echo.music.iad1tya.recognition

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.music.shazamkit.models.RecognitionResult
import com.music.shazamkit.models.RecognitionStatus
import echo.music.iad1tya.MainActivity
import echo.music.iad1tya.R
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class RecognitionForegroundService : Service() {
  private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private var recognitionJob: Job? = null
  private var statusJob: Job? = null
  private var keepNotificationOnStop = false
  private var terminalStateHandled = false

  /** Listening to the phone's own audio (playback capture) instead of the microphone. */
  private var deviceAudio = false

  /** Started from the recognition screen (see [DeviceAudioCapture.start]). */
  private var fromApp = false
  private var projection: MediaProjection? = null
  private val projectionCallback =
    object : MediaProjection.Callback() {
      override fun onStop() {
        // The user revoked the capture (e.g. from the status bar) while we were still listening.
        if (recognitionJob?.isActive == true) {
          Timber.tag(TAG).i("Audio capture stopped by the system/user")
          recognitionJob?.cancel()
          MusicRecognitionService.reportError(getString(R.string.recognition_device_capture_stopped))
        }
      }
    }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      createNotificationChannel()
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Timber.tag(TAG).d("onStartCommand: action=%s, flags=%d, startId=%d", intent?.action, flags, startId)
    val busy = recognitionJob?.isActive == true
    val wantsDeviceAudio =
      intent?.action == ACTION_RECOGNIZE_DEVICE_AUDIO && DeviceAudioCapture.isSupported
    if (!busy) {
      deviceAudio = wantsDeviceAudio
      fromApp = intent?.getBooleanExtra(EXTRA_FROM_APP, false) == true
    }

    if (!startInForeground()) return START_NOT_STICKY
    if (busy) return START_NOT_STICKY

    val mediaProjection =
      if (deviceAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        obtainProjection(intent)
          ?: run {
            MusicRecognitionService.reportError(
              getString(R.string.recognition_device_capture_unavailable)
            )
            renderStatus(MusicRecognitionService.recognitionStatus.value)
            return START_NOT_STICKY
          }
      } else {
        null
      }

    startRecognitionIfNeeded(mediaProjection)
    return START_NOT_STICKY
  }

  /** Turns the consent returned by the system dialog into a live MediaProjection. */
  @RequiresApi(Build.VERSION_CODES.Q)
  private fun obtainProjection(intent: Intent?): MediaProjection? {
    if (intent == null) return null
    val resultCode = intent.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, Activity.RESULT_CANCELED)
    val data =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
      } else {
        @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)
      } ?: return null

    return try {
      getSystemService(MediaProjectionManager::class.java)
        .getMediaProjection(resultCode, data)
        ?.also { it.registerCallback(projectionCallback, Handler(Looper.getMainLooper())) }
        .also { projection = it }
    } catch (e: Exception) {
      Timber.tag(TAG).w(e, "Unable to obtain MediaProjection")
      null
    }
  }

  private fun releaseProjection() {
    projection?.let {
      runCatching { it.unregisterCallback(projectionCallback) }
      runCatching { it.stop() }
    }
    projection = null
  }

  override fun onDestroy() {
    Timber.tag(TAG).d("Service destroyed (keepNotification=%b)", keepNotificationOnStop)
    recognitionJob?.cancel()
    statusJob?.cancel()
    serviceScope.cancel()
    releaseProjection()
    if (!keepNotificationOnStop) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    }
    super.onDestroy()
  }

  private fun startInForeground(): Boolean {
    val notification =
      buildNotification(
        title = getString(R.string.recognize_music),
        contentText = listeningText(),
        isTerminal = false,
        contentIntent = null,
        largeIcon = null,
        actionIntent = null,
        actionTitle = null,
      )

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(NOTIFICATION_ID, notification, foregroundServiceType())
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
      return true
    } catch (foregroundTypeException: SecurityException) {
      Timber.w(foregroundTypeException, "Unable to start microphone foreground service")
      stopSelf()
      return false
    } catch (runtimeException: RuntimeException) {
      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
          runtimeException::class.java.name ==
            "android.app.ForegroundServiceStartNotAllowedException"
      ) {
        Timber.w(runtimeException, "Unable to start microphone foreground service")
        stopSelf()
        return false
      }
      throw runtimeException
    }
  }

  /**
   * Playback capture needs the `mediaProjection` type; the `microphone` type is kept as well so
   * the app may record while in the background (Android 11+ silences recording otherwise).
   */
  @RequiresApi(Build.VERSION_CODES.Q)
  private fun foregroundServiceType(): Int {
    val microphone =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
      } else {
        0
      }
    return if (deviceAudio) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or microphone
    } else {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    }
  }

  private fun listeningText(): String =
    getString(
      if (deviceAudio) R.string.recognition_notification_listening_device
      else R.string.recognition_notification_listening
    )

  private fun startRecognitionIfNeeded(mediaProjection: MediaProjection?) {
    if (recognitionJob?.isActive == true) return
    Timber.tag(TAG).d("Starting recognition flow")

    keepNotificationOnStop = false
    terminalStateHandled = false
    MusicRecognitionService.reset()
    Timber.tag(TAG).d("MusicRecognitionService reset")

    statusJob?.cancel()
    statusJob =
      serviceScope.launch {
        MusicRecognitionService.recognitionStatus.collect { status ->
          when (status) {
            is RecognitionStatus.Ready -> Unit
            else -> renderStatus(status)
          }
        }
      }

    recognitionJob =
      serviceScope.launch {
        try {
          val result =
            MusicRecognitionService.recognize(this@RecognitionForegroundService, mediaProjection)
          if (
            result is RecognitionStatus.Error &&
              MusicRecognitionService.recognitionStatus.value !is RecognitionStatus.Error
          ) {
            renderStatus(result)
          }
        } finally {
          // One consent per recognition: stop capturing as soon as we are done listening.
          releaseProjection()
        }
      }
  }

  private fun renderStatus(status: RecognitionStatus) {
    when (status) {
      is RecognitionStatus.Listening -> {
        Timber.tag(TAG).d("Status: Listening")
        updateNotification(
          title = getString(R.string.recognize_music),
          contentText = listeningText(),
          isTerminal = false,
          contentIntent = null,
          largeIcon = null,
          actionIntent = null,
          actionTitle = null,
        )
      }
      is RecognitionStatus.Processing -> {
        Timber.tag(TAG).d("Status: Processing")
        updateNotification(
          title = getString(R.string.recognize_music),
          contentText = getString(R.string.recognition_notification_processing),
          isTerminal = false,
          contentIntent = null,
          largeIcon = null,
          actionIntent = null,
          actionTitle = null,
        )
      }
      is RecognitionStatus.Success -> {
        Timber.tag(TAG).i("Status: Success — '%s' by %s", status.result.title, status.result.artist)
        handleSuccess(status.result)
      }
      is RecognitionStatus.NoMatch -> {
        if (terminalStateHandled) return
        terminalStateHandled = true
        Timber.tag(TAG).i("Status: No match")
        if (!resultShownInApp()) {
          updateNotification(
            title = getString(R.string.recognize_music),
            contentText = getString(R.string.recognition_notification_no_match),
            isTerminal = true,
            contentIntent = null,
            largeIcon = null,
            actionIntent = null,
            actionTitle = null,
          )
        }
        finishWithPersistentResult()
      }
      is RecognitionStatus.Error -> {
        if (terminalStateHandled) return
        terminalStateHandled = true
        Timber.tag(TAG).w("Status: Error — %s", status.message)
        if (!resultShownInApp()) {
          updateNotification(
            title = getString(R.string.recognition_notification_failed),
            contentText = status.message,
            isTerminal = true,
            contentIntent = null,
            largeIcon = null,
            actionIntent = null,
            actionTitle = null,
          )
        }
        finishWithPersistentResult()
      }
      is RecognitionStatus.Ready -> Unit
    }
  }

  private fun updateNotification(
    title: String,
    contentText: String,
    isTerminal: Boolean,
    contentIntent: PendingIntent?,
    largeIcon: Bitmap?,
    actionIntent: PendingIntent?,
    actionTitle: String?,
  ) {
    NotificationManagerCompat.from(this)
      .notify(
        NOTIFICATION_ID,
        buildNotification(
          title = title,
          contentText = contentText,
          isTerminal = isTerminal,
          contentIntent = contentIntent,
          largeIcon = largeIcon,
          actionIntent = actionIntent,
          actionTitle = actionTitle,
        ),
      )
  }

  private fun buildNotification(
    title: String,
    contentText: String,
    isTerminal: Boolean,
    contentIntent: PendingIntent?,
    largeIcon: Bitmap?,
    actionIntent: PendingIntent?,
    actionTitle: String?,
  ) =
    NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(if (deviceAudio) R.drawable.headphones else R.drawable.ic_widget_mic)
      .setContentTitle(title)
      .setContentText(contentText)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOnlyAlertOnce(true)
      .setOngoing(!isTerminal)
      .setAutoCancel(isTerminal)
      .setContentIntent(contentIntent)
      .setLargeIcon(largeIcon)
      .apply {
        if (actionIntent != null && actionTitle != null) {
          addAction(0, actionTitle, actionIntent)
        }
      }
      .build()

  private fun handleSuccess(result: RecognitionResult) {
    if (terminalStateHandled) return
    terminalStateHandled = true
    serviceScope.launch { saveRecognitionToHistory(this@RecognitionForegroundService, result) }
    if (resultShownInApp()) {
      finishWithPersistentResult()
      return
    }

    val pendingIntent = createResultPendingIntent(result)
    updateNotification(
      title = result.title,
      contentText = result.artist,
      isTerminal = true,
      contentIntent = pendingIntent,
      largeIcon = null,
      actionIntent = pendingIntent,
      actionTitle = getString(R.string.listen_on_echo_music),
    )

    serviceScope.launch {
      val coverUrl = result.coverArtHqUrl ?: result.coverArtUrl
      val coverBitmap =
        if (coverUrl == null) {
          null
        } else {
          withTimeoutOrNull(1_500L) { loadBitmap(coverUrl) }
        }

      if (coverBitmap != null) {
        updateNotification(
          title = result.title,
          contentText = result.artist,
          isTerminal = true,
          contentIntent = pendingIntent,
          largeIcon = coverBitmap,
          actionIntent = pendingIntent,
          actionTitle = getString(R.string.listen_on_echo_music),
        )
      }
      finishWithPersistentResult()
    }
  }

  private suspend fun loadBitmap(url: String): Bitmap? =
    withContext(Dispatchers.IO) {
      Timber.tag(TAG).d("Loading cover art bitmap from %s", url)
      runCatching {
          val connection =
            (URL(url).openConnection() as? HttpURLConnection) ?: return@runCatching null
          try {
            connection.connectTimeout = BITMAP_CONNECT_TIMEOUT_MS
            connection.readTimeout = BITMAP_READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.doInput = true
            connection.connect()
            connection.inputStream.use(BitmapFactory::decodeStream)
          } finally {
            connection.disconnect()
          }
        }
        .getOrNull()
    }

  private fun createResultPendingIntent(result: RecognitionResult): PendingIntent {
    val launchIntent =
      Intent(this, MainActivity::class.java).apply {
        action = MainActivity.ACTION_RECOGNITION
        putExtra(EXTRA_RECOGNITION_TRACK_ID, result.trackId)
        putExtra(EXTRA_RECOGNITION_TITLE, result.title)
        putExtra(EXTRA_RECOGNITION_ARTIST, result.artist)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }

    return PendingIntent.getActivity(
      this,
      RESULT_PENDING_INTENT_REQUEST_CODE,
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  /** The user started this from the recognition screen and is still looking at it. */
  private fun resultShownInApp(): Boolean = fromApp && MusicRecognitionService.isScreenVisible

  private fun finishWithPersistentResult() {
    if (resultShownInApp()) {
      // The result is already on screen: no need for a leftover notification.
      Timber.tag(TAG).d("Finishing without notification (result shown in app)")
      keepNotificationOnStop = false
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return
    }
    Timber.tag(TAG).d("Finishing with persistent notification")
    keepNotificationOnStop = true
    stopForeground(STOP_FOREGROUND_DETACH)
    stopSelf()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val channel =
      NotificationChannel(
          CHANNEL_ID,
          getString(R.string.recognition_notification_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        )
        .apply {
          description = getString(R.string.recognition_notification_channel_desc)
          setShowBadge(false)
        }

    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  companion object {
    /** Listen to the phone's own audio; needs [EXTRA_PROJECTION_RESULT_CODE] + [EXTRA_PROJECTION_DATA]. */
    const val ACTION_RECOGNIZE_DEVICE_AUDIO = "echo.music.iad1tya.recognition.DEVICE_AUDIO"
    const val EXTRA_PROJECTION_RESULT_CODE = "projection_result_code"
    const val EXTRA_PROJECTION_DATA = "projection_data"
    const val EXTRA_FROM_APP = "from_app"

    const val EXTRA_RECOGNITION_TRACK_ID = "recognition_track_id"
    const val EXTRA_RECOGNITION_TITLE = "recognition_title"
    const val EXTRA_RECOGNITION_ARTIST = "recognition_artist"

    private const val CHANNEL_ID = "recognition_channel"
    private const val NOTIFICATION_ID = 9100
    private const val RESULT_PENDING_INTENT_REQUEST_CODE = 9101
    private const val TAG = "RecognitionFgService"
    private const val BITMAP_CONNECT_TIMEOUT_MS = 1_200
    private const val BITMAP_READ_TIMEOUT_MS = 1_200
  }
}

package echo.music.iad1tya.recognition

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.music.shazamkit.models.RecognitionResult
import dagger.hilt.android.EntryPointAccessors
import echo.music.iad1tya.db.entities.RecognitionHistory
import echo.music.iad1tya.widget.RecognizerWidgetEntryPoint
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Entry points for recognizing what the phone itself is playing ("device audio").
 *
 * Android only allows capturing other apps' playback through a MediaProjection, which needs the
 * user's consent (the system "start recording or casting" dialog) every time, and a foreground
 * service of type `mediaProjection` while it is in use. The flow is therefore:
 * [consentIntent] → user accepts → [start] the [RecognitionForegroundService] with the result.
 */
object DeviceAudioCapture {

  val isSupported: Boolean
    get() = MusicRecognitionService.isDeviceAudioSupported

  /**
   * Intent for the system capture-consent dialog. On Android 14+ the whole-display option is
   * requested, so the dialog doesn't ask the user to pick a single app (audio capture is not tied
   * to one app anyway).
   */
  fun consentIntent(context: Context): Intent {
    val manager = context.getSystemService(MediaProjectionManager::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
    } else {
      manager.createScreenCaptureIntent()
    }
  }

  /**
   * Starts listening to the device audio with the consent returned by [consentIntent].
   *
   * @param fromApp true when started from the recognition screen: the result notification is then
   *   dropped if the user is still looking at the screen.
   */
  fun start(context: Context, resultCode: Int, data: Intent, fromApp: Boolean) {
    val intent =
      Intent(context, RecognitionForegroundService::class.java)
        .setAction(RecognitionForegroundService.ACTION_RECOGNIZE_DEVICE_AUDIO)
        .putExtra(RecognitionForegroundService.EXTRA_PROJECTION_RESULT_CODE, resultCode)
        .putExtra(RecognitionForegroundService.EXTRA_PROJECTION_DATA, data)
        .putExtra(RecognitionForegroundService.EXTRA_FROM_APP, fromApp)
    try {
      ContextCompat.startForegroundService(context, intent)
    } catch (e: Exception) {
      Timber.w(e, "Unable to start device-audio recognition")
      MusicRecognitionService.reportError(
        context.getString(echo.music.iad1tya.R.string.recognition_device_capture_unavailable)
      )
    }
  }

  /** Stops a recognition running in the foreground service (no-op when none is running). */
  fun cancel(context: Context) {
    context.stopService(Intent(context, RecognitionForegroundService::class.java))
  }
}

/** Writes a recognized song to the recognition history, once per result. */
internal suspend fun saveRecognitionToHistory(context: Context, result: RecognitionResult) {
  if (!MusicRecognitionService.claimHistorySave(result)) return
  // NonCancellable: the service may stop right after a match; the entry must still be written.
  withContext(Dispatchers.IO + NonCancellable) {
    try {
      EntryPointAccessors.fromApplication(
          context.applicationContext,
          RecognizerWidgetEntryPoint::class.java,
        )
        .databaseDao()
        .insert(result.toHistoryEntry())
    } catch (e: Exception) {
      Timber.w(e, "Unable to save recognition to history")
    }
  }
}

internal fun RecognitionResult.toHistoryEntry(): RecognitionHistory =
  RecognitionHistory(
    trackId = trackId,
    title = title,
    artist = artist,
    album = album,
    coverArtUrl = coverArtUrl,
    coverArtHqUrl = coverArtHqUrl,
    genre = genre,
    releaseDate = releaseDate,
    label = label,
    shazamUrl = shazamUrl,
    appleMusicUrl = appleMusicUrl,
    spotifyUrl = spotifyUrl,
    isrc = isrc,
    youtubeVideoId = youtubeVideoId,
    recognizedAt = LocalDateTime.now(),
  )

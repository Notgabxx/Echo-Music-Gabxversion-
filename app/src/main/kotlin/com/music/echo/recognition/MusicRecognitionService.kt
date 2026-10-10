package echo.music.iad1tya.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.music.shazamkit.Shazam
import com.music.shazamkit.models.RecognitionResult
import com.music.shazamkit.models.RecognitionStatus
import echo.music.iad1tya.R
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where song recognition listens. */
enum class RecognitionSource {
  /** The microphone: music playing around the user. */
  MICROPHONE,

  /**
   * Audio played by this phone itself (other apps, videos, games), captured digitally through
   * Android's playback-capture API. It does not depend on the speaker, so it also works with wired
   * or Bluetooth headphones. Requires Android 10+ and the user's capture consent.
   */
  DEVICE_AUDIO,
}

object MusicRecognitionService {

  private const val RECORDING_SAMPLE_RATE = 44100
  private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
  private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
  private const val BYTES_PER_SECOND = RECORDING_SAMPLE_RATE * 2

  /** Longest listening window. */
  private const val RECORDING_DURATION_MS = 10_000L

  /**
   * Moments at which the audio heard so far is already sent for matching: a clean signal (always
   * the case for device audio) is recognized in a few seconds instead of after the whole window.
   */
  private val MATCH_ATTEMPTS_MS = longArrayOf(4_000L, 7_000L, RECORDING_DURATION_MS)

  /** Minimum amount of non-silent audio for an early attempt; the last one takes what it has. */
  private const val MIN_EARLY_AUDIO_MS = 3_000L
  private const val MIN_FINAL_AUDIO_MS = 1_000L

  /** Peak amplitude (16-bit) under which audio is treated as silence. */
  private const val SILENCE_PEAK = 48

  /** A recognized song is shown again when the user comes back within this window. */
  private const val RESULT_FRESHNESS_MS = 5 * 60_000L

  private val _recognitionStatus = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
  val recognitionStatus: StateFlow<RecognitionStatus> = _recognitionStatus.asStateFlow()

  private val running = AtomicBoolean(false)
  @Volatile private var lastResultAt = 0L
  @Volatile private var lastSavedResult: RecognitionResult? = null

  /** Set by the recognition screen while it is resumed (visible to the user). */
  @Volatile var isScreenVisible: Boolean = false

  /** True while a recognition (from any entry point) is listening or matching. */
  val isRunning: Boolean
    get() = running.get()

  /** Device-audio capture needs the playback-capture API (Android 10+). */
  val isDeviceAudioSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

  fun hasRecordPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
  }

  /** True when the last recognized song is recent enough to be shown again. */
  fun hasFreshResult(): Boolean =
    _recognitionStatus.value is RecognitionStatus.Success &&
      SystemClock.elapsedRealtime() - lastResultAt < RESULT_FRESHNESS_MS

  @Volatile private var showLastResultRequested = false

  /** The recognition screen is being opened from a result notification. */
  fun requestShowLastResult() {
    showLastResultRequested = true
  }

  /** True (once) when the screen should open on the last result instead of starting over. */
  fun consumeShowLastResult(): Boolean {
    val requested = showLastResultRequested
    showLastResultRequested = false
    return requested && hasFreshResult()
  }

  /** Returns true once per result, so every recognized song is written to history only once. */
  @Synchronized
  fun claimHistorySave(result: RecognitionResult): Boolean {
    if (lastSavedResult === result) return false
    lastSavedResult = result
    return true
  }

  /** Publishes a terminal error raised outside the recognition flow (e.g. capture revoked). */
  fun reportError(message: String) {
    _recognitionStatus.value = RecognitionStatus.Error(message)
  }

  /**
   * Listens and identifies the song that is playing. With a [projection] the phone's own audio
   * output is captured (see [RecognitionSource.DEVICE_AUDIO]); otherwise the microphone is used.
   * The caller owns the projection and stops it afterwards.
   */
  suspend fun recognize(context: Context, projection: MediaProjection? = null): RecognitionStatus =
    withContext(Dispatchers.IO) {
      val appContext = context.applicationContext
      if (!hasRecordPermission(appContext)) {
        return@withContext publish(
          RecognitionStatus.Error(appContext.getString(R.string.recognition_permission_missing))
        )
      }

      val source =
        if (projection != null && isDeviceAudioSupported) RecognitionSource.DEVICE_AUDIO
        else RecognitionSource.MICROPHONE

      running.set(true)
      _recognitionStatus.value = RecognitionStatus.Listening
      try {
        publish(listenAndMatch(appContext, source, projection))
      } catch (e: CancellationException) {
        // Cancelled by the user (or the screen went away): go back to idle, unless a terminal
        // state (e.g. "capture stopped") was already published.
        val current = _recognitionStatus.value
        if (current is RecognitionStatus.Listening || current is RecognitionStatus.Processing) {
          _recognitionStatus.value = RecognitionStatus.Ready
        }
        throw e
      } catch (e: Exception) {
        publish(RecognitionStatus.Error(errorMessage(appContext, e)))
      } finally {
        running.set(false)
      }
    }

  private fun publish(status: RecognitionStatus): RecognitionStatus {
    if (status is RecognitionStatus.Success) lastResultAt = SystemClock.elapsedRealtime()
    _recognitionStatus.value = status
    return status
  }

  /**
   * Records up to [RECORDING_DURATION_MS] while trying to match at each of [MATCH_ATTEMPTS_MS], and
   * stops at the first match.
   */
  private suspend fun listenAndMatch(
    context: Context,
    source: RecognitionSource,
    projection: MediaProjection?,
  ): RecognitionStatus = coroutineScope {
    val minBuffer = AudioRecord.getMinBufferSize(RECORDING_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    // ~200 ms chunks: large enough to never overrun, small enough for responsive attempts.
    val bufferSize = maxOf(if (minBuffer > 0) minBuffer else 0, BYTES_PER_SECOND / 5)

    val record =
      runCatching {
          if (
            source == RecognitionSource.DEVICE_AUDIO &&
              projection != null &&
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
          ) {
            createPlaybackCaptureRecord(projection, bufferSize)
          } else {
            createMicrophoneRecord(bufferSize)
          }
        }
        .getOrNull()

    if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
      record?.release()
      return@coroutineScope RecognitionStatus.Error(context.getString(unavailableMessage(source)))
    }

    val pcm = ByteArrayOutputStream(BYTES_PER_SECOND * (RECORDING_DURATION_MS / 1000L + 1).toInt())
    val recordedBytes = AtomicLong(0L)
    val targetBytes = RECORDING_DURATION_MS * BYTES_PER_SECOND / 1000L
    var recorder: Job? = null

    try {
      record.startRecording()
      if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
        return@coroutineScope RecognitionStatus.Error(context.getString(unavailableMessage(source)))
      }

      val recorderJob =
        launch(Dispatchers.IO) {
          val buffer = ByteArray(bufferSize)
          // Wall-clock guard in case the audio stack delivers data slower than real time.
          val deadline = SystemClock.elapsedRealtime() + RECORDING_DURATION_MS + 3_000L
          while (
            isActive &&
              recordedBytes.get() < targetBytes &&
              SystemClock.elapsedRealtime() < deadline
          ) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
              synchronized(pcm) { pcm.write(buffer, 0, read) }
              recordedBytes.addAndGet(read.toLong())
            } else if (read < 0) {
              break
            }
          }
        }
      recorder = recorderJob

      var outcome: Result<RecognitionResult>? = null
      var heardAudio = false

      for ((index, attemptAtMs) in MATCH_ATTEMPTS_MS.withIndex()) {
        val neededBytes = attemptAtMs * BYTES_PER_SECOND / 1000L
        while (recordedBytes.get() < neededBytes && recorderJob.isActive) delay(100)

        val finalAttempt = index == MATCH_ATTEMPTS_MS.lastIndex || !recorderJob.isActive
        val audio = trimLeadingSilence(synchronized(pcm) { pcm.toByteArray() })
        val audioMs = audio.size * 1000L / BYTES_PER_SECOND
        if (audio.isNotEmpty()) heardAudio = true

        val minimumMs = if (finalAttempt) MIN_FINAL_AUDIO_MS else MIN_EARLY_AUDIO_MS
        if (audioMs < minimumMs) {
          if (finalAttempt) break else continue
        }

        if (finalAttempt) _recognitionStatus.value = RecognitionStatus.Processing
        val attempt = match(audio)
        outcome = attempt
        // Stop on a match, or on a real failure (network, service) that waiting won't fix.
        if (attempt.isSuccess || !attempt.isNoMatch() || finalAttempt) break
      }

      when {
        outcome == null && !heardAudio ->
          RecognitionStatus.Error(context.getString(silentMessage(source)))
        outcome == null || outcome.isNoMatch() ->
          RecognitionStatus.NoMatch(context.getString(R.string.recognition_no_match_hint))
        outcome.isSuccess -> RecognitionStatus.Success(outcome.getOrThrow())
        else ->
          RecognitionStatus.Error(
            errorMessage(context, outcome.exceptionOrNull() ?: Exception())
          )
      }
    } finally {
      recorder?.cancel()
      // stop() also wakes up a read() in progress, so the recorder can finish before release().
      runCatching { record.stop() }
      withContext(NonCancellable) { recorder?.join() }
      record.release()
    }
  }

  @SuppressLint("MissingPermission")
  private fun createMicrophoneRecord(bufferSize: Int): AudioRecord =
    AudioRecord(
      MediaRecorder.AudioSource.MIC,
      RECORDING_SAMPLE_RATE,
      CHANNEL_CONFIG,
      AUDIO_FORMAT,
      bufferSize,
    )

  /**
   * Captures what other apps are playing (media, games and unclassified audio) straight from the
   * mixer, so it works whether the sound goes to the speaker or to headphones. Flux's own playback
   * is excluded — the player already shows that song — and apps can opt out of capture (e.g.
   * protected streams), in which case their audio simply isn't included.
   */
  @SuppressLint("MissingPermission")
  @RequiresApi(Build.VERSION_CODES.Q)
  private fun createPlaybackCaptureRecord(projection: MediaProjection, bufferSize: Int): AudioRecord {
    val captureConfig =
      AudioPlaybackCaptureConfiguration.Builder(projection)
        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
        .addMatchingUsage(AudioAttributes.USAGE_GAME)
        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
        .excludeUid(Process.myUid())
        .build()

    return AudioRecord.Builder()
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AUDIO_FORMAT)
          .setSampleRate(RECORDING_SAMPLE_RATE)
          .setChannelMask(CHANNEL_CONFIG)
          .build()
      )
      .setBufferSizeInBytes(bufferSize)
      .setAudioPlaybackCaptureConfig(captureConfig)
      .build()
  }

  /** Fingerprints the PCM (44.1 kHz mono) and asks Shazam for a match. */
  private suspend fun match(pcm: ByteArray): Result<RecognitionResult> {
    val resampled =
      AudioResampler.resample(
          DecodedAudio(
            data = pcm,
            channelCount = 1,
            sampleRate = RECORDING_SAMPLE_RATE,
            pcmEncoding = AUDIO_FORMAT,
          ),
          VibraSignature.REQUIRED_SAMPLE_RATE,
        )
        .getOrElse {
          return Result.failure(Exception("Failed to resample audio: ${it.message}", it))
        }

    val validFormat =
      resampled.channelCount == 1 &&
        resampled.sampleRate == VibraSignature.REQUIRED_SAMPLE_RATE &&
        resampled.pcmEncoding == AudioFormat.ENCODING_PCM_16BIT &&
        ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN &&
        resampled.data.isNotEmpty() &&
        resampled.data.size % 2 == 0
    if (!validFormat) {
      return Result.failure(IllegalStateException("Invalid audio format for fingerprint generation"))
    }

    val signature =
      try {
        VibraSignature.fromI16(resampled.data)
      } catch (e: Exception) {
        return Result.failure(Exception("Failed to generate fingerprint: ${e.message}", e))
      }

    val sampleDurationMs =
      (resampled.data.size / 2) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE
    return Shazam.recognize(signature, sampleDurationMs)
  }

  /**
   * Drops the silence before the music starts (e.g. the user pressed play a few seconds after
   * starting), keeping a short lead-in. Returns an empty array when everything is silent.
   */
  private fun trimLeadingSilence(pcm: ByteArray): ByteArray {
    var index = 0
    while (index + 1 < pcm.size) {
      // Little-endian signed 16-bit sample.
      val sample = (pcm[index].toInt() and 0xFF) or (pcm[index + 1].toInt() shl 8)
      if (sample > SILENCE_PEAK || sample < -SILENCE_PEAK) break
      index += 2
    }
    if (index + 1 >= pcm.size) return ByteArray(0)
    val leadInBytes = BYTES_PER_SECOND / 10
    val start = (index - leadInBytes).coerceAtLeast(0).let { it - it % 2 }
    return if (start == 0) pcm else pcm.copyOfRange(start, pcm.size)
  }

  private fun Result<RecognitionResult>.isNoMatch(): Boolean =
    isFailure && exceptionOrNull()?.message?.contains("No match", ignoreCase = true) == true

  private fun unavailableMessage(source: RecognitionSource): Int =
    if (source == RecognitionSource.DEVICE_AUDIO) R.string.recognition_device_capture_unavailable
    else R.string.recognition_mic_unavailable

  private fun silentMessage(source: RecognitionSource): Int =
    if (source == RecognitionSource.DEVICE_AUDIO) R.string.recognition_device_silent
    else R.string.recognition_mic_silent

  private fun errorMessage(context: Context, error: Throwable): String =
    if (generateSequence(error) { it.cause }.any { it is IOException }) {
      context.getString(R.string.recognition_network_error)
    } else {
      error.message?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.recognition_failed_generic)
    }

  fun reset() {
    _recognitionStatus.value = RecognitionStatus.Ready
  }
}

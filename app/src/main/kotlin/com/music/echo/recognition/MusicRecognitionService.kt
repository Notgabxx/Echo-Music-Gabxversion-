package echo.music.iad1tya.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.music.shazamkit.Shazam
import com.music.shazamkit.models.RecognitionResult
import com.music.shazamkit.models.RecognitionStatus
import echo.music.iad1tya.R
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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

/**
 * Song recognition through the microphone, done the way the proven open-source Shazam clients do
 * it (SongRec / ShazamIO): 16 kHz mono audio, fingerprinted over a 12-second window (zero-padded
 * in front while less has been heard) and matched every 4 seconds until Shazam finds the song.
 */
object MusicRecognitionService {

  private const val SAMPLE_RATE = ShazamSignatureGenerator.SAMPLE_RATE
  private const val FALLBACK_SAMPLE_RATE = 44100
  private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
  private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

  /** How long we listen at most: one full Shazam window. */
  private const val LISTEN_SECONDS = ShazamSignatureGenerator.WINDOW_SECONDS

  /** Like SongRec: a new attempt every 4 seconds of audio. */
  private val MATCH_ATTEMPTS_SECONDS = intArrayOf(4, 8, LISTEN_SECONDS)

  /** Peak amplitude (16-bit) under which audio is treated as silence. */
  private const val SILENCE_PEAK = 48

  /** A recognized song is shown again when the user comes back within this window. */
  private const val RESULT_FRESHNESS_MS = 5 * 60_000L

  private val _recognitionStatus = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
  val recognitionStatus: StateFlow<RecognitionStatus> = _recognitionStatus.asStateFlow()

  private val running = AtomicBoolean(false)
  @Volatile private var lastResultAt = 0L
  @Volatile private var lastSavedResult: RecognitionResult? = null
  @Volatile private var showLastResultRequested = false

  /** True while a recognition (from any entry point) is listening or matching. */
  val isRunning: Boolean
    get() = running.get()

  fun hasRecordPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
  }

  /** True when the last recognized song is recent enough to be shown again. */
  fun hasFreshResult(): Boolean =
    _recognitionStatus.value is RecognitionStatus.Success &&
      SystemClock.elapsedRealtime() - lastResultAt < RESULT_FRESHNESS_MS

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

  /** Listens through the microphone and identifies the song that is playing. */
  suspend fun recognize(context: Context): RecognitionStatus =
    withContext(Dispatchers.IO) {
      val appContext = context.applicationContext
      if (!hasRecordPermission(appContext)) {
        return@withContext publish(
          RecognitionStatus.Error(appContext.getString(R.string.recognition_permission_missing))
        )
      }

      running.set(true)
      _recognitionStatus.value = RecognitionStatus.Listening
      try {
        publish(listenAndMatch(appContext))
      } catch (e: CancellationException) {
        // Cancelled by the user (or the screen went away): back to idle.
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

  private class OpenRecorder(val record: AudioRecord, val sampleRate: Int)

  /**
   * Prefers the unprocessed microphone at 16 kHz where the device supports it (no noise
   * suppression or automatic gain, which can eat music), then the regular one, then 44.1 kHz with
   * resampling for devices that refuse 16 kHz.
   */
  @SuppressLint("MissingPermission")
  private fun openRecorder(context: Context): OpenRecorder? {
    val unprocessed =
      runCatching {
          context
            .getSystemService(AudioManager::class.java)
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        }
        .getOrDefault(false)
    val candidates = buildList {
      if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED to SAMPLE_RATE)
      add(MediaRecorder.AudioSource.MIC to SAMPLE_RATE)
      add(MediaRecorder.AudioSource.MIC to FALLBACK_SAMPLE_RATE)
    }
    for ((source, rate) in candidates) {
      val minBuffer = AudioRecord.getMinBufferSize(rate, CHANNEL_CONFIG, AUDIO_FORMAT)
      if (minBuffer <= 0) continue
      // ~400 ms of headroom so the reader never overruns.
      val bufferSize = maxOf(minBuffer, rate * 2 * 2 / 5)
      val record =
        runCatching { AudioRecord(source, rate, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize) }
          .getOrNull() ?: continue
      if (record.state == AudioRecord.STATE_INITIALIZED) return OpenRecorder(record, rate)
      record.release()
    }
    return null
  }

  private suspend fun listenAndMatch(context: Context): RecognitionStatus = coroutineScope {
    val opened =
      openRecorder(context)
        ?: return@coroutineScope RecognitionStatus.Error(
          context.getString(R.string.recognition_mic_unavailable)
        )
    val record = opened.record
    val rate = opened.sampleRate

    val audio = ShortArray(rate * LISTEN_SECONDS)
    val written = AtomicInteger(0)
    var recorder: Job? = null

    try {
      record.startRecording()
      if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
        return@coroutineScope RecognitionStatus.Error(
          context.getString(R.string.recognition_mic_unavailable)
        )
      }

      val recorderJob =
        launch(Dispatchers.IO) {
          val chunk = rate / 10 // 100 ms per read
          // Wall-clock guard in case the audio stack delivers data slower than real time.
          val deadline = SystemClock.elapsedRealtime() + LISTEN_SECONDS * 1000L + 3_000L
          while (isActive && written.get() < audio.size && SystemClock.elapsedRealtime() < deadline) {
            val position = written.get()
            val read = record.read(audio, position, minOf(chunk, audio.size - position))
            if (read > 0) {
              written.addAndGet(read)
            } else if (read < 0) {
              break
            }
          }
        }
      recorder = recorderJob

      var outcome: Result<RecognitionResult>? = null
      var heardAudio = false

      for ((index, attemptSeconds) in MATCH_ATTEMPTS_SECONDS.withIndex()) {
        val needed = attemptSeconds * rate
        while (written.get() < needed && recorderJob.isActive) delay(100)

        val finalAttempt = index == MATCH_ATTEMPTS_SECONDS.lastIndex || !recorderJob.isActive
        val available = written.get()
        if (available < rate) {
          // Less than a second of audio: nothing worth sending yet.
          if (finalAttempt) break else continue
        }

        val pcm = to16kHz(audio.copyOf(available), rate)
        if (isSilent(pcm)) {
          if (finalAttempt) break else continue
        }
        heardAudio = true

        if (finalAttempt) _recognitionStatus.value = RecognitionStatus.Processing
        val attempt = match(pcm)
        outcome = attempt
        // Stop on a match, or on a real failure (network, service) that waiting won't fix.
        if (attempt.isSuccess || !attempt.isNoMatch() || finalAttempt) break
      }

      when {
        !heardAudio -> RecognitionStatus.Error(context.getString(R.string.recognition_mic_silent))
        outcome == null || outcome.isNoMatch() ->
          RecognitionStatus.NoMatch(context.getString(R.string.recognition_no_match_hint))
        outcome.isSuccess -> RecognitionStatus.Success(outcome.getOrThrow())
        else ->
          RecognitionStatus.Error(errorMessage(context, outcome.exceptionOrNull() ?: Exception()))
      }
    } finally {
      recorder?.cancel()
      // stop() also wakes up a read() in progress, so the recorder can finish before release().
      runCatching { record.stop() }
      withContext(NonCancellable) { recorder?.join() }
      record.release()
    }
  }

  /** Fingerprints the 16 kHz PCM over Shazam's 12-second window and asks Shazam for a match. */
  private suspend fun match(pcm16k: ShortArray): Result<RecognitionResult> {
    val signature =
      withContext(Dispatchers.Default) { ShazamSignatureGenerator.windowedSignature(pcm16k) }
    if (signature.peakCount == 0) return Result.failure(Exception("No match found"))
    val uri = ShazamSignatureGenerator.encodeToUri(signature)
    return Shazam.recognize(uri, signature.durationMs)
  }

  /** Recordings are 16 kHz already, except on devices that only offer 44.1 kHz. */
  private suspend fun to16kHz(pcm: ShortArray, rate: Int): ShortArray {
    if (rate == SAMPLE_RATE) return pcm
    val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    bytes.asShortBuffer().put(pcm)
    val resampled =
      AudioResampler.resample(
          DecodedAudio(
            data = bytes.array(),
            channelCount = 1,
            sampleRate = rate,
            pcmEncoding = AUDIO_FORMAT,
          ),
          SAMPLE_RATE,
        )
        .getOrThrow()
    val out = ShortArray(resampled.data.size / 2)
    ByteBuffer.wrap(resampled.data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
    return out
  }

  private fun isSilent(pcm: ShortArray): Boolean {
    for (sample in pcm) {
      if (sample > SILENCE_PEAK || sample < -SILENCE_PEAK) return false
    }
    return true
  }

  private fun Result<RecognitionResult>.isNoMatch(): Boolean =
    isFailure && exceptionOrNull()?.message?.contains("No match", ignoreCase = true) == true

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

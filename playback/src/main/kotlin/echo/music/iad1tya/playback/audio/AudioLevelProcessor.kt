package echo.music.iad1tya.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Pass-through processor that measures three frequency bands for audio-reactive visuals.
 *
 * The signal is never modified: every buffer is copied to the output untouched. When a visualiser
 * has requested analysis ([AudioLevels.acquire]) the processor runs two one-pole low-pass filters
 * over a mono mix (≈12 multiply-adds per sample — negligible next to decoding) and publishes the
 * RMS of the bass / mid / treble bands to [AudioLevels] on a perceptual (dB) scale.
 *
 * Unsupported encodings simply make the processor inactive, so it can never break playback.
 */
@UnstableApi
@Suppress("DEPRECATION")
class AudioLevelProcessor : AudioProcessor {

  private var sampleRate = 0
  private var channelCount = 0
  private var encoding = C.ENCODING_INVALID
  private var active = false

  private var outputBuffer: ByteBuffer = EMPTY_BUFFER
  private var inputEnded = false

  private var lowCoefficient = 0f
  private var highCoefficient = 0f
  private var lowState = 0f
  private var highCutState = 0f

  override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    sampleRate = inputAudioFormat.sampleRate
    channelCount = inputAudioFormat.channelCount
    encoding = inputAudioFormat.encoding
    active =
      sampleRate > 0 &&
        channelCount > 0 &&
        (encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT)
    if (active) {
      lowCoefficient = onePoleCoefficient(LOW_CUTOFF_HZ, sampleRate)
      highCoefficient = onePoleCoefficient(HIGH_CUTOFF_HZ, sampleRate)
    }
    return inputAudioFormat
  }

  override fun isActive(): Boolean = active

  override fun queueInput(inputBuffer: ByteBuffer) {
    if (!inputBuffer.hasRemaining()) {
      outputBuffer = EMPTY_BUFFER
      return
    }

    if (AudioLevels.isAnalysisRequested) {
      try {
        analyze(inputBuffer)
      } catch (_: Exception) {
        // Analysis is purely cosmetic; never let it interfere with playback.
      }
    }

    val out = replaceOutputBuffer(inputBuffer.remaining())
    out.put(inputBuffer)
    out.flip()
  }

  private fun analyze(buffer: ByteBuffer) {
    val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
    val frameBytes = bytesPerSample * channelCount
    val start = buffer.position()
    val frames = (buffer.limit() - start) / frameBytes
    if (frames <= 0) return

    val originalOrder = buffer.order()
    buffer.order(ByteOrder.LITTLE_ENDIAN)

    var low = lowState
    var highCut = highCutState
    var sumLow = 0.0
    var sumMid = 0.0
    var sumHigh = 0.0
    var sumAll = 0.0
    val isFloat = bytesPerSample == 4

    for (frame in 0 until frames) {
      val base = start + frame * frameBytes
      var mono = 0f
      for (channel in 0 until channelCount) {
        val index = base + channel * bytesPerSample
        mono += if (isFloat) buffer.getFloat(index) else buffer.getShort(index) / 32768f
      }
      mono /= channelCount

      low += lowCoefficient * (mono - low)
      highCut += highCoefficient * (mono - highCut)
      val bandMid = highCut - low
      val bandHigh = mono - highCut

      sumLow += (low * low).toDouble()
      sumMid += (bandMid * bandMid).toDouble()
      sumHigh += (bandHigh * bandHigh).toDouble()
      sumAll += (mono * mono).toDouble()
    }

    buffer.order(originalOrder)
    lowState = low
    highCutState = highCut

    val n = frames.toDouble()
    AudioLevels.bass = toPerceptual(sqrt(sumLow / n), BASS_GAIN)
    AudioLevels.mid = toPerceptual(sqrt(sumMid / n), MID_GAIN)
    AudioLevels.treble = toPerceptual(sqrt(sumHigh / n), TREBLE_GAIN)
    AudioLevels.level = toPerceptual(sqrt(sumAll / n), 1.0)
    AudioLevels.lastUpdateNanos = System.nanoTime()
  }

  override fun queueEndOfStream() {
    inputEnded = true
  }

  override fun getOutput(): ByteBuffer {
    val output = outputBuffer
    outputBuffer = EMPTY_BUFFER
    return output
  }

  override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

  @Deprecated("Deprecated in AudioProcessor")
  override fun flush() {
    outputBuffer = EMPTY_BUFFER
    inputEnded = false
    lowState = 0f
    highCutState = 0f
  }

  @Deprecated("Deprecated in AudioProcessor")
  override fun reset() {
    flush()
    sampleRate = 0
    channelCount = 0
    encoding = C.ENCODING_INVALID
    active = false
  }

  private fun replaceOutputBuffer(size: Int): ByteBuffer {
    if (outputBuffer.capacity() < size) {
      outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
    } else {
      outputBuffer.clear()
    }
    return outputBuffer
  }

  companion object {
    private val EMPTY_BUFFER: ByteBuffer =
      ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    private const val LOW_CUTOFF_HZ = 150.0
    private const val HIGH_CUTOFF_HZ = 2500.0

    private const val BASS_GAIN = 1.0
    private const val MID_GAIN = 1.8
    private const val TREBLE_GAIN = 4.0

    /** dB window mapped to 0..1. */
    private const val FLOOR_DB = -48.0
    private const val RANGE_DB = 42.0

    private fun onePoleCoefficient(cutoffHz: Double, sampleRate: Int): Float =
      (1.0 - exp(-2.0 * PI * cutoffHz / sampleRate)).toFloat()

    private fun toPerceptual(rms: Double, gain: Double): Float {
      val db = 20.0 * log10(rms * gain + 1e-7)
      return ((db - FLOOR_DB) / RANGE_DB).coerceIn(0.0, 1.0).toFloat()
    }
  }
}

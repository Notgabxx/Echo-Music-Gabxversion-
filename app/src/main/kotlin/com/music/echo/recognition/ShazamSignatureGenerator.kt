package echo.music.iad1tya.recognition

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Shazam audio fingerprint ("signature") generator.
 *
 * A line-by-line port of SongRec's reference implementation (GPL-3.0,
 * https://github.com/marin-m/SongRec, `src/core/fingerprinting/algorithm.rs` and
 * `signature_format.rs`), which is also what ShazamIO uses through `shazamio-core`. Input is
 * 16 kHz mono 16-bit PCM.
 *
 * Shazam clients always send a [WINDOW_SECONDS] window: Shazam answers worse (or not at all) for
 * other lengths, so shorter recordings are zero-padded in front, like SongRec does with the
 * microphone.
 */
internal object ShazamSignatureGenerator {

  const val SAMPLE_RATE = 16_000
  const val WINDOW_SECONDS = 12
  const val WINDOW_SAMPLES = SAMPLE_RATE * WINDOW_SECONDS

  private const val DATA_URI_PREFIX = "data:audio/vnd.shazam.sig;base64,"

  class FrequencyPeak(
    val fftPassNumber: Int,
    val peakMagnitude: Int,
    val correctedPeakFrequencyBin: Int,
  )

  class Signature(
    val sampleRateHz: Int,
    val numberSamples: Int,
    /** Peaks of the 250–520 Hz, 520–1450 Hz, 1450–3500 Hz and 3500–5500 Hz bands. */
    val bands: Array<MutableList<FrequencyPeak>>,
  ) {
    val durationMs: Long
      get() = numberSamples * 1000L / sampleRateHz

    val peakCount: Int
      get() = bands.sumOf { it.size }
  }

  /** The last [WINDOW_SECONDS] of [pcm], zero-padded in front when the recording is shorter. */
  fun toWindow(pcm: ShortArray): ShortArray {
    if (pcm.size == WINDOW_SAMPLES) return pcm
    val window = ShortArray(WINDOW_SAMPLES)
    if (pcm.size > WINDOW_SAMPLES) {
      pcm.copyInto(window, 0, pcm.size - WINDOW_SAMPLES, pcm.size)
    } else {
      pcm.copyInto(window, WINDOW_SAMPLES - pcm.size)
    }
    return window
  }

  /** SongRec's `make_signature_from_buffer`: fingerprints every full 128-sample chunk. */
  fun generate(samples: ShortArray): Signature = Generator(samples.size).run(samples)

  /** Signature of the standard window built from [pcm] (see [toWindow]). */
  fun windowedSignature(pcm: ShortArray): Signature = generate(toWindow(pcm))

  fun encodeToUri(signature: Signature): String =
    DATA_URI_PREFIX + Base64.getEncoder().encodeToString(encodeToBinary(signature))

  /** Little-endian 16 kHz PCM bytes → signature URI of the standard window. */
  fun fromI16(samples: ByteArray): String {
    require(samples.size >= 2 && samples.size % 2 == 0) {
      "samples must be a non-empty byte array with even length (16-bit PCM)"
    }
    val pcm = ShortArray(samples.size / 2)
    ByteBuffer.wrap(samples).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
    return encodeToUri(windowedSignature(pcm))
  }

  /** SongRec's `DecodedSignature::encode_to_binary`. */
  fun encodeToBinary(signature: Signature): ByteArray {
    val out = ByteArrayOutputStream()

    out.writeU32(0xcafe2580.toInt()) // magic1
    out.writeU32(0) // crc32, written below
    out.writeU32(0) // size_minus_header, written below
    out.writeU32(0x94119c00.toInt()) // magic2
    out.writeU32(0) // void1
    out.writeU32(0)
    out.writeU32(0)
    out.writeU32(sampleRateId(signature.sampleRateHz) shl 27) // shifted_sample_rate_id
    out.writeU32(0) // void2
    out.writeU32(0)
    out.writeU32(signature.numberSamples + (signature.sampleRateHz * 0.24f).toInt())
    out.writeU32((15 shl 19) + 0x40000) // fixed_value

    out.writeU32(0x40000000)
    out.writeU32(0) // size_minus_header again, written below

    for ((bandIndex, peaks) in signature.bands.withIndex()) {
      if (peaks.isEmpty()) continue
      val peaksOut = ByteArrayOutputStream()
      var fftPassNumber = 0
      for (peak in peaks) {
        check(peak.fftPassNumber >= fftPassNumber)
        if (peak.fftPassNumber - fftPassNumber >= 255) {
          peaksOut.write(0xff)
          peaksOut.writeU32(peak.fftPassNumber)
          fftPassNumber = peak.fftPassNumber
        }
        peaksOut.write(peak.fftPassNumber - fftPassNumber)
        peaksOut.writeU16(peak.peakMagnitude)
        peaksOut.writeU16(peak.correctedPeakFrequencyBin)
        fftPassNumber = peak.fftPassNumber
      }
      val peaksBuffer = peaksOut.toByteArray()
      out.writeU32(0x60030040 + bandIndex)
      out.writeU32(peaksBuffer.size)
      out.write(peaksBuffer)
      repeat((4 - peaksBuffer.size % 4) % 4) { out.write(0) }
    }

    val bytes = out.toByteArray()
    val sizeMinusHeader = bytes.size - 48
    bytes.putU32(8, sizeMinusHeader)
    bytes.putU32(48 + 4, sizeMinusHeader)
    val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }.value.toInt()
    bytes.putU32(4, crc)
    return bytes
  }

  private fun sampleRateId(sampleRateHz: Int): Int =
    when (sampleRateHz) {
      8000 -> 1
      11025 -> 2
      16000 -> 3
      32000 -> 4
      44100 -> 5
      48000 -> 6
      else -> throw IllegalArgumentException("Unsupported sample rate $sampleRateHz")
    }

  private fun ByteArrayOutputStream.writeU32(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
  }

  private fun ByteArrayOutputStream.writeU16(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
  }

  private fun ByteArray.putU32(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    this[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    this[offset + 3] = ((value ushr 24) and 0xFF).toByte()
  }

  /** State of SongRec's `SignatureGenerator`. */
  private class Generator(numberSamples: Int) {
    private val ring = ShortArray(FFT_SIZE)
    private var ringIndex = 0
    private val reordered = FloatArray(FFT_SIZE)
    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)

    private val fftOutputs = Array(RING) { FloatArray(BINS) }
    private var fftIndex = 0
    private val spreadOutputs = Array(RING) { FloatArray(BINS) }
    private var spreadIndex = 0
    private val spreadCopy = FloatArray(BINS)
    private var numSpreadFftsDone = 0

    private val signature =
      Signature(
        sampleRateHz = SAMPLE_RATE,
        numberSamples = numberSamples,
        bands = Array(4) { mutableListOf() },
      )

    fun run(samples: ShortArray): Signature {
      var offset = 0
      while (offset + CHUNK <= samples.size) {
        doFft(samples, offset)
        doPeakSpreading()
        numSpreadFftsDone++
        if (numSpreadFftsDone >= 46) doPeakRecognition()
        offset += CHUNK
      }
      return signature
    }

    private fun doFft(samples: ShortArray, offset: Int) {
      // Copy the 128 new samples to the ring buffer.
      samples.copyInto(ring, ringIndex, offset, offset + CHUNK)
      ringIndex = (ringIndex + CHUNK) and (FFT_SIZE - 1)

      // Reorder (latest data at the end) and apply the Hann window.
      var silent = true
      for (index in 0 until FFT_SIZE) {
        val sample = ring[(index + ringIndex) and (FFT_SIZE - 1)]
        if (sample.toInt() != 0) silent = false
        reordered[index] = sample.toFloat() * SHAZAM_HANNING_WINDOW[index]
      }

      val output = fftOutputs[fftIndex]
      if (silent) {
        // An FFT of zeros is zero everywhere: skip the work (padding is mostly silence).
        output.fill(MIN_POWER)
      } else {
        Fft.forward(reordered, re, im)
        for (bin in 0 until BINS) {
          val power = ((re[bin] * re[bin] + im[bin] * im[bin]) / (1 shl 17)).toFloat()
          output[bin] = if (power > MIN_POWER) power else MIN_POWER
        }
      }
      fftIndex = (fftIndex + 1) and (RING - 1)
    }

    private fun doPeakSpreading() {
      val realFftResults = fftOutputs[(fftIndex - 1) and (RING - 1)]
      val spread = spreadOutputs[spreadIndex]

      // Frequency-domain spreading of peak values.
      realFftResults.copyInto(spread)
      for (position in 0..1022) {
        spread[position] = maxOf(spread[position], spread[position + 1], spread[position + 2])
      }

      // Time-domain spreading of peak values.
      spread.copyInto(spreadCopy)
      for (formerFftNumber in FORMER_FFTS) {
        val former = spreadOutputs[(spreadIndex - formerFftNumber) and (RING - 1)]
        for (position in 0 until BINS) {
          if (spreadCopy[position] > former[position]) former[position] = spreadCopy[position]
        }
      }

      spreadIndex = (spreadIndex + 1) and (RING - 1)
    }

    private fun doPeakRecognition() {
      val fftMinus46 = fftOutputs[(fftIndex - 46) and (RING - 1)]
      val fftMinus49 = spreadOutputs[(spreadIndex - 49) and (RING - 1)]

      for (binPosition in 10..1014) {
        val value = fftMinus46[binPosition]
        // Large enough to be a peak.
        if (value < 1f / 64f || value < fftMinus49[binPosition - 1]) continue

        // Frequency-domain local maximum.
        var maxNeighborInFftMinus49 = 0f
        for (neighborOffset in NEIGHBOR_OFFSETS) {
          maxNeighborInFftMinus49 =
            maxOf(maxNeighborInFftMinus49, fftMinus49[binPosition + neighborOffset])
        }
        if (value <= maxNeighborInFftMinus49) continue

        // Time-domain local maximum.
        var maxNeighborInOtherAdjacentFfts = maxNeighborInFftMinus49
        for (otherOffset in OTHER_FFT_OFFSETS) {
          val otherFft = spreadOutputs[(spreadIndex + otherOffset) and (RING - 1)]
          maxNeighborInOtherAdjacentFfts =
            maxOf(maxNeighborInOtherAdjacentFfts, otherFft[binPosition - 1])
        }
        if (value <= maxNeighborInOtherAdjacentFfts) continue

        val fftPassNumber = numSpreadFftsDone - 46
        val peakMagnitude = magnitude(value)
        val peakMagnitudeBefore = magnitude(fftMinus46[binPosition - 1])
        val peakMagnitudeAfter = magnitude(fftMinus46[binPosition + 1])

        val peakVariation1 = peakMagnitude * 2f - peakMagnitudeBefore - peakMagnitudeAfter
        val peakVariation2 = (peakMagnitudeAfter - peakMagnitudeBefore) * 32f / peakVariation1

        val correctedPeakFrequencyBin = (binPosition * 64 + peakVariation2.toInt()) and 0xFFFF

        // FFT bin → frequency (16 kHz sample rate, 1024 useful bins, ×64 above).
        val frequencyHz = correctedPeakFrequencyBin.toFloat() * (16000f / 2f / 1024f / 64f)
        val band =
          when (frequencyHz.toInt()) {
            in 250..519 -> 0
            in 520..1449 -> 1
            in 1450..3499 -> 2
            in 3500..5500 -> 3
            else -> continue
          }

        signature.bands[band].add(
          FrequencyPeak(
            fftPassNumber = fftPassNumber,
            peakMagnitude = peakMagnitude.toInt().coerceIn(0, 0xFFFF),
            correctedPeakFrequencyBin = correctedPeakFrequencyBin,
          )
        )
      }
    }

    private fun magnitude(power: Float): Float = maxOf(ln(power), 1f / 64f) * 1477.3f + 6144f
  }

  /** Iterative radix-2 FFT of a real 2048-sample frame (unnormalised, like `realfft`). */
  private object Fft {
    private val bitReversed = IntArray(FFT_SIZE)
    private val cosTable = DoubleArray(FFT_SIZE / 2)
    private val sinTable = DoubleArray(FFT_SIZE / 2)

    init {
      val bits = Integer.numberOfTrailingZeros(FFT_SIZE)
      for (i in 0 until FFT_SIZE) {
        bitReversed[i] = Integer.reverse(i) ushr (32 - bits)
      }
      for (k in 0 until FFT_SIZE / 2) {
        val angle = -2.0 * PI * k / FFT_SIZE
        cosTable[k] = cos(angle)
        sinTable[k] = sin(angle)
      }
    }

    fun forward(input: FloatArray, re: DoubleArray, im: DoubleArray) {
      for (i in 0 until FFT_SIZE) {
        re[bitReversed[i]] = input[i].toDouble()
        im[bitReversed[i]] = 0.0
      }
      var size = 2
      while (size <= FFT_SIZE) {
        val half = size / 2
        val step = FFT_SIZE / size
        var start = 0
        while (start < FFT_SIZE) {
          var k = 0
          for (j in start until start + half) {
            val wr = cosTable[k]
            val wi = sinTable[k]
            val l = j + half
            val tr = re[l] * wr - im[l] * wi
            val ti = re[l] * wi + im[l] * wr
            re[l] = re[j] - tr
            im[l] = im[j] - ti
            re[j] += tr
            im[j] += ti
            k += step
          }
          start += size
        }
        size *= 2
      }
    }
  }

  private const val FFT_SIZE = 2048
  private const val BINS = FFT_SIZE / 2 + 1
  private const val RING = 256
  private const val CHUNK = 128
  private const val MIN_POWER = 0.0000000001f
  private val FORMER_FFTS = intArrayOf(1, 3, 6)
  private val NEIGHBOR_OFFSETS = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)
  private val OTHER_FFT_OFFSETS =
    intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)
}

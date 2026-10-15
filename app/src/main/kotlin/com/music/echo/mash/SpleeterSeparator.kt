package echo.music.iad1tya.mash

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer

/** Stems of one passage; [vocalRatio] is the share of the energy that belongs to the voice. */
class SeparatedSong(val vocals: StereoPcm?, val accompaniment: StereoPcm?, val vocalRatio: Double)

/**
 * Voice/accompaniment separation with Deezer's Spleeter 2-stems (ONNX export of sherpa-onnx),
 * done exactly like sherpa-onnx: 4096-point STFT (periodic Hann, hop 1024, no centering) of each
 * channel, magnitudes of the first 1024 bins in chunks of 512 frames → both networks → ratio
 * masks (v² / (v² + a²)) applied to the complex STFT → inverse STFT with overlap-add.
 */
class SpleeterSeparator(vocalsModel: File, accompanimentModel: File, threads: Int) :
  AutoCloseable {
  private val env = OrtEnvironment.getEnvironment()
  private val options =
    OrtSession.SessionOptions().apply {
      setIntraOpNumThreads(threads.coerceIn(1, 8))
      setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
  private val vocalsNet = env.createSession(vocalsModel.absolutePath, options)
  private val accompanimentNet = env.createSession(accompanimentModel.absolutePath, options)

  /** Number of network chunks for [samples] samples (progress planning). */
  fun chunksFor(samples: Int): Int {
    val frames = framesFor(samples)
    return (frames + CHUNK - 1) / CHUNK
  }

  fun separate(
    pcm: StereoPcm,
    keepVocals: Boolean,
    keepAccompaniment: Boolean,
    onChunk: (done: Int, total: Int) -> Unit = { _, _ -> },
  ): SeparatedSong {
    val n = pcm.size
    val frames = framesFor(n)
    val chunks = (frames + CHUNK - 1) / CHUNK
    val channels = arrayOf(pcm.left, pcm.right)
    val window = hannWindow(N_FFT)
    val fft = RealFft(N_FFT)

    val vocalsOut = if (keepVocals) arrayOf(FloatArray(n), FloatArray(n)) else null
    val accompOut = if (keepAccompaniment) arrayOf(FloatArray(n), FloatArray(n)) else null

    val specRe = Array(2) { FloatArray(CHUNK * BINS) }
    val specIm = Array(2) { FloatArray(CHUNK * BINS) }
    val input = FloatArray(2 * CHUNK * BINS)
    val frame = FloatArray(N_FFT)
    val re = FloatArray(N_FFT / 2 + 1)
    val im = FloatArray(N_FFT / 2 + 1)
    val shape = longArrayOf(2, 1, CHUNK.toLong(), BINS.toLong())

    var vocalEnergy = 0.0
    var totalEnergy = 0.0

    for (c in 0 until chunks) {
      val f0 = c * CHUNK
      val count = minOf(CHUNK, frames - f0)
      input.fill(0f)
      for (ch in 0..1) {
        val x = channels[ch]
        for (f in 0 until count) {
          val start = (f0 + f) * HOP
          for (i in 0 until N_FFT) frame[i] = x[start + i] * window[i]
          fft.forward(frame, re, im)
          val base = f * BINS
          val inBase = ch * CHUNK * BINS + base
          for (k in 0 until BINS) {
            specRe[ch][base + k] = re[k]
            specIm[ch][base + k] = im[k]
            input[inBase + k] = kotlin.math.sqrt(re[k] * re[k] + im[k] * im[k])
          }
        }
      }

      val vocalsSpec = run(vocalsNet, input, shape)
      val accompSpec = run(accompanimentNet, input, shape)

      for (ch in 0..1) {
        for (f in 0 until count) {
          val base = f * BINS
          val inBase = ch * CHUNK * BINS + base
          // Ratio masks, exactly as sherpa-onnx computes them.
          for (k in 0 until BINS) {
            val v = vocalsSpec[inBase + k]
            val a = accompSpec[inBase + k]
            val sv = v * v
            val sa = a * a
            val sum = sv + sa + 1e-10f
            val mv = (sv + 5e-11f) / sum
            val ma = (sa + 5e-11f) / sum
            val e = specRe[ch][base + k] * specRe[ch][base + k] + specIm[ch][base + k] * specIm[ch][base + k]
            vocalEnergy += e * mv
            totalEnergy += e
            // Keep the masks in the network buffers for the inverse STFT below.
            vocalsSpec[inBase + k] = mv
            accompSpec[inBase + k] = ma
          }
          val start = (f0 + f) * HOP
          if (vocalsOut != null) {
            overlapAdd(fft, specRe[ch], specIm[ch], vocalsSpec, base, inBase, window, vocalsOut[ch], start, re, im, frame)
          }
          if (accompOut != null) {
            overlapAdd(fft, specRe[ch], specIm[ch], accompSpec, base, inBase, window, accompOut[ch], start, re, im, frame)
          }
        }
      }
      onChunk(c + 1, chunks)
    }

    // Divide by the summed squared window (as kaldi-native-fbank's iSTFT does).
    val denominator = FloatArray(n)
    for (f in 0 until frames) {
      val start = f * HOP
      for (i in 0 until N_FFT) denominator[start + i] += window[i] * window[i]
    }
    for (out in listOfNotNull(vocalsOut, accompOut)) {
      for (ch in 0..1) {
        val y = out[ch]
        for (i in 0 until n) {
          val d = denominator[i]
          y[i] = if (d > 0f) y[i] / d else 0f
        }
      }
    }

    return SeparatedSong(
      vocals = vocalsOut?.let { StereoPcm(it[0], it[1]) },
      accompaniment = accompOut?.let { StereoPcm(it[0], it[1]) },
      vocalRatio = if (totalEnergy > 0) vocalEnergy / totalEnergy else 0.0,
    )
  }

  private fun overlapAdd(
    fft: RealFft,
    specRe: FloatArray,
    specIm: FloatArray,
    mask: FloatArray,
    base: Int,
    maskBase: Int,
    window: FloatArray,
    out: FloatArray,
    start: Int,
    re: FloatArray,
    im: FloatArray,
    frame: FloatArray,
  ) {
    re.fill(0f)
    im.fill(0f)
    for (k in 0 until BINS) {
      val m = mask[maskBase + k]
      re[k] = specRe[base + k] * m
      im[k] = specIm[base + k] * m
    }
    fft.inverse(re, im, frame)
    for (i in 0 until N_FFT) out[start + i] += frame[i] * window[i]
  }

  private fun run(session: OrtSession, input: FloatArray, shape: LongArray): FloatArray {
    OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
      session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
        val buffer = (result.get(0) as OnnxTensor).floatBuffer
        val out = FloatArray(buffer.remaining())
        buffer.get(out)
        return out
      }
    }
  }

  override fun close() {
    runCatching { vocalsNet.close() }
    runCatching { accompanimentNet.close() }
    runCatching { options.close() }
  }

  companion object {
    const val N_FFT = 4096
    const val HOP = 1024
    const val BINS = 1024
    const val CHUNK = 512

    fun framesFor(samples: Int): Int = if (samples < N_FFT) 0 else 1 + (samples - N_FFT) / HOP
  }
}

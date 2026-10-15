package echo.music.iad1tya.mash

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Tempo of a passage: [bpm], first beat at [phaseSec], [confidence] 0..1. */
class TempoInfo(val bpm: Double, val phaseSec: Double, val confidence: Double)

/** Key of a passage: pitch class [root] (0 = C), [minor], [confidence] (correlation margin). */
class KeyInfo(val root: Int, val minor: Boolean, val confidence: Double) {
  /** Root of the relative major (minor keys move up 3 semitones). */
  val majorRoot: Int
    get() = if (minor) (root + 3) % 12 else root

  override fun toString(): String =
    NOTE_NAMES[root] + if (minor) "m" else ""

  companion object {
    val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
  }
}

/** Beat tracking and key detection on [MASH_RATE] audio. */
object MashAnalysis {
  /** Onset envelope frames per second (hop of 441 samples). */
  const val ENV_RATE = 100.0
  private const val HOP = 441
  private const val FFT = 2048

  /** Spectral flux of log-compressed magnitudes up to ~8 kHz, local mean removed. */
  fun onsetEnvelope(mono: FloatArray): FloatArray {
    val frames = if (mono.size < FFT) 0 else 1 + (mono.size - FFT) / HOP
    val env = FloatArray(frames)
    if (frames == 0) return env
    val fft = RealFft(FFT)
    val window = hannWindow(FFT)
    val buf = FloatArray(FFT)
    val re = FloatArray(FFT / 2 + 1)
    val im = FloatArray(FFT / 2 + 1)
    val maxBin = (8000.0 * FFT / MASH_RATE).toInt()
    var prev = FloatArray(maxBin)
    var cur = FloatArray(maxBin)
    for (f in 0 until frames) {
      val start = f * HOP
      for (i in 0 until FFT) buf[i] = mono[start + i] * window[i]
      fft.forward(buf, re, im)
      var flux = 0f
      for (k in 1 until maxBin) {
        val mag = sqrt(re[k] * re[k] + im[k] * im[k])
        val v = ln(1f + 100f * mag)
        cur[k] = v
        if (f > 0) {
          val d = v - prev[k]
          if (d > 0) flux += d
        }
      }
      env[f] = flux
      val t = prev
      prev = cur
      cur = t
    }
    // Remove the slow trend (a 0.4 s moving average) and keep the positive part.
    val half = 20
    val out = FloatArray(frames)
    var sum = 0.0
    var count = 0
    var lo = 0
    var hi = -1
    for (i in 0 until frames) {
      while (hi < minOf(frames - 1, i + half)) {
        hi++
        sum += env[hi]
        count++
      }
      while (lo < i - half) {
        sum -= env[lo]
        lo++
        count--
      }
      out[i] = (env[i] - sum / count).toFloat().coerceAtLeast(0f)
    }
    return out
  }

  /** Tempo by autocorrelation (with a preference for ~120 BPM), refined by a comb over the beats. */
  fun tempo(env: FloatArray): TempoInfo {
    if (env.size < 400) return TempoInfo(120.0, 0.0, 0.0)
    val mean = env.average()
    val x = DoubleArray(env.size) { env[it] - mean }
    val minLag = (ENV_RATE * 60 / 200).toInt() // 200 BPM
    val maxLag = (ENV_RATE * 60 / 60).toInt() // 60 BPM
    val ac = DoubleArray(maxLag + 2)
    var r0 = 0.0
    for (i in x.indices) r0 += x[i] * x[i]
    for (lag in minLag - 1..maxLag + 1) {
      var s = 0.0
      for (i in 0 until x.size - lag) s += x[i] * x[i + lag]
      ac[lag] = s
    }
    var bestLag = minLag
    var bestScore = Double.NEGATIVE_INFINITY
    for (lag in minLag..maxLag) {
      val bpm = ENV_RATE * 60 / lag
      val prior = exp(-0.5 * (log2(bpm / 120.0) / 0.9).let { it * it })
      val score = ac[lag] * prior
      if (score > bestScore) {
        bestScore = score
        bestLag = lag
      }
    }
    // Parabolic interpolation around the peak.
    val a = ac[bestLag - 1]
    val b = ac[bestLag]
    val c = ac[bestLag + 1]
    val denom = a - 2 * b + c
    val shift = if (denom != 0.0) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0
    val coarseBpm = ENV_RATE * 60 / (bestLag + shift)

    // Comb refinement: the BPM and phase whose beat grid lands on the most onset energy.
    var bpm = coarseBpm
    var phase = 0.0
    var best = Double.NEGATIVE_INFINITY
    var candidate = coarseBpm * 0.97
    while (candidate <= coarseBpm * 1.03) {
      val period = ENV_RATE * 60 / candidate
      val (ph, score) = bestPhase(env, period)
      if (score > best) {
        best = score
        bpm = candidate
        phase = ph
      }
      candidate += 0.02
    }
    val confidence = if (r0 > 0) (ac[bestLag] / r0).coerceIn(0.0, 1.0) else 0.0
    return TempoInfo(bpm, phase / ENV_RATE, confidence)
  }

  /** Phase (frames) maximizing the mean envelope on beats spaced by [period]. */
  private fun bestPhase(env: FloatArray, period: Double): Pair<Double, Double> {
    var bestPh = 0.0
    var bestScore = Double.NEGATIVE_INFINITY
    var ph = 0.0
    while (ph < period) {
      var s = 0.0
      var n = 0
      var t = ph
      while (t < env.size - 1) {
        s += interp(env, t)
        n++
        t += period
      }
      val score = if (n > 0) s / n else 0.0
      if (score > bestScore) {
        bestScore = score
        bestPh = ph
      }
      ph += 0.5
    }
    return bestPh to bestScore
  }

  fun interp(x: FloatArray, t: Double): Double {
    if (t <= 0) return x.firstOrNull()?.toDouble() ?: 0.0
    val i = floor(t).toInt()
    if (i >= x.size - 1) return x.lastOrNull()?.toDouble() ?: 0.0
    val f = t - i
    return x[i] * (1 - f) + x[i + 1] * f
  }

  private val MAJOR =
    doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
  private val MINOR =
    doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

  /** Krumhansl–Schmuckler key from a chroma of 65 Hz – 2.1 kHz. */
  fun key(mono: FloatArray): KeyInfo {
    val n = 8192
    val fft = RealFft(n)
    val window = hannWindow(n)
    val buf = FloatArray(n)
    val re = FloatArray(n / 2 + 1)
    val im = FloatArray(n / 2 + 1)
    val chroma = DoubleArray(12)
    val loBin = (65.0 * n / MASH_RATE).toInt().coerceAtLeast(1)
    val hiBin = (2100.0 * n / MASH_RATE).toInt()
    val pitchClass = IntArray(hiBin + 1) { k ->
      if (k < loBin) -1
      else {
        val f = k.toDouble() * MASH_RATE / n
        ((12 * log2(f / 440.0)).roundToInt() + 69).mod(12)
      }
    }
    var start = 0
    while (start + n <= mono.size) {
      for (i in 0 until n) buf[i] = mono[start + i] * window[i]
      fft.forward(buf, re, im)
      for (k in loBin..hiBin) {
        val pc = pitchClass[k]
        if (pc >= 0) chroma[pc] += ln(1.0 + sqrt((re[k] * re[k] + im[k] * im[k]).toDouble()))
      }
      start += n / 2
    }
    var best = -2.0
    var second = -2.0
    var bestRoot = 0
    var bestMinor = false
    for (minor in listOf(false, true)) {
      val profile = if (minor) MINOR else MAJOR
      for (root in 0 until 12) {
        val r = correlation(chroma, profile, root)
        if (r > best) {
          second = best
          best = r
          bestRoot = root
          bestMinor = minor
        } else if (r > second) {
          second = r
        }
      }
    }
    return KeyInfo(bestRoot, bestMinor, (best - second).coerceAtLeast(0.0))
  }

  private fun correlation(chroma: DoubleArray, profile: DoubleArray, root: Int): Double {
    val mc = chroma.average()
    val mp = profile.average()
    var num = 0.0
    var dc = 0.0
    var dp = 0.0
    for (i in 0 until 12) {
      val c = chroma[(i + root) % 12] - mc
      val p = profile[i] - mp
      num += c * p
      dc += c * c
      dp += p * p
    }
    return if (dc <= 0 || dp <= 0) 0.0 else num / sqrt(dc * dp)
  }

  /** Semitones to move [voice] into [beat]'s key, or 0 when they're too far apart or unsure. */
  fun semitoneShift(beat: KeyInfo, voice: KeyInfo): Int {
    var d = (beat.majorRoot - voice.majorRoot).mod(12)
    if (d > 6) d -= 12
    val sure = beat.confidence > 0.02 && voice.confidence > 0.02
    return if (sure && abs(d) <= 4) d else 0
  }

}

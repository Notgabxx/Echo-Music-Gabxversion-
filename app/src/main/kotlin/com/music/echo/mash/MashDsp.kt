package echo.music.iad1tya.mash

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** Stereo audio at [MASH_RATE], one float array per channel (-1..1). */
class StereoPcm(val left: FloatArray, val right: FloatArray) {
  val size: Int
    get() = left.size

  fun mono(): FloatArray = FloatArray(size) { (left[it] + right[it]) * 0.5f }

  fun slice(from: Int, to: Int): StereoPcm =
    StereoPcm(left.copyOfRange(from, to), right.copyOfRange(from, to))
}

/** The rate everything is processed at (Spleeter's). */
const val MASH_RATE = 44100

/**
 * Real FFT of size [n] (a power of two) through an n/2-point complex FFT. [forward] gives bins
 * 0..n/2; [inverse] takes them back to n samples (scaled by 1/n).
 */
internal class RealFft(val n: Int) {
  private val m = n / 2
  private val bitRev = IntArray(m)
  private val cosM = FloatArray(m / 2)
  private val sinM = FloatArray(m / 2)
  private val cosN = FloatArray(m + 1)
  private val sinN = FloatArray(m + 1)
  private val zr = FloatArray(m)
  private val zi = FloatArray(m)

  init {
    require(n >= 4 && n and (n - 1) == 0) { "n must be a power of two" }
    val bits = Integer.numberOfTrailingZeros(m)
    for (i in 0 until m) bitRev[i] = Integer.reverse(i) ushr (32 - bits)
    for (i in 0 until m / 2) {
      cosM[i] = cos(2.0 * PI * i / m).toFloat()
      sinM[i] = sin(2.0 * PI * i / m).toFloat()
    }
    for (k in 0..m) {
      cosN[k] = cos(2.0 * PI * k / n).toFloat()
      sinN[k] = sin(2.0 * PI * k / n).toFloat()
    }
  }

  /** In-place complex FFT of (zr, zi); [inverse] uses +i (no scaling). */
  private fun complexFft(re: FloatArray, im: FloatArray, inverse: Boolean) {
    for (i in 0 until m) {
      val j = bitRev[i]
      if (j > i) {
        var t = re[i]
        re[i] = re[j]
        re[j] = t
        t = im[i]
        im[i] = im[j]
        im[j] = t
      }
    }
    val sign = if (inverse) 1f else -1f
    var size = 2
    while (size <= m) {
      val halfSize = size / 2
      val step = m / size
      var start = 0
      while (start < m) {
        var k = 0
        for (j in start until start + halfSize) {
          val wr = cosM[k]
          val wi = sign * sinM[k]
          val l = j + halfSize
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

  /** [input] (n samples) → [outRe]/[outIm] (n/2 + 1 bins). */
  fun forward(input: FloatArray, outRe: FloatArray, outIm: FloatArray) {
    for (k in 0 until m) {
      zr[k] = input[2 * k]
      zi[k] = input[2 * k + 1]
    }
    complexFft(zr, zi, inverse = false)
    for (k in 0..m) {
      val a = k % m
      val b = (m - k) % m
      val ar = zr[a]
      val ai = zi[a]
      val br = zr[b]
      val bi = -zi[b]
      val er = (ar + br) * 0.5f
      val ei = (ai + bi) * 0.5f
      val or = (ai - bi) * 0.5f
      val oi = -(ar - br) * 0.5f
      val wr = cosN[k]
      val wi = -sinN[k]
      outRe[k] = er + or * wr - oi * wi
      outIm[k] = ei + or * wi + oi * wr
    }
  }

  /** Bins 0..n/2 → [output] (n samples), scaled by 1/n. The inputs are left untouched. */
  fun inverse(inRe: FloatArray, inIm: FloatArray, output: FloatArray) {
    for (k in 0 until m) {
      val xr = inRe[k]
      val xi = inIm[k]
      val cr = inRe[m - k]
      val ci = -inIm[m - k]
      val er = (xr + cr) * 0.5f
      val ei = (xi + ci) * 0.5f
      val dr = (xr - cr) * 0.5f
      val di = (xi - ci) * 0.5f
      // O = D · conj(W^k), W = e^(-2πi/n)  →  conj(W^k) = cos + i·sin
      val wr = cosN[k]
      val wi = sinN[k]
      val or = dr * wr - di * wi
      val oi = dr * wi + di * wr
      // Z = E + i·O
      zr[k] = er - oi
      zi[k] = ei + or
    }
    complexFft(zr, zi, inverse = true)
    val scale = 1f / m
    for (k in 0 until m) {
      output[2 * k] = zr[k] * scale
      output[2 * k + 1] = zi[k] * scale
    }
  }
}

/** Periodic Hann window (torch.hann_window default). */
internal fun hannWindow(size: Int): FloatArray =
  FloatArray(size) { (0.5 - 0.5 * cos(2.0 * PI * it / size)).toFloat() }

/**
 * Windowed-sinc resampler (32 taps, Blackman window, 512 phases with linear interpolation). Used
 * for 48 → 44.1 kHz and for pitch shifting.
 */
internal object Resampler {
  private const val HALF_TAPS = 16
  private const val PHASES = 512
  private val table: FloatArray by lazy { buildTable(1.0) }

  private fun buildTable(cutoff: Double): FloatArray {
    val taps = HALF_TAPS * 2
    val t = FloatArray((PHASES + 1) * taps)
    for (p in 0..PHASES) {
      val frac = p.toDouble() / PHASES
      for (j in 0 until taps) {
        val x = (j - HALF_TAPS + 1) - frac // distance from the output point
        val sinc = if (abs(x) < 1e-9) 1.0 else sin(PI * x * cutoff) / (PI * x * cutoff)
        val w = (x + HALF_TAPS) / (2.0 * HALF_TAPS)
        val blackman =
          if (w <= 0.0 || w >= 1.0) 0.0
          else 0.42 - 0.5 * cos(2 * PI * w) + 0.08 * cos(4 * PI * w)
        t[p * taps + j] = (sinc * blackman * cutoff).toFloat()
      }
    }
    return t
  }

  /** Resamples [input] by [ratio] = outputRate / inputRate. */
  fun resample(input: FloatArray, ratio: Double): FloatArray {
    if (abs(ratio - 1.0) < 1e-9) return input.copyOf()
    val outLen = floor(input.size * ratio).toInt()
    val out = FloatArray(outLen)
    // Downsampling needs a lower cutoff to avoid aliasing.
    val tab = if (ratio < 1.0) buildTable(ratio) else table
    val taps = HALF_TAPS * 2
    val step = 1.0 / ratio
    for (i in 0 until outLen) {
      val pos = i * step
      val base = floor(pos).toInt()
      val frac = pos - base
      val pf = frac * PHASES
      val p0 = pf.toInt().coerceAtMost(PHASES - 1)
      val pw = (pf - p0).toFloat()
      val row0 = p0 * taps
      val row1 = row0 + taps
      var acc = 0f
      var j = 0
      var src = base - HALF_TAPS + 1
      while (j < taps) {
        if (src in input.indices) {
          val c = tab[row0 + j] + (tab[row1 + j] - tab[row0 + j]) * pw
          acc += input[src] * c
        }
        j++
        src++
      }
      out[i] = acc
    }
    return out
  }
}

/**
 * WSOLA time stretch: plays [input] at [speed] (1.2 = 20% faster) without changing its pitch.
 * Frames of 2048 samples with 50% overlap; each frame is taken where it best continues the
 * previous one (±512 samples around its nominal place).
 */
internal object Wsola {
  private const val N = 2048
  private const val HS = N / 2
  private const val TOL = 512

  fun stretch(input: StereoPcm, speed: Double): StereoPcm {
    if (abs(speed - 1.0) < 1e-4) return StereoPcm(input.left.copyOf(), input.right.copyOf())
    val inLen = input.size
    val outLen = (inLen / speed).toInt()
    val outL = FloatArray(outLen + N)
    val outR = FloatArray(outLen + N)
    val window = hannWindow(N)
    val mono = input.mono()
    var prevPos = 0
    var k = 0
    val lastStart = inLen - N
    if (lastStart <= 0) return StereoPcm(input.left.copyOf(), input.right.copyOf())
    while (k * HS < outLen) {
      val nominal = (k * HS * speed).toInt()
      val pos =
        if (k == 0) {
          0
        } else {
          val natural = (prevPos + HS).coerceAtMost(lastStart)
          bestMatch(mono, natural, nominal, lastStart)
        }.coerceIn(0, lastStart)
      val outStart = k * HS
      for (j in 0 until N) {
        val w = window[j]
        outL[outStart + j] += input.left[pos + j] * w
        outR[outStart + j] += input.right[pos + j] * w
      }
      prevPos = pos
      k++
    }
    return StereoPcm(outL.copyOf(outLen), outR.copyOf(outLen))
  }

  /** Candidate start near [nominal] whose first half looks most like the [natural] continuation. */
  private fun bestMatch(mono: FloatArray, natural: Int, nominal: Int, lastStart: Int): Int {
    val lo = (nominal - TOL).coerceAtLeast(0)
    val hi = (nominal + TOL).coerceAtMost(lastStart)
    if (hi <= lo) return nominal.coerceIn(0, lastStart)
    var best = nominal.coerceIn(lo, hi)
    var bestScore = Float.NEGATIVE_INFINITY
    // Coarse search (every 2 samples, every 4th product), then refine around the winner.
    var c = lo
    while (c <= hi) {
      val s = similarity(mono, natural, c, 4)
      if (s > bestScore) {
        bestScore = s
        best = c
      }
      c += 2
    }
    val fineLo = (best - 2).coerceAtLeast(lo)
    val fineHi = (best + 2).coerceAtMost(hi)
    bestScore = Float.NEGATIVE_INFINITY
    for (f in fineLo..fineHi) {
      val s = similarity(mono, natural, f, 1)
      if (s > bestScore) {
        bestScore = s
        best = f
      }
    }
    return best
  }

  private fun similarity(x: FloatArray, a: Int, b: Int, stride: Int): Float {
    var dot = 0f
    var ea = 0f
    var eb = 0f
    var j = 0
    while (j < HS) {
      val va = x[a + j]
      val vb = x[b + j]
      dot += va * vb
      ea += va * va
      eb += vb * vb
      j += stride
    }
    return if (ea <= 0f || eb <= 0f) 0f else dot / sqrt(ea * eb)
  }
}

/** RBJ biquad high-pass, applied in place. */
internal fun highPass(x: FloatArray, cutoffHz: Double, sampleRate: Int = MASH_RATE) {
  val w0 = 2 * PI * cutoffHz / sampleRate
  val alpha = sin(w0) / (2 * 0.7071)
  val cosW = cos(w0)
  val a0 = 1 + alpha
  val b0 = ((1 + cosW) / 2 / a0).toFloat()
  val b1 = (-(1 + cosW) / a0).toFloat()
  val b2 = b0
  val a1 = (-2 * cosW / a0).toFloat()
  val a2 = ((1 - alpha) / a0).toFloat()
  var x1 = 0f
  var x2 = 0f
  var y1 = 0f
  var y2 = 0f
  for (i in x.indices) {
    val x0 = x[i]
    val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
    x2 = x1
    x1 = x0
    y2 = y1
    y1 = y0
    x[i] = y0
  }
}

internal fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Double {
  var s = 0.0
  for (i in from until to) s += x[i] * x[i]
  return if (to > from) sqrt(s / (to - from)) else 0.0
}

/** Gentle limiter: transparent below 0.8, rounds peaks off above it. */
internal fun softClip(v: Float): Float {
  val a = abs(v)
  if (a <= 0.8f) return v
  val over = (a - 0.8f) / 0.2f
  val y = 0.8f + 0.2f * tanh(over.toDouble()).toFloat()
  return if (v < 0) -y else y
}

/** Writes 16-bit stereo PCM WAV. */
internal fun writeWav(file: File, pcm: StereoPcm, sampleRate: Int = MASH_RATE) {
  val frames = pcm.size
  val dataBytes = frames * 4
  DataOutputStream(BufferedOutputStream(FileOutputStream(file), 1 shl 16)).use { out ->
    fun le32(v: Int) = out.writeInt(Integer.reverseBytes(v))
    fun le16(v: Int) = out.writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt())
    out.writeBytes("RIFF")
    le32(36 + dataBytes)
    out.writeBytes("WAVE")
    out.writeBytes("fmt ")
    le32(16)
    le16(1)
    le16(2)
    le32(sampleRate)
    le32(sampleRate * 4)
    le16(4)
    le16(16)
    out.writeBytes("data")
    le32(dataBytes)
    for (i in 0 until frames) {
      le16((max(-1f, min(1f, pcm.left[i])) * 32767f).toInt())
      le16((max(-1f, min(1f, pcm.right[i])) * 32767f).toInt())
    }
  }
}

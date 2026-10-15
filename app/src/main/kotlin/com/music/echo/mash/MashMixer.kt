package echo.music.iad1tya.mash

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** One song's passage after analysis. */
class SongPart(
  val mix: StereoPcm,
  val envelope: FloatArray,
  val tempo: TempoInfo,
  val key: KeyInfo,
) {
  companion object {
    fun analyze(mix: StereoPcm): SongPart {
      val mono = mix.mono()
      val env = MashAnalysis.onsetEnvelope(mono)
      return SongPart(mix, env, MashAnalysis.tempo(env), MashAnalysis.key(mono))
    }
  }
}

/** How the voice is fitted onto the beat. */
class MashPlan(
  /** Playback speed applied to the voice (beat BPM / voice BPM, octave-corrected). */
  val speed: Double,
  /** Semitones the voice is moved to match the beat's key. */
  val semitones: Int,
  /** Delay of the voice so its beats land on the beat's beats (seconds). */
  val voiceDelaySec: Double,
  val beatBpm: Double,
  val voiceBpm: Double,
)

object MashMixer {
  private const val BEAT_RMS = 0.11 // ≈ -19 dBFS
  private const val VOICE_RMS = 0.14 // ≈ -17 dBFS

  /**
   * True when the first song should give the beat: the voice comes from the song whose voice
   * carries more of its energy, the beat from the song with the steadier rhythm.
   */
  fun firstIsBeat(
    firstVocalRatio: Double,
    secondVocalRatio: Double,
    first: SongPart,
    second: SongPart,
  ): Boolean {
    val score =
      (secondVocalRatio - firstVocalRatio) + 0.5 * (first.tempo.confidence - second.tempo.confidence)
    return score >= 0
  }

  fun plan(beat: SongPart, voice: SongPart): MashPlan {
    // Bring the tempo ratio within an octave of 1 (a 70 BPM voice can ride a 140 BPM beat).
    var ratio = beat.tempo.bpm / voice.tempo.bpm
    while (ratio > 1.41) ratio /= 2
    while (ratio < 0.71) ratio *= 2
    val speed = ratio.coerceIn(0.75, 1.35)
    val semitones = MashAnalysis.semitoneShift(beat.key, voice.key)

    // Beat grids: phase of the voice after the speed change, aligned to the beat's grid, then
    // the bar offset (0–3 beats) whose onsets agree best.
    val beatPeriod = 60.0 / beat.tempo.bpm
    val voicePhase = voice.tempo.phaseSec / speed
    var base = (beat.tempo.phaseSec - voicePhase) % beatPeriod
    if (base < 0) base += beatPeriod
    var bestDelay = base
    var bestScore = Double.NEGATIVE_INFINITY
    for (k in 0..3) {
      val delay = base + k * beatPeriod
      val score = envelopeAgreement(beat.envelope, voice.envelope, speed, delay)
      if (score > bestScore) {
        bestScore = score
        bestDelay = delay
      }
    }
    return MashPlan(speed, semitones, bestDelay, beat.tempo.bpm, voice.tempo.bpm)
  }

  /** Correlation of the beat's onsets with the voice song's onsets, sped up and delayed. */
  private fun envelopeAgreement(beat: FloatArray, voice: FloatArray, speed: Double, delaySec: Double): Double {
    val rate = MashAnalysis.ENV_RATE
    var s = 0.0
    var i = (delaySec * rate).toInt()
    while (i < beat.size) {
      val t = (i / rate - delaySec) * speed * rate
      if (t >= voice.size - 1) break
      if (t >= 0) s += beat[i] * MashAnalysis.interp(voice, t)
      i++
    }
    return s
  }

  /** Fits the voice to the plan: tempo by WSOLA, key by resampling. */
  fun fitVoice(voice: StereoPcm, plan: MashPlan): StereoPcm {
    val pitch = 2.0.pow(plan.semitones / 12.0)
    // Stretch to speed/pitch, then resample by 1/pitch: overall speed = plan.speed, pitch × pitch.
    val stretched = Wsola.stretch(voice, plan.speed / pitch)
    if (plan.semitones == 0) return stretched
    val ratio = 1.0 / pitch
    return StereoPcm(
      Resampler.resample(stretched.left, ratio),
      Resampler.resample(stretched.right, ratio),
    )
  }

  /**
   * Final mix: beat and voice leveled, the voice cleaned of low-end bleed, the beat ducked a
   * little under the voice, fades and a gentle limiter.
   */
  fun render(beat: StereoPcm, voice: StereoPcm, plan: MashPlan): StereoPcm {
    val delay = (plan.voiceDelaySec * MASH_RATE).toInt()
    val length = min(beat.size, delay + voice.size)
    val vl = voice.left.copyOf()
    val vr = voice.right.copyOf()
    highPass(vl, 140.0)
    highPass(vr, 140.0)

    val beatGain = gainFor(beat, BEAT_RMS)
    val voiceGain = gainFor(StereoPcm(vl, vr), VOICE_RMS)

    // Voice envelope (30 ms attack, 250 ms release) drives a soft duck of up to 3 dB.
    val attack = exp(-1.0 / (0.030 * MASH_RATE)).toFloat()
    val release = exp(-1.0 / (0.250 * MASH_RATE)).toFloat()
    var envelope = 0f
    val outL = FloatArray(length)
    val outR = FloatArray(length)
    val fadeIn = (1.0 * MASH_RATE).toInt()
    val fadeOut = (2.5 * MASH_RATE).toInt()
    val voiceRef = (VOICE_RMS * 1.4).toFloat()
    for (i in 0 until length) {
      val j = i - delay
      val v0 = if (j in vl.indices) vl[j] * voiceGain else 0f
      val v1 = if (j in vr.indices) vr[j] * voiceGain else 0f
      val level = max(abs(v0), abs(v1))
      envelope = if (level > envelope) attack * envelope + (1 - attack) * level else release * envelope + (1 - release) * level
      val duck = 1f - 0.3f * min(1f, envelope / voiceRef)
      var l = beat.left[i] * beatGain * duck + v0
      var r = beat.right[i] * beatGain * duck + v1
      val fade =
        when {
          i < fadeIn -> i.toFloat() / fadeIn
          i > length - fadeOut -> (length - i).toFloat() / fadeOut
          else -> 1f
        }
      l *= fade
      r *= fade
      outL[i] = softClip(l)
      outR[i] = softClip(r)
    }
    return StereoPcm(outL, outR)
  }

  private fun gainFor(pcm: StereoPcm, target: Double): Float {
    // Level from the loud parts only (silences and intros shouldn't make the gain jump).
    val block = MASH_RATE / 10
    val levels = ArrayList<Double>()
    var start = 0
    while (start + block <= pcm.size) {
      levels += sqrt((rms(pcm.left, start, start + block).let { it * it } + rms(pcm.right, start, start + block).let { it * it }) / 2)
      start += block
    }
    if (levels.isEmpty()) return 1f
    levels.sort()
    val loud = levels.subList((levels.size * 0.5).toInt(), levels.size).average()
    if (loud <= 1e-6) return 1f
    return (target / loud).coerceIn(0.1, 8.0).toFloat()
  }
}

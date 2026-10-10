package echo.music.iad1tya.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import echo.music.iad1tya.playback.audio.AudioLevels
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Per-frame audio dynamics shared by the halo and the artwork pulse.
 *
 * Values are sampled from [AudioLevels] once per display frame, delayed to line up with what is
 * actually audible (the analysis runs ahead of the speaker by roughly the audio-track buffer), and
 * smoothed with separate attack/release time constants so motion feels organic instead of jittery.
 * All reads happen in draw / graphics-layer lambdas: the composition is never invalidated per frame.
 */
@Stable
class AudioReactiveState internal constructor() {
  internal val frameTick = mutableLongStateOf(0L)

  private var bassValue = 0f
  private var midValue = 0f
  private var trebleValue = 0f
  private var kickValue = 0f
  private var bassAverage = 0f
  private var phaseValue = 0f
  private var lastNanos = 0L

  private val historyTime = LongArray(HISTORY)
  private val historyBass = FloatArray(HISTORY)
  private val historyMid = FloatArray(HISTORY)
  private val historyTreble = FloatArray(HISTORY)
  private var historyHead = 0

  /** Smoothed bass energy, 0..1. Read only from draw/layer lambdas. */
  val bass: Float
    get() {
      frameTick.longValue
      return bassValue
    }

  /** Smoothed mid energy, 0..1. Read only from draw/layer lambdas. */
  val mid: Float
    get() {
      frameTick.longValue
      return midValue
    }

  /** Smoothed treble energy, 0..1. Read only from draw/layer lambdas. */
  val treble: Float
    get() {
      frameTick.longValue
      return trebleValue
    }

  /** Fast-decaying onset envelope (beat "kick"), 0..1. Read only from draw/layer lambdas. */
  val kick: Float
    get() {
      frameTick.longValue
      return kickValue
    }

  /** Monotonic organic phase in turns; advances faster when the mids are busy. */
  val phase: Float
    get() {
      frameTick.longValue
      return phaseValue
    }

  /** Timestamp of the current frame ([System.nanoTime] clock). Read only from draw/layer lambdas. */
  val frameNanos: Long
    get() = frameTick.longValue

  internal val isAtRest: Boolean
    get() = bassValue < 0.003f && midValue < 0.003f && trebleValue < 0.003f && kickValue < 0.003f

  internal fun step(now: Long, active: Boolean) {
    val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
    lastNanos = now

    // Record the live analysis into the delay line.
    val fresh = active && AudioLevels.isFresh(now)
    historyHead = (historyHead + 1) % HISTORY
    historyTime[historyHead] = now
    if (fresh) {
      historyBass[historyHead] = AudioLevels.bass
      historyMid[historyHead] = AudioLevels.mid
      historyTreble[historyHead] = AudioLevels.treble
    } else if (active) {
      // No analysis available (audio offload, bit-perfect USB output, casting…): breathe gently
      // so the halo still feels alive while music plays.
      val t = now / 1_000_000_000.0
      historyBass[historyHead] = (0.32 + 0.12 * sin(t * 2.0 * PI * 0.22)).toFloat()
      historyMid[historyHead] = (0.28 + 0.08 * sin(t * 2.0 * PI * 0.13 + 1.3)).toFloat()
      historyTreble[historyHead] = 0.18f
    } else {
      historyBass[historyHead] = 0f
      historyMid[historyHead] = 0f
      historyTreble[historyHead] = 0f
    }

    // Pick the newest sample that is at least DELAY old (falls back to the oldest available).
    val target = now - AUDIBLE_DELAY_NANOS
    var index = historyHead
    for (i in 0 until HISTORY) {
      val candidate = (historyHead - i + HISTORY) % HISTORY
      index = candidate
      if (historyTime[candidate] != 0L && historyTime[candidate] <= target) break
    }
    val targetBass = historyBass[index]
    val targetMid = historyMid[index]
    val targetTreble = historyTreble[index]

    bassValue = follow(bassValue, targetBass, dt, attack = 0.045f, release = 0.22f)
    midValue = follow(midValue, targetMid, dt, attack = 0.07f, release = 0.32f)
    trebleValue = follow(trebleValue, targetTreble, dt, attack = 0.035f, release = 0.18f)

    bassAverage += (bassValue - bassAverage) * min(1f, dt / 0.8f)
    val onset = ((bassValue - bassAverage - 0.035f) * 4f).coerceIn(0f, 1f)
    kickValue = max(kickValue * exp(-dt / 0.16f), onset)

    phaseValue += dt * (0.08f + midValue * 0.32f)
  }

  private fun follow(current: Float, target: Float, dt: Float, attack: Float, release: Float): Float {
    val tau = if (target > current) attack else release
    return current + (target - current) * (1f - exp(-dt / tau))
  }

  private companion object {
    const val HISTORY = 96
    /** ≈ how far the decoder/analyser runs ahead of the speaker in steady state. */
    const val AUDIBLE_DELAY_NANOS = 200_000_000L
  }
}

/**
 * Drives an [AudioReactiveState] at display refresh rate while [active]; when it turns inactive
 * the values decay to rest and the frame loop stops, so a paused player costs nothing.
 */
@Composable
fun rememberAudioReactiveState(active: Boolean): AudioReactiveState {
  val state = remember { AudioReactiveState() }
  LaunchedEffect(active) {
    if (active) AudioLevels.acquire()
    try {
      while (true) {
        withFrameNanos { now ->
          state.step(now, active)
          state.frameTick.longValue = now
        }
        if (!active && state.isAtRest) break
      }
    } finally {
      if (active) AudioLevels.release()
    }
  }
  return state
}

/**
 * Organic aura behind the album artwork: three soft light pools orbit behind the cover, swelling
 * with the bass, flowing faster with the mids and flickering with the treble, plus a breathing
 * base glow. Drawn with radial-gradient shaders only (no blur passes), so it stays GPU-cheap.
 *
 * The modifier should match the artwork's bounds; the aura spills outside them by design.
 */
@Composable
fun AudioReactiveHalo(
  state: AudioReactiveState,
  primary: Color,
  secondary: Color,
  tertiary: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) { drawHalo(state, primary, secondary, tertiary, size) }
}

/**
 * Lets the full player draw the cover's aura behind its whole content. Drawn inside the artwork
 * pager, the light was clipped by the pager's bounds and ended in a hard line right above the
 * song title; drawn from the player's root it fades out naturally.
 */
@Stable
class ArtworkHaloHost {
  private var owner: Any? = null

  var state: AudioReactiveState? by mutableStateOf(null)
    private set

  /** Artwork bounds in root coordinates. */
  var bounds: Rect by mutableStateOf(Rect.Zero)
    private set

  fun attach(owner: Any, state: AudioReactiveState) {
    this.owner = owner
    this.state = state
  }

  fun detach(owner: Any) {
    if (this.owner === owner) {
      this.owner = null
      state = null
    }
  }

  fun updateBounds(owner: Any, bounds: Rect) {
    if (this.owner === owner) this.bounds = bounds
  }
}

val LocalArtworkHaloHost = staticCompositionLocalOf<ArtworkHaloHost?> { null }

/** Full-size layer that paints the halo of the artwork registered in [host]. */
@Composable
fun ArtworkHaloLayer(host: ArtworkHaloHost, modifier: Modifier = Modifier) {
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val tertiary = MaterialTheme.colorScheme.tertiary
  val origin = remember { mutableStateOf(Offset.Zero) }
  Canvas(modifier = modifier.onPlaced { origin.value = it.positionInRoot() }) {
    val state = host.state ?: return@Canvas
    val bounds = host.bounds
    if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas
    val layerOrigin = origin.value
    translate(left = bounds.left - layerOrigin.x, top = bounds.top - layerOrigin.y) {
      drawHalo(state, primary, secondary, tertiary, bounds.size)
    }
  }
}

private fun DrawScope.drawHalo(
  state: AudioReactiveState,
  primary: Color,
  secondary: Color,
  tertiary: Color,
  haloSize: Size,
) {
  val bass = state.bass
  val mid = state.mid
  val treble = state.treble
  val kick = state.kick
  val phase = state.phase

  val cx = haloSize.width / 2f
  val cy = haloSize.height / 2f
  val base = min(haloSize.width, haloSize.height) / 2f
  if (base <= 0f) return
  val center = Offset(cx, cy)

  // Breathing base glow — light spilling from behind the cover.
  val glowRadius = base * (1.32f + 0.12f * bass + 0.10f * kick)
  val glowAlpha = (0.30f + 0.28f * bass + 0.18f * kick).coerceAtMost(0.75f)
  drawCircle(
    brush =
      Brush.radialGradient(
        colorStops =
          arrayOf(
            0.0f to primary.copy(alpha = glowAlpha),
            0.62f to primary.copy(alpha = glowAlpha * 0.55f),
            1.0f to Color.Transparent,
          ),
        center = center,
        radius = glowRadius,
      ),
    radius = glowRadius,
    center = center,
  )

  // Three orbiting light pools.
  val twoPi = (2.0 * PI).toFloat()
  val pools =
    arrayOf(
      Triple(secondary, bass, 0.00f),
      Triple(tertiary, mid, 0.37f),
      Triple(primary, treble, 0.71f),
    )
  for ((color, energy, offset) in pools) {
    val angleA = twoPi * (phase * (0.9f + offset) + offset)
    val angleB = twoPi * (phase * (0.6f + offset * 0.5f) + offset * 1.7f)
    val orbit = base * (0.52f + 0.10f * energy)
    val poolCenter = Offset(cx + orbit * cos(angleA), cy + orbit * 0.85f * sin(angleB))
    val poolRadius = base * (0.78f + 0.30f * energy + 0.12f * kick)
    val poolAlpha = (0.20f + 0.40f * energy).coerceAtMost(0.62f)
    drawCircle(
      brush =
        Brush.radialGradient(
          colors = listOf(color.copy(alpha = poolAlpha), Color.Transparent),
          center = poolCenter,
          radius = poolRadius,
        ),
      radius = poolRadius,
      center = poolCenter,
    )
  }
}

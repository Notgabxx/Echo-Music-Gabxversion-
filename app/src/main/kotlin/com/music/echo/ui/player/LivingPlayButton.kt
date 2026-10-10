package echo.music.iad1tya.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The player's "living" play/pause button.
 *
 * - **Body**: while music plays the outline turns into a soft liquid shape — three slow waves flow
 *   around the edge and swell with the music (bass gives depth, mids speed up the flow, every beat
 *   gives a quick swell and a faint glow). On pause it settles back into a perfect circle.
 * - **Beat rings**: each detected beat sends a thin ring out from the edge, like a sound wave.
 * - **Glyph**: play ▶ and pause ❚❚ morph into each other — the triangle splits into the two bars —
 *   on a spring, instead of swapping icons.
 *
 * Everything that changes per frame is read in draw lambdas, so playing music never recomposes the
 * controls, and the frame loop stops completely while paused.
 *
 * @param living animate the body/rings with the music (the glyph morph is always on).
 * @param intensity scales the body deformation (use < 1 for small buttons).
 * @param overrideIcon shown instead of the play/pause glyph (e.g. mute for Listen Together guests).
 */
@Composable
fun LivingPlayButton(
  isPlaying: Boolean,
  onClick: () -> Unit,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
  living: Boolean = true,
  beatRings: Boolean = true,
  intensity: Float = 1f,
  glyphSize: Dp = 36.dp,
  overrideIcon: Painter? = null,
  enabled: Boolean = true,
  contentDescription: String? = null,
  interactionSource: MutableInteractionSource? = null,
) {
  val source = interactionSource ?: remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val pressScale by
    animateFloatAsState(
      targetValue = if (pressed) 0.88f else 1f,
      animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f),
      label = "livingPressScale",
    )
  // 0 = play glyph, 1 = pause glyph. A slightly under-damped spring gives the bars a little bounce.
  val glyphMorph by
    animateFloatAsState(
      targetValue = if (isPlaying) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
      label = "livingGlyphMorph",
    )
  val liveness by
    animateFloatAsState(
      targetValue = if (isPlaying && living) 1f else 0f,
      animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
      label = "livingLiveness",
    )

  val audio = rememberAudioReactiveState(active = isPlaying && living)
  val haptics = LocalHapticFeedback.current
  val drawing = remember { LivingButtonDrawing() }

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .graphicsLayer {
          scaleX = pressScale
          scaleY = pressScale
        }
        .semantics {
          role = Role.Button
          if (contentDescription != null) this.contentDescription = contentDescription
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
          haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onClick()
        }
        .drawBehind {
          val live = liveness
          drawing.drawBody(this, containerColor, live, intensity, audio)
          if (beatRings) drawing.drawBeatRings(this, containerColor, live, audio)
          if (overrideIcon == null) {
            drawing.drawGlyph(this, contentColor, glyphMorph, glyphSize.toPx())
          }
        }
  ) {
    if (overrideIcon != null) {
      Icon(
        painter = overrideIcon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(glyphSize),
      )
    }
  }
}

/** Reusable paths and beat-ring bookkeeping (plain fields: only touched from the draw pass). */
private class LivingButtonDrawing {
  private val bodyPath = Path()
  private val leftGlyph = Path()
  private val rightGlyph = Path()

  private val ringBirths = LongArray(MAX_RINGS)
  private var ringArmed = true

  fun drawBody(
    scope: DrawScope,
    color: Color,
    live: Float,
    intensity: Float,
    audio: AudioReactiveState,
  ) {
    with(scope) {
      val radius = size.minDimension / 2f
      if (live < 0.002f) {
        drawCircle(color = color, radius = radius, center = center)
        return
      }

      val bass = audio.bass
      val mid = audio.mid
      val kick = audio.kick
      val turn = audio.phase * 2f * PI.toFloat()

      // Beat glow: a soft light that blooms around the body on every kick.
      val glowAlpha = 0.22f * kick * live
      if (glowAlpha > 0.01f) {
        val glowRadius = radius * 1.55f
        drawCircle(
          brush =
            Brush.radialGradient(
              colors = listOf(color.copy(alpha = glowAlpha), Color.Transparent),
              center = center,
              radius = glowRadius,
            ),
          radius = glowRadius,
          center = center,
        )
      }

      // Kept subtle on purpose (≈3% of the radius with typical music): alive, not wobbly.
      val depth = live * intensity * (0.010f + 0.028f * bass + 0.012f * mid)
      val swell = 1f + live * intensity * 0.04f * kick
      bodyPath.rewind()
      for (i in 0..BODY_STEPS) {
        val theta = i * (2f * PI.toFloat() / BODY_STEPS)
        val wave =
          0.55f * sin(3f * theta + turn) +
            0.30f * sin(5f * theta - 1.6f * turn + 1.7f) +
            0.15f * sin(7f * theta + 2.3f * turn + 0.6f)
        val r = radius * swell * (1f + depth * wave)
        val x = center.x + r * cos(theta)
        val y = center.y + r * sin(theta)
        if (i == 0) bodyPath.moveTo(x, y) else bodyPath.lineTo(x, y)
      }
      bodyPath.close()
      drawPath(bodyPath, color)
    }
  }

  fun drawBeatRings(scope: DrawScope, color: Color, live: Float, audio: AudioReactiveState) {
    with(scope) {
      if (live < 0.01f) return
      val now = audio.frameNanos
      val kick = audio.kick

      // Spawn on the rising edge of a beat; re-arm once the envelope has dropped.
      if (ringArmed && kick > 0.6f && live > 0.5f) {
        var oldest = 0
        for (i in 1 until MAX_RINGS) if (ringBirths[i] < ringBirths[oldest]) oldest = i
        ringBirths[oldest] = now
        ringArmed = false
      } else if (kick < 0.35f) {
        ringArmed = true
      }

      val radius = size.minDimension / 2f
      for (birth in ringBirths) {
        if (birth == 0L) continue
        val progress = (now - birth) / RING_LIFETIME_NANOS
        if (progress < 0f || progress >= 1f) continue
        val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
        val fade = (1f - progress) * (1f - progress)
        drawCircle(
          color = color.copy(alpha = 0.32f * fade * live),
          radius = radius * (1.03f + 0.42f * eased),
          center = center,
          style = Stroke(width = 2.5.dp.toPx() * (1f - 0.5f * progress)),
        )
      }
    }
  }

  /** Draws the play ▶ ⇄ pause ❚❚ morph; [t] is 0 for play and 1 for pause (may overshoot). */
  fun drawGlyph(scope: DrawScope, color: Color, t: Float, glyphPx: Float) {
    val morph = t.coerceIn(-0.08f, 1.08f)
    val unit = glyphPx / 24f
    val cx = scope.center.x
    val cy = scope.center.y
    // A tiny dip mid-morph makes the change read as one gesture.
    val dip = 1f - 0.06f * sin(PI.toFloat() * morph.coerceIn(0f, 1f))

    setQuad(leftGlyph, PLAY_LEFT, PAUSE_LEFT, morph, unit, glyphPx, cx, cy, dip)
    setQuad(rightGlyph, PLAY_RIGHT, PAUSE_RIGHT, morph, unit, glyphPx, cx, cy, dip)

    // Fill + round-joined stroke = the same shapes with softly rounded corners.
    val rounding = Stroke(width = 2.2f * unit * dip, join = StrokeJoin.Round)
    scope.drawPath(leftGlyph, color)
    scope.drawPath(leftGlyph, color, style = rounding)
    scope.drawPath(rightGlyph, color)
    scope.drawPath(rightGlyph, color, style = rounding)
  }

  /** Writes the quad interpolated between [from] and [to] (24-unit grid) into [path], in pixels. */
  private fun setQuad(
    path: Path,
    from: FloatArray,
    to: FloatArray,
    morph: Float,
    unit: Float,
    glyphPx: Float,
    cx: Float,
    cy: Float,
    dip: Float,
  ) {
    path.rewind()
    val originX = cx - glyphPx / 2f
    val originY = cy - glyphPx / 2f
    for (i in 0 until 4) {
      val x = from[i * 2] + (to[i * 2] - from[i * 2]) * morph
      val y = from[i * 2 + 1] + (to[i * 2 + 1] - from[i * 2 + 1]) * morph
      val px = cx + (originX + x * unit - cx) * dip
      val py = cy + (originY + y * unit - cy) * dip
      if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
  }

  private companion object {
    const val BODY_STEPS = 72
    const val MAX_RINGS = 3
    const val RING_LIFETIME_NANOS = 750_000_000f

    // Glyph geometry on a 24-unit grid (inset by the 1.1-unit corner rounding).
    // Play: the triangle is cut in two at x = 13.3; each half maps onto one pause bar.
    val PLAY_LEFT = floatArrayOf(8.6f, 6f, 13.3f, 9f, 13.3f, 15f, 8.6f, 18f)
    val PLAY_RIGHT = floatArrayOf(13.3f, 9f, 18f, 12f, 18f, 12f, 13.3f, 15f)
    val PAUSE_LEFT = floatArrayOf(7f, 6f, 9f, 6f, 9f, 18f, 7f, 18f)
    val PAUSE_RIGHT = floatArrayOf(15f, 6f, 17f, 6f, 17f, 18f, 15f, 18f)
  }
}

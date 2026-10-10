package echo.music.iad1tya.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import echo.music.iad1tya.ui.theme.EchoMotion

/**
 * Album-tinted ambient light for the main screens.
 *
 * The app theme is already seeded from the current artwork (Material You via materialKolor); this
 * paints two very soft light pools from the theme's container tones behind the screen content, the
 * top bar and the liquid-glass surfaces. Containers are the roles M3 designs for tinted
 * backgrounds, so on-surface text stays legible on top.
 *
 * Colors animate between tracks (≈1 s, emphasized easing) and are only read while drawing, so a
 * track change repaints the backdrop without recomposing any screen. Nothing animates while idle:
 * the backdrop costs zero frames when the music is not changing.
 */
@Stable
class AmbientBackdropColors
internal constructor(
  private val first: State<Color>,
  private val second: State<Color>,
  internal val strength: Float,
) {
  internal val primary: Color
    get() = first.value

  internal val secondary: Color
    get() = second.value
}

@Composable
fun rememberAmbientBackdropColors(enabled: Boolean, pureBlack: Boolean): AmbientBackdropColors {
  val scheme = MaterialTheme.colorScheme
  val first =
    animateColorAsState(
      targetValue =
        if (enabled) scheme.primaryContainer else scheme.primaryContainer.copy(alpha = 0f),
      animationSpec = tween(durationMillis = 900, easing = EchoMotion.Emphasized),
      label = "ambientPrimary"
    )
  val second =
    animateColorAsState(
      targetValue =
        if (enabled) scheme.tertiaryContainer else scheme.tertiaryContainer.copy(alpha = 0f),
      animationSpec = tween(durationMillis = 1100, easing = EchoMotion.Emphasized),
      label = "ambientSecondary"
    )
  val strength = if (pureBlack) 0.38f else 0.6f
  return remember(first, second, strength) { AmbientBackdropColors(first, second, strength) }
}

/**
 * Draws the ambient pools. Geometry is derived from [referenceWidth] (the window width) so that
 * several surfaces anchored at the window origin (screen content, top bar, glass backdrop) paint
 * one continuous light field.
 */
fun DrawScope.drawAmbientBackdrop(colors: AmbientBackdropColors, referenceWidth: Float = size.width) {
  val first = colors.primary
  val second = colors.secondary
  if (first.alpha < 0.005f && second.alpha < 0.005f) return
  val width = if (referenceWidth > 0f) referenceWidth else size.width
  if (width <= 0f) return
  val strength = colors.strength

  drawRect(
    brush =
      Brush.radialGradient(
        colors =
          listOf(
            first.copy(alpha = first.alpha * strength),
            first.copy(alpha = first.alpha * strength * 0.45f),
            Color.Transparent,
          ),
        center = Offset(width * 0.18f, -width * 0.12f),
        radius = width * 1.15f,
      )
  )
  drawRect(
    brush =
      Brush.radialGradient(
        colors = listOf(second.copy(alpha = second.alpha * strength * 0.85f), Color.Transparent),
        center = Offset(width * 0.95f, width * 0.10f),
        radius = width * 0.9f,
      )
  )
}

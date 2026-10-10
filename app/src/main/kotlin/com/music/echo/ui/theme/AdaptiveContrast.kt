package echo.music.iad1tya.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * WCAG 2.x contrast helpers used to keep text legible on colors extracted from album artwork.
 *
 * The player and mini-player draw white text/icons on top of artwork-derived gradients. A bright
 * cover (yellow, white, pastel) used to produce a background that white text could not be read
 * on. These helpers darken (or lighten) a background just enough to reach the requested contrast
 * ratio while keeping its hue, so the "dynamic" look survives and legibility is guaranteed.
 */
object AdaptiveContrast {
  /** WCAG AA for normal text. */
  const val AA = 4.5f

  /** WCAG AA for large/bold text and UI glyphs (icons). */
  const val AA_LARGE = 3.0f

  fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return max(la, lb) / min(la, lb)
  }

  /**
   * Returns [background] shifted towards black (for light foregrounds) or white (for dark
   * foregrounds) by the smallest amount that makes [foreground] reach [minRatio]. The blend runs in
   * Oklab (Compose's color lerp), which preserves the perceived hue far better than RGB scaling.
   */
  fun ensureContrast(
    background: Color,
    foreground: Color = Color.White,
    minRatio: Float = AA,
  ): Color {
    if (background == Color.Unspecified || foreground == Color.Unspecified) return background
    val opaque = background.copy(alpha = 1f)
    if (contrastRatio(opaque, foreground) >= minRatio) return background

    val target = if (foreground.luminance() > 0.5f) Color.Black else Color.White
    var low = 0f
    var high = 1f
    repeat(14) {
      val mid = (low + high) * 0.5f
      if (contrastRatio(lerp(opaque, target, mid), foreground) >= minRatio) high = mid else low = mid
    }
    return lerp(opaque, target, high).copy(alpha = background.alpha)
  }

  /** Picks whichever of [light] / [dark] reads better on [background]. */
  fun bestContentColor(
    background: Color,
    light: Color = Color.White,
    dark: Color = Color(0xFF101010),
  ): Color = if (contrastRatio(background, light) >= contrastRatio(background, dark)) light else dark

  /**
   * Makes a whole artwork gradient legible for white content: the first stop (behind large
   * headers) needs [AA_LARGE], every following stop (behind titles, labels and controls) needs
   * [AA].
   */
  fun legibleGradientForWhite(colors: List<Color>): List<Color> =
    colors.mapIndexed { index, color ->
      ensureContrast(color, Color.White, if (index == 0) AA_LARGE else AA)
    }
}

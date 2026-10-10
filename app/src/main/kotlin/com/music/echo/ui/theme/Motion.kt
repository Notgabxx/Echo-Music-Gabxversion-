package echo.music.iad1tya.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.using

/**
 * Material "fade through" with a subtle depth cue: the outgoing content fades out quickly while
 * growing slightly, the incoming content fades in from 92% scale on the emphasized-decelerate
 * curve. Used for switching player modes (artwork ↔ lyrics) where both states share the space.
 */
fun <S> AnimatedContentTransitionScope<S>.fadeThroughTransform(): ContentTransform =
  (fadeIn(animationSpec = tween(300, 90, EchoMotion.EmphasizedDecelerate)) +
      scaleIn(
        animationSpec = tween(420, 90, EchoMotion.EmphasizedDecelerate),
        initialScale = 0.92f,
      ))
    .togetherWith(
      fadeOut(animationSpec = tween(150, 0, EchoMotion.EmphasizedAccelerate)) +
        scaleOut(
          animationSpec = tween(150, 0, EchoMotion.EmphasizedAccelerate),
          targetScale = 1.04f,
        )
    )
    .using(SizeTransform(clip = false))

/**
 * Shared motion tokens for Echo Music.
 *
 * Easing curves follow the Material 3 "emphasized" set; springs are tuned for direct-manipulation
 * surfaces (player sheet, artwork morph) so a released gesture keeps its momentum and settles
 * with a barely perceptible overshoot instead of a linear stop.
 */
object EchoMotion {
  /** Material 3 emphasized easing — the default for large, expressive transitions. */
  val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

  /** Emphasized decelerate — for elements entering the screen. */
  val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

  /** Emphasized accelerate — for elements leaving the screen. */
  val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

  /** Damping used when a sheet settles after a fling (≈0.5% overshoot of the travel). */
  const val SheetDampingRatio = 0.86f

  /** Stiffness used when a sheet settles after a fling. */
  const val SheetStiffness = 420f

  /** Damping for small tactile feedback (press, like, toggle). */
  const val TactileDampingRatio = 0.55f

  /** Stiffness for small tactile feedback (press, like, toggle). */
  const val TactileStiffness = 600f

  fun <T> tactileSpring(): FiniteAnimationSpec<T> =
    spring(dampingRatio = TactileDampingRatio, stiffness = TactileStiffness)

  fun <T> enterTween(durationMillis: Int = 420, delayMillis: Int = 0): FiniteAnimationSpec<T> =
    tween(durationMillis, delayMillis, EmphasizedDecelerate)

  fun <T> exitTween(durationMillis: Int = 180): FiniteAnimationSpec<T> =
    tween(durationMillis, 0, EmphasizedAccelerate)
}

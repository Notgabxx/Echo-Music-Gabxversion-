package echo.music.iad1tya.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import echo.music.iad1tya.constants.NavigationBarAnimationSpec
import echo.music.iad1tya.ui.theme.EchoMotion
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Draggable sheet used by the player (and the queue inside it).
 *
 * @param fluidMorph when true the sheet "morphs" instead of cross-fading: the expanded content
 *   grows out of the collapsed bar (scale from the bottom edge) while the collapsed bar lifts and
 *   dissolves, so the transition reads as one continuous surface.
 * @param overlay drawn above both the collapsed and the expanded content, inside the moving sheet.
 *   Used for shared-element style effects (e.g. the artwork flying from the mini player into the
 *   full player). It must not consume touches.
 */
@Composable
fun BottomSheet(
  state: BottomSheetState,
  modifier: Modifier = Modifier,
  background: @Composable (BoxScope.() -> Unit) = {},
  onDismiss: (() -> Unit)? = null,
  collapsedContent: @Composable BoxScope.() -> Unit,
  isExpandable: Boolean = true,
  fluidMorph: Boolean = false,
  overlay: @Composable BoxScope.() -> Unit = {},
  content: @Composable BoxScope.() -> Unit,
) {
  Box(
    modifier =
      modifier
        .graphicsLayer {
          alpha = (1.4f * (state.progress.coerceAtLeast(0.1f) - 0.1f).pow(0.5f)).coerceIn(0f, 1f)
        }
        .fillMaxSize(),
    content = background
  )
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .graphicsLayer {
          val y = (state.expandedBound - state.value).toPx().coerceAtLeast(0f)
          translationY = y
        }
        .pointerInput(state, isExpandable) {
          if (!isExpandable) return@pointerInput
          val velocityTracker = VelocityTracker()

          detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
              velocityTracker.addPointerInputChange(change)
              state.dispatchRawDelta(dragAmount)
            },
            onDragCancel = {
              velocityTracker.resetTracking()
              state.collapseSoft()
            },
            onDragEnd = {
              val velocity = -velocityTracker.calculateVelocity().y
              velocityTracker.resetTracking()
              state.performFling(velocity, onDismiss)
            }
          )
        }
        .graphicsLayer {
          // Continuous corner morph instead of snapping from 16dp to 0dp at the very top.
          val progress = state.progress.coerceIn(0f, 1f)
          val cornerRadius = 16.dp.toPx() * (1f - progress)
          shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
          clip = true
        }
  ) {
    if (!state.isCollapsed && !state.isDismissed) {
      BackHandler(onBack = state::collapseSoft)
    }

    if (!state.isCollapsed) {
      BoxWithConstraints(
        modifier =
          Modifier.fillMaxSize().graphicsLayer {
            val progress = state.progress
            alpha = ((progress - 0.15f) * 4).coerceIn(0f, 1f)
            if (fluidMorph) {
              // Grow out of the bar at the bottom: 94% → 100% on an ease-out curve.
              val t = progress.coerceIn(0f, 1f)
              val eased = 1f - (1f - t) * (1f - t) * (1f - t)
              val scale = 0.94f + 0.06f * eased
              scaleX = scale
              scaleY = scale
              transformOrigin = TransformOrigin(0.5f, 1f)
            }
          },
        content = content
      )
    }

    if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
      Box(
        modifier =
          Modifier.graphicsLayer {
              val fade = (state.progress * 4).coerceIn(0f, 1f)
              alpha = 1f - fade
              if (fluidMorph) {
                // The bar lifts and swells slightly while it dissolves into the full player.
                translationY = -18.dp.toPx() * fade
                val scale = 1f + 0.04f * fade
                scaleX = scale
                scaleY = scale
              }
            }
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = { if (isExpandable) state.expandSoft() },
            )
            .fillMaxWidth()
            .height(state.collapsedBound),
        content = collapsedContent,
      )
    }

    overlay()
  }
}

@Stable
class BottomSheetState(
  draggableState: DraggableState,
  private val coroutineScope: CoroutineScope,
  private val animatable: Animatable<Dp, AnimationVector1D>,
  private val onAnchorChanged: (Int) -> Unit,
  val collapsedBound: Dp,
  private val density: Float = 0f,
) : DraggableState by draggableState {
  val dismissedBound: Dp
    get() = animatable.lowerBound!!

  val expandedBound: Dp
    get() = animatable.upperBound!!

  val value by animatable.asState()

  val isDismissed by derivedStateOf { value == animatable.lowerBound!! }

  val isCollapsed by derivedStateOf { value == collapsedBound }

  val isExpanded by derivedStateOf { value == animatable.upperBound }

  val progress by derivedStateOf {
    1f - (animatable.upperBound!! - animatable.value) / (animatable.upperBound!! - collapsedBound)
  }

  /**
   * Spring used after a fling: keeps the finger's momentum (initial velocity) and lands with a
   * barely visible overshoot, which is what makes the sheet feel physical rather than scripted.
   */
  private val settleSpec: AnimationSpec<Dp> =
    spring(dampingRatio = EchoMotion.SheetDampingRatio, stiffness = EchoMotion.SheetStiffness)

  fun collapse(animationSpec: AnimationSpec<Dp>) {
    onAnchorChanged(collapsedAnchor)
    coroutineScope.launch { animatable.animateTo(collapsedBound, animationSpec) }
  }

  fun collapse(animationSpec: AnimationSpec<Dp>, initialVelocity: Dp) {
    onAnchorChanged(collapsedAnchor)
    coroutineScope.launch { animatable.animateTo(collapsedBound, animationSpec, initialVelocity) }
  }

  fun expand(animationSpec: AnimationSpec<Dp>) {
    onAnchorChanged(expandedAnchor)
    coroutineScope.launch { animatable.animateTo(animatable.upperBound!!, animationSpec) }
  }

  fun expand(animationSpec: AnimationSpec<Dp>, initialVelocity: Dp) {
    onAnchorChanged(expandedAnchor)
    coroutineScope.launch {
      animatable.animateTo(animatable.upperBound!!, animationSpec, initialVelocity)
    }
  }

  private fun collapse() {
    collapse(SpringSpec())
  }

  private fun expand() {
    expand(SpringSpec())
  }

  fun collapseSoft() {
    collapse(spring(stiffness = Spring.StiffnessMediumLow))
  }

  fun expandSoft() {
    expand(spring(stiffness = Spring.StiffnessMediumLow))
  }

  fun dismiss() {
    onAnchorChanged(dismissedAnchor)
    coroutineScope.launch { animatable.animateTo(animatable.lowerBound!!) }
  }

  suspend fun dismissAndWait() {
    onAnchorChanged(dismissedAnchor)
    animatable.animateTo(animatable.lowerBound!!)
  }

  fun snapTo(value: Dp) {
    coroutineScope.launch { animatable.snapTo(value) }
  }

  fun performFling(velocity: Float, onDismiss: (() -> Unit)?) {
    // velocity is in px/s, positive when moving towards the expanded state.
    if (density <= 0f) {
      performFlingWithoutMomentum(velocity, onDismiss)
      return
    }
    val momentum = (velocity / density).dp

    if (velocity > 250) {
      expand(settleSpec, momentum)
    } else if (velocity < -250) {
      if (value < collapsedBound && onDismiss != null) {
        dismiss()
        onDismiss.invoke()
      } else {
        collapse(settleSpec, momentum)
      }
    } else {
      val l0 = dismissedBound
      val l1 = (collapsedBound - dismissedBound) / 2
      val l2 = (expandedBound - collapsedBound) / 2
      val l3 = expandedBound

      when (value) {
        in l0..l1 -> {
          if (onDismiss != null) {
            dismiss()
            onDismiss.invoke()
          } else {
            collapse(settleSpec, momentum)
          }
        }
        in l1..l2 -> collapse(settleSpec, momentum)
        in l2..l3 -> expand(settleSpec, momentum)
        else -> Unit
      }
    }
  }

  private fun performFlingWithoutMomentum(velocity: Float, onDismiss: (() -> Unit)?) {
    if (velocity > 250) {
      expand()
    } else if (velocity < -250) {
      if (value < collapsedBound && onDismiss != null) {
        dismiss()
        onDismiss.invoke()
      } else {
        collapse()
      }
    } else {
      val l0 = dismissedBound
      val l1 = (collapsedBound - dismissedBound) / 2
      val l2 = (expandedBound - collapsedBound) / 2
      val l3 = expandedBound

      when (value) {
        in l0..l1 -> {
          if (onDismiss != null) {
            dismiss()
            onDismiss.invoke()
          } else {
            collapse()
          }
        }
        in l1..l2 -> collapse()
        in l2..l3 -> expand()
        else -> Unit
      }
    }
  }

  val preUpPostDownNestedScrollConnection
    get() =
      object : NestedScrollConnection {
        var isTopReached = false

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          if (isExpanded && available.y < 0) {
            isTopReached = false
          }

          return if (isTopReached && available.y < 0 && source == NestedScrollSource.UserInput) {
            dispatchRawDelta(available.y)
            available
          } else {
            Offset.Zero
          }
        }

        override fun onPostScroll(
          consumed: Offset,
          available: Offset,
          source: NestedScrollSource,
        ): Offset {
          if (!isTopReached) {
            isTopReached = consumed.y == 0f && available.y > 0
          }

          return if (isTopReached && source == NestedScrollSource.UserInput) {
            dispatchRawDelta(available.y)
            available
          } else {
            Offset.Zero
          }
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
          return if (isTopReached) {
            val velocity = -available.y
            performFling(velocity, null)

            available
          } else {
            Velocity.Zero
          }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
          isTopReached = false
          return Velocity.Zero
        }
      }
}

const val expandedAnchor = 2
const val collapsedAnchor = 1
const val dismissedAnchor = 0

@Composable
fun rememberBottomSheetState(
  dismissedBound: Dp,
  expandedBound: Dp,
  collapsedBound: Dp = dismissedBound,
  initialAnchor: Int = dismissedAnchor,
): BottomSheetState {
  val density = LocalDensity.current
  val coroutineScope = rememberCoroutineScope()

  var previousAnchor by rememberSaveable { mutableIntStateOf(initialAnchor) }
  val animatable = remember { Animatable(0.dp, Dp.VectorConverter) }

  return remember(dismissedBound, expandedBound, collapsedBound, coroutineScope) {
    val initialValue =
      when (previousAnchor) {
        expandedAnchor -> expandedBound
        collapsedAnchor -> collapsedBound
        dismissedAnchor -> dismissedBound
        else -> error("Unknown BottomSheet anchor")
      }

    animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
    coroutineScope.launch { animatable.animateTo(initialValue, NavigationBarAnimationSpec) }

    BottomSheetState(
      draggableState =
        DraggableState { delta ->
          coroutineScope.launch {
            animatable.snapTo(animatable.value - with(density) { delta.toDp() })
          }
        },
      onAnchorChanged = { previousAnchor = it },
      coroutineScope = coroutineScope,
      animatable = animatable,
      collapsedBound = collapsedBound,
      density = density.density,
    )
  }
}

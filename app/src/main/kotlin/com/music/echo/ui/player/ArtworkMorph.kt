package echo.music.iad1tya.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlin.math.min

/**
 * Shared-element style morph between the mini player artwork and the full player artwork.
 *
 * Both artworks report their live [LayoutCoordinates]; while the player sheet is between collapsed
 * and expanded, an overlay inside the sheet draws the artwork at a rectangle interpolated from the
 * mini rect to the full rect (position, size and corner radius) and the two originals are hidden.
 * Everything is computed in the graphics-layer (draw) phase from the sheet progress, so dragging
 * the player never recomposes — the artwork is moved, scaled and clipped on the GPU only.
 *
 * Robustness rules:
 * - the morph only starts if everything is ready near the beginning of a gesture (first/last 12%
 *   of travel); otherwise that gesture falls back to the regular cross-fade, so the artwork never
 *   "pops" in mid-way,
 * - the overlay only shows once its image is loaded (it shares the memory-cache entry with the
 *   full player artwork, which is prefetched on track change),
 * - at exactly collapsed / expanded the originals are shown again, so the resting state is always
 *   the real, interactive UI.
 */
@Stable
class ArtworkMorphState(private val progressProvider: () -> Float) {
  internal var miniCoordinates: LayoutCoordinates? = null
  internal var fullCoordinates: LayoutCoordinates? = null
  internal var overlayCoordinates: LayoutCoordinates? = null

  internal var miniCornerFraction: Float = 0.5f
  internal var fullCornerRadiusPx: Float = 0f

  internal var miniAvailable by mutableStateOf(false)
  internal var fullAvailable by mutableStateOf(false)
  internal var fullSize by mutableStateOf(IntSize.Zero)
  internal var requestedImage by mutableStateOf<String?>(null)
  internal var loadedImage by mutableStateOf<String?>(null)

  /** 0 = idle, 1 = morphing during this gesture, -1 = skipped for this gesture. */
  private var session = 0

  private val isReady: Boolean
    get() =
      miniAvailable &&
        fullAvailable &&
        fullSize != IntSize.Zero &&
        requestedImage != null &&
        loadedImage == requestedImage &&
        miniCoordinates?.isAttached == true &&
        fullCoordinates?.isAttached == true &&
        overlayCoordinates?.isAttached == true

  internal val progress: Float
    get() = progressProvider()

  /** True while the overlay stands in for both originals. Safe to call from draw lambdas. */
  fun isMorphing(): Boolean {
    val p = progressProvider()
    if (p <= 0f || p >= 1f) {
      session = 0
      return false
    }
    val ready = isReady
    return when (session) {
      1 -> {
        if (!ready) session = -1
        ready
      }
      -1 -> false
      else -> {
        if (ready) {
          session = 1
          true
        } else {
          if (p > 0.12f && p < 0.88f) session = -1
          false
        }
      }
    }
  }
}

/** Provided by the player sheet; null when the fluid transition is disabled. */
val LocalArtworkMorphState = staticCompositionLocalOf<ArtworkMorphState?> { null }

/**
 * Marks the mini player artwork as the morph's collapsed end.
 *
 * @param cornerFraction corner radius as a fraction of the artwork's smaller side (0.5 = circle).
 */
@Composable
fun Modifier.artworkMorphMini(cornerFraction: Float): Modifier {
  val morph = LocalArtworkMorphState.current ?: return this
  DisposableEffect(morph) {
    onDispose {
      morph.miniCoordinates = null
      morph.miniAvailable = false
    }
  }
  return this.onGloballyPositioned { coordinates ->
      morph.miniCoordinates = coordinates
      morph.miniCornerFraction = cornerFraction
      morph.miniAvailable = coordinates.isAttached && coordinates.size.width > 0
    }
    .graphicsLayer { alpha = if (morph.isMorphing()) 0f else 1f }
}

/** Marks the full player artwork (current track) as the morph's expanded end. */
@Composable
fun Modifier.artworkMorphFull(cornerRadius: Dp): Modifier {
  val morph = LocalArtworkMorphState.current ?: return this
  val cornerRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
  DisposableEffect(morph) {
    onDispose {
      morph.fullCoordinates = null
      morph.fullAvailable = false
    }
  }
  return this.onGloballyPositioned { coordinates ->
      morph.fullCoordinates = coordinates
      morph.fullCornerRadiusPx = cornerRadiusPx
      morph.fullSize = coordinates.size
      morph.fullAvailable = coordinates.isAttached && coordinates.size.width > 0
    }
    .graphicsLayer { alpha = if (morph.isMorphing()) 0f else 1f }
}

/**
 * Draws the travelling artwork. Must be placed inside the moving sheet, above its content
 * (see `BottomSheet(overlay = …)`). Never consumes touches.
 */
@Composable
fun ArtworkMorphOverlay(
  morph: ArtworkMorphState,
  imageUrl: String?,
  contentScale: ContentScale,
) {
  SideEffect { morph.requestedImage = imageUrl }

  val fullSize = morph.fullSize
  Box(modifier = Modifier.fillMaxSize().onPlaced { morph.overlayCoordinates = it }) {
    if (imageUrl != null && fullSize != IntSize.Zero) {
      val context = LocalContext.current
      val density = LocalDensity.current
      val request =
        remember(imageUrl) {
          ImageRequest.Builder(context)
            .data(imageUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
        }
      AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = contentScale,
        onSuccess = { morph.loadedImage = imageUrl },
        modifier =
          Modifier.size(
              width = with(density) { fullSize.width.toDp() },
              height = with(density) { fullSize.height.toDp() },
            )
            .graphicsLayer { applyArtworkMorph(morph) }
      )
    }
  }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
  start + (stop - start) * fraction

private fun GraphicsLayerScope.applyArtworkMorph(morph: ArtworkMorphState) {
  if (!morph.isMorphing()) {
    alpha = 0f
    return
  }
  val overlay = morph.overlayCoordinates
  val mini = morph.miniCoordinates
  val full = morph.fullCoordinates
  if (overlay == null || mini == null || full == null) {
    alpha = 0f
    return
  }

  val rects: Pair<Rect, Rect>? =
    try {
      overlay.localBoundingBoxOf(mini, false) to overlay.localBoundingBoxOf(full, false)
    } catch (_: Exception) {
      null
    }
  if (rects == null) {
    alpha = 0f
    return
  }
  val miniRect = rects.first
  val fullRect = rects.second

  val baseWidth = size.width
  val baseHeight = size.height
  if (baseWidth <= 0f || baseHeight <= 0f || miniRect.width <= 0f || fullRect.width <= 0f) {
    alpha = 0f
    return
  }

  val t = morph.progress.coerceIn(0f, 1f)
  val width = lerpFloat(miniRect.width, fullRect.width, t)
  val height = lerpFloat(miniRect.height, fullRect.height, t)

  transformOrigin = TransformOrigin(0f, 0f)
  translationX = lerpFloat(miniRect.left, fullRect.left, t)
  translationY = lerpFloat(miniRect.top, fullRect.top, t)
  scaleX = width / baseWidth
  scaleY = height / baseHeight

  val miniRadius = min(miniRect.width, miniRect.height) * morph.miniCornerFraction
  val radius = lerpFloat(miniRadius, morph.fullCornerRadiusPx, t)
  val averageScale = ((scaleX + scaleY) * 0.5f).coerceAtLeast(0.0001f)
  shape = RoundedCornerShape(radius / averageScale)
  clip = true
  alpha = 1f
}

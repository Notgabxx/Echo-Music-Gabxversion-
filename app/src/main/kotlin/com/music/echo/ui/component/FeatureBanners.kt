package echo.music.iad1tya.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import echo.music.iad1tya.LocalListenTogetherManager
import echo.music.iad1tya.R
import echo.music.iad1tya.listentogether.RoomRole
import echo.music.iad1tya.listentogether.RoomState
import echo.music.iad1tya.ui.component.backdrop.backdrops.layerBackdrop
import echo.music.iad1tya.ui.component.backdrop.backdrops.rememberLayerBackdrop
import echo.music.iad1tya.ui.theme.AdaptiveContrast

/** Route of the Listen Together screen that has its own top bar (reachable from anywhere). */
private const val LISTEN_TOGETHER_ROUTE = "listen_together_from_topbar"

private enum class FeatureBannerKind {
  LISTEN_TOGETHER,
  RECOGNITION,
  SMART_MIX,
}

/**
 * Hero-style feature banners for the home screen, matching the look of the "quick picks" hero
 * carousel (extra-large rounded cards, outline, peeking neighbours). Listen Together is always the
 * first banner — while a room is active it turns into a live status card — so the feature is one
 * tap away. Action buttons are liquid glass sampling each banner's own artwork.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureBanners(
  navController: NavController,
  modifier: Modifier = Modifier,
) {
  val banners = remember {
    listOf(
      FeatureBannerKind.LISTEN_TOGETHER,
      FeatureBannerKind.RECOGNITION,
      FeatureBannerKind.SMART_MIX,
    )
  }

  val listenTogetherManager = LocalListenTogetherManager.current
  val role by
    listenTogetherManager?.role?.collectAsState() ?: remember { mutableStateOf(RoomRole.NONE) }
  val roomState by
    listenTogetherManager?.roomState?.collectAsState()
      ?: remember { mutableStateOf<RoomState?>(null) }

  val configuration = LocalConfiguration.current
  val heroWidth = configuration.screenWidthDp.dp - 32.dp
  val carouselState = rememberCarouselState { banners.size }

  HorizontalCenteredHeroCarousel(
    state = carouselState,
    maxItemWidth = heroWidth,
    itemSpacing = 8.dp,
    contentPadding = PaddingValues(horizontal = 16.dp),
    modifier = modifier.fillMaxWidth().height(BannerHeight)
  ) { index ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .maskClip(MaterialTheme.shapes.extraLarge)
          .maskBorder(
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            MaterialTheme.shapes.extraLarge
          )
    ) {
      when (banners[index]) {
        FeatureBannerKind.LISTEN_TOGETHER -> {
          val activeRoom = roomState
          if (role != RoomRole.NONE && activeRoom != null) {
            FeatureBanner(
              palette = bannerPalette(MaterialTheme.colorScheme.primary, Color(0xFF00B4FF)),
              label = stringResource(R.string.banner_listen_active_label),
              title = stringResource(R.string.banner_listen_active_title),
              subtitle =
                stringResource(
                  R.string.banner_listen_active_subtitle,
                  activeRoom.roomCode,
                  activeRoom.users.count { it.isConnected }
                ),
              badge = { TogetherBadge(live = true) },
              primaryAction =
                BannerAction(stringResource(R.string.banner_listen_open), R.drawable.group_filled) {
                  navController.navigate(LISTEN_TOGETHER_ROUTE)
                },
              onClick = { navController.navigate(LISTEN_TOGETHER_ROUTE) },
            )
          } else {
            FeatureBanner(
              palette = bannerPalette(Color(0xFF6A3DFF), MaterialTheme.colorScheme.primary),
              label = stringResource(R.string.banner_listen_label),
              title = stringResource(R.string.banner_listen_title),
              subtitle = stringResource(R.string.banner_listen_subtitle),
              badge = { TogetherBadge(live = false) },
              primaryAction =
                BannerAction(stringResource(R.string.banner_listen_create), R.drawable.group_filled) {
                  navController.navigate(LISTEN_TOGETHER_ROUTE)
                },
              secondaryAction =
                BannerAction(stringResource(R.string.banner_listen_join), null) {
                  navController.navigate(LISTEN_TOGETHER_ROUTE)
                },
              onClick = { navController.navigate(LISTEN_TOGETHER_ROUTE) },
            )
          }
        }
        FeatureBannerKind.RECOGNITION ->
          FeatureBanner(
            palette =
              bannerPalette(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary),
            label = stringResource(R.string.banner_find_label),
            title = stringResource(R.string.banner_find_title),
            subtitle = stringResource(R.string.banner_find_subtitle),
            badge = { IconBadge(R.drawable.mic) },
            primaryAction =
              BannerAction(stringResource(R.string.banner_find_action), R.drawable.mic) {
                navController.navigate("recognition")
              },
            onClick = { navController.navigate("recognition") },
          )
        FeatureBannerKind.SMART_MIX ->
          FeatureBanner(
            palette =
              bannerPalette(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary),
            label = stringResource(R.string.banner_mix_label),
            title = stringResource(R.string.banner_mix_title),
            subtitle = stringResource(R.string.banner_mix_subtitle),
            badge = { IconBadge(R.drawable.sparks) },
            primaryAction =
              BannerAction(stringResource(R.string.banner_mix_action), R.drawable.sparks) {
                navController.navigate("generate")
              },
            onClick = { navController.navigate("generate") },
          )
      }
    }
  }
}

private class BannerAction(
  val text: String,
  @DrawableRes val icon: Int?,
  val onClick: () -> Unit,
)

/** Two banner colors, adjusted so white text always meets WCAG AA on top of them. */
private class BannerPalette(val start: Color, val end: Color, val glow: Color)

private fun bannerPalette(first: Color, second: Color): BannerPalette =
  BannerPalette(
    start = AdaptiveContrast.ensureContrast(first, Color.White, AdaptiveContrast.AA),
    end = AdaptiveContrast.ensureContrast(second, Color.White, AdaptiveContrast.AA),
    glow = lerp(first, Color.White, 0.25f),
  )

@Composable
private fun FeatureBanner(
  palette: BannerPalette,
  label: String,
  title: String,
  subtitle: String,
  badge: @Composable () -> Unit,
  primaryAction: BannerAction,
  onClick: () -> Unit,
  secondaryAction: BannerAction? = null,
) {
  // The banner artwork is recorded into its own backdrop so the glass buttons refract it.
  val bannerBackdrop = rememberLayerBackdrop()

  Box(modifier = Modifier.fillMaxSize().clickable(onClick = onClick)) {
    Box(
      modifier =
        Modifier.fillMaxSize().layerBackdrop(bannerBackdrop).drawBehind {
          drawRect(
            brush =
              Brush.linearGradient(
                colors = listOf(palette.start, palette.end),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
              )
          )
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(palette.glow.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.18f),
                radius = size.height * 0.95f,
              ),
            radius = size.height * 0.95f,
            center = Offset(size.width * 0.82f, size.height * 0.18f),
          )
          // Soft "sound waves" radiating from the badge in the top-end corner.
          val waveCenter =
            Offset(
              size.width - BannerPadding.toPx() - BadgeSize.toPx() / 2f,
              BannerPadding.toPx() + BadgeSize.toPx() / 2f,
            )
          drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = 30.dp.toPx(),
            center = waveCenter,
          )
          WaveRings.forEach { (radius, alpha) ->
            drawCircle(
              color = Color.White.copy(alpha = alpha),
              radius = radius.toPx(),
              center = waveCenter,
              style = Stroke(width = 1.5.dp.toPx()),
            )
          }
          // Soft shade behind the text block for extra legibility.
          drawRect(
            brush =
              Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                startY = size.height * 0.35f,
                endY = size.height,
              )
          )
        }
    )

    Column(modifier = Modifier.fillMaxSize().padding(BannerPadding)) {
      // Header: eyebrow label + badge. The badge has its own slot in the layout, so it can never
      // run into the title, whatever the text length, language or font scale.
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(BadgeSize),
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 1.2.sp,
          color = Color.White.copy(alpha = 0.86f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        badge()
      }

      Spacer(Modifier.height(6.dp))

      // Title and subtitle share the space left above the buttons; if it runs short (large font
      // scale) the subtitle gives up lines first, with an ellipsis.
      Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().weight(1f),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.86f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth(0.92f),
        )
      }

      Spacer(Modifier.height(10.dp))
      CompositionLocalProvider(LocalAppBackdrop provides bannerBackdrop) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          GlassPillButton(action = primaryAction, emphasized = true)
          if (secondaryAction != null) {
            GlassPillButton(action = secondaryAction, emphasized = false)
          }
        }
      }
    }
  }
}

@Composable
private fun GlassPillButton(action: BannerAction, emphasized: Boolean) {
  val shape = RoundedCornerShape(percent = 50)
  val glassConfig =
    LocalGlassEffectConfig.current.copy(
      surfaceTintColor = Color.White,
      surfaceOpacity = if (emphasized) 0.26f else 0.12f,
      blurRadius = 10f,
    )
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.height(40.dp)
        .then(
          if (isGlassSupported()) {
            Modifier.liquidGlass(config = glassConfig, shape = shape)
          } else {
            Modifier.clip(shape)
              .background(Color.White.copy(alpha = if (emphasized) 0.28f else 0.16f))
          }
        )
        .clip(shape)
        .clickable(onClick = action.onClick)
        .padding(horizontal = 16.dp),
  ) {
    if (action.icon != null) {
      Icon(
        painter = painterResource(action.icon),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(6.dp))
    }
    Text(
      text = action.text,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = Color.White,
      maxLines = 1,
    )
  }
}

/** Three side-by-side "friends" bubbles — reads as people listening together. */
@Composable
private fun TogetherBadge(live: Boolean) {
  val icons = listOf(R.drawable.group_filled, R.drawable.music_note, R.drawable.favorite)
  Box {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      icons.forEach { icon -> BadgeBubble(icon = icon, size = BadgeSize - 2.dp) }
    }
    if (live) {
      Box(
        modifier =
          Modifier.align(Alignment.TopEnd)
            .size(11.dp)
            .clip(CircleShape)
            .background(Color(0xFF34E37A))
            .border(2.dp, Color.White, CircleShape)
      )
    }
  }
}

/** A single icon bubble; the banner draws sound-wave rings radiating from it. */
@Composable
private fun IconBadge(@DrawableRes icon: Int) {
  BadgeBubble(icon = icon, size = BadgeSize)
}

@Composable
private fun BadgeBubble(@DrawableRes icon: Int, size: Dp) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier.size(size)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.20f))
        .border(1.dp, Color.White.copy(alpha = 0.50f), CircleShape),
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier.size(size * 0.5f),
    )
  }
}

private val BannerHeight = 224.dp
private val BannerPadding = 16.dp
private val BadgeSize = 36.dp

/** Radius and opacity of each sound-wave ring drawn behind the badge. */
private val WaveRings = listOf(50.dp to 0.16f, 74.dp to 0.10f, 100.dp to 0.06f)

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package echo.music.iad1tya.ui.screens.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import echo.music.iad1tya.BuildConfig
import echo.music.iad1tya.FluxBrand
import echo.music.iad1tya.LocalPlayerAwareWindowInsets
import echo.music.iad1tya.R
import echo.music.iad1tya.ui.component.IconButton
import echo.music.iad1tya.ui.component.Material3SettingsGroup
import echo.music.iad1tya.ui.component.Material3SettingsItem
import echo.music.iad1tya.ui.utils.backToMain

/**
 * About Flux. Keeps the upstream credit and license notice required by the GPL-3.0 ("Appropriate
 * Legal Notices") — Flux is a fork of Echo Music.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
  navController: NavController,
  scrollBehavior: TopAppBarScrollBehavior,
  onBack: (() -> Unit)? = null,
  highlightKey: String? = null
) {
  val uriHandler = LocalUriHandler.current

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.surface,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.about),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = { onBack?.invoke() ?: navController.navigateUp() },
            onLongClick = navController::backToMain,
          ) {
            Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
          }
        },
        windowInsets = TopAppBarDefaults.windowInsets,
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          ),
        scrollBehavior = scrollBehavior,
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(
              WindowInsetsSides.Horizontal,
            ),
          ),
      contentPadding =
        PaddingValues(
          start = 16.dp,
          top = innerPadding.calculateTopPadding() + 8.dp,
          end = 16.dp,
          bottom =
            androidx.compose.foundation.layout.WindowInsets.systemBars
              .asPaddingValues()
              .calculateBottomPadding() + 32.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item { AboutAppCard() }

      item {
        Material3SettingsGroup(
          title = stringResource(R.string.about_project),
          items =
            listOf(
              Material3SettingsItem(
                icon = painterResource(R.drawable.github),
                title = { Text(stringResource(R.string.about_source_code)) },
                description = { Text(FluxBrand.REPO_SLUG) },
                onClick = { uriHandler.openUri(FluxBrand.REPO_URL) }
              ),
              Material3SettingsItem(
                icon = painterResource(R.drawable.update),
                title = { Text(stringResource(R.string.about_releases)) },
                description = { Text(stringResource(R.string.about_releases_desc)) },
                onClick = { uriHandler.openUri(FluxBrand.RELEASES_URL) }
              ),
            )
        )
      }

      item {
        Material3SettingsGroup(
          title = stringResource(R.string.about_credits),
          items =
            listOf(
              Material3SettingsItem(
                icon = painterResource(R.drawable.info),
                title = {
                  Text(stringResource(R.string.about_based_on, FluxBrand.UPSTREAM_NAME))
                },
                description = { Text(stringResource(R.string.about_based_on_desc)) },
                onClick = { uriHandler.openUri(FluxBrand.UPSTREAM_URL) }
              ),
              Material3SettingsItem(
                icon = painterResource(R.drawable.link),
                title = { Text(stringResource(R.string.about_license)) },
                description = {
                  Text(stringResource(R.string.about_license_desc, FluxBrand.LICENSE_NAME))
                },
                onClick = { uriHandler.openUri(FluxBrand.LICENSE_URL) }
              ),
            )
        )
      }
    }
  }
}

@Composable
private fun AboutAppCard() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by
      animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec =
          spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
          ),
        label = "logoScale"
      )

    Box(
      modifier =
        Modifier.size(96.dp)
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
          }
          .clip(CircleShape)
          .background(Brush.linearGradient(listOf(Color(0xFF6A3DFF), Color(0xFF00B4FF))))
          .clickable(interactionSource = interactionSource, indication = null, onClick = {}),
      contentAlignment = Alignment.Center
    ) {
      Image(
        painter = painterResource(R.drawable.flux_icon_foreground),
        contentDescription = null,
        modifier = Modifier.fillMaxSize()
      )
    }

    Spacer(Modifier.height(4.dp))

    Text(
      text = FluxBrand.APP_NAME,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
      ) {
        Text(
          text = BuildConfig.VERSION_NAME,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
      if (BuildConfig.DEBUG) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        ) {
          Text(
            text = "DEBUG",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }
    }
    Text(
      text = stringResource(R.string.about_tagline),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

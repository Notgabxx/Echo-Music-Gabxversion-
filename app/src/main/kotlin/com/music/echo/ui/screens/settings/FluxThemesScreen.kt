package echo.music.iad1tya.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import echo.music.iad1tya.R
import echo.music.iad1tya.ui.theme.FluxThemeSpec
import echo.music.iad1tya.ui.theme.FluxThemes
import echo.music.iad1tya.ui.theme.parseThemeColor
import echo.music.iad1tya.ui.theme.themeFontFamily
import echo.music.iad1tya.ui.theme.toShapes
import echo.music.iad1tya.ui.theme.withThemeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Appearance → Themes: apply a built-in theme, import a `.json` theme file, or go back
 * to the default look. Applying recreates the activity, which reloads the theme everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluxThemesScreen(navController: NavController) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var refreshKey by remember { mutableIntStateOf(0) }

  val entries by
    produceState(initialValue = emptyList<FluxThemes.Entry>(), refreshKey) {
      value = withContext(Dispatchers.IO) { FluxThemes.all(context) }
    }
  val activeId by
    produceState(initialValue = "", refreshKey) {
      value = withContext(Dispatchers.IO) { FluxThemes.activeId(context) }
    }

  fun applyTheme(id: String) {
    scope.launch {
      FluxThemes.setActive(context, id)
      Toast.makeText(context, R.string.themes_applying, Toast.LENGTH_SHORT).show()
      context.findActivity()?.recreate()
    }
  }

  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { FluxThemes.import(context, uri) } }
        result
          .onSuccess { spec ->
            Toast.makeText(
                context,
                context.getString(R.string.themes_imported, spec.name),
                Toast.LENGTH_SHORT,
              )
              .show()
            refreshKey++
          }
          .onFailure { error ->
            Toast.makeText(
                context,
                context.getString(R.string.themes_import_failed, error.message ?: ""),
                Toast.LENGTH_LONG,
              )
              .show()
          }
      }
    }

  fun launchImport() {
    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.themes_title), fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = { navController.navigateUp() }) {
            Icon(
              painterResource(R.drawable.arrow_back),
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
        actions = {
          IconButton(onClick = ::launchImport) {
            Icon(
              painterResource(R.drawable.add),
              contentDescription = stringResource(R.string.themes_import),
            )
          }
        },
      )
    }
  ) { padding ->
    LazyColumn(
      contentPadding =
        PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = padding.calculateTopPadding() + 8.dp,
          bottom = padding.calculateBottomPadding() + 32.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxSize(),
    ) {
      item {
        Text(
          text = stringResource(R.string.themes_intro),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      item(key = "flux:default") {
        ThemeCard(
          spec = null,
          active = activeId.isBlank(),
          builtIn = true,
          onApply = { applyTheme("") },
          onDelete = null,
        )
      }

      items(entries, key = { "theme:" + it.spec.id }) { entry ->
        ThemeCard(
          spec = entry.spec,
          active = entry.spec.id == activeId,
          builtIn = entry.builtIn,
          onApply = { applyTheme(entry.spec.id) },
          onDelete =
            if (entry.builtIn) {
              null
            } else {
              {
                scope.launch {
                  withContext(Dispatchers.IO) { FluxThemes.delete(context, entry.spec.id) }
                  if (entry.spec.id == activeId) {
                    applyTheme("")
                  } else {
                    refreshKey++
                  }
                }
              }
            },
        )
      }

      item {
        OutlinedButton(onClick = ::launchImport, modifier = Modifier.fillMaxWidth()) {
          Icon(painterResource(R.drawable.add), contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.themes_import))
        }
      }

      item {
        Text(
          text = stringResource(R.string.themes_help),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun ThemeCard(
  spec: FluxThemeSpec?,
  active: Boolean,
  builtIn: Boolean,
  onApply: () -> Unit,
  onDelete: (() -> Unit)?,
) {
  val scheme = rememberThemePreviewScheme(spec)
  val previewShapes = remember(spec) { spec?.shapes?.toShapes() }
  val name = spec?.name ?: stringResource(R.string.themes_default_name)
  val description = spec?.description ?: stringResource(R.string.themes_default_desc)

  Card(
    onClick = onApply,
    shape = RoundedCornerShape(20.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
      ),
    border =
      if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(14.dp),
    ) {
      ThemePreview(
        scheme = scheme,
        cardShape = previewShapes?.medium ?: RoundedCornerShape(10.dp),
        pillShape = previewShapes?.extraLarge ?: RoundedCornerShape(50),
      )
      Spacer(Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          fontFamily = themeFontFamily(spec?.typography?.font),
          fontStyle = if (spec?.typography?.italicTitles == true) FontStyle.Italic else null,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val author = spec?.author
        if (!author.isNullOrBlank()) {
          Text(
            text = stringResource(R.string.themes_by, author),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
        Spacer(Modifier.height(4.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (active) {
            Text(
              text = stringResource(R.string.themes_in_use),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
            )
          } else {
            Button(onClick = onApply) { Text(stringResource(R.string.themes_apply)) }
          }
          Spacer(Modifier.weight(1f))
          if (onDelete != null) {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.themes_delete)) }
          } else if (builtIn && spec != null) {
            Text(
              text = stringResource(R.string.themes_built_in),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

/** A tiny mock of the app in the theme's colors and shapes. */
@Composable
private fun ThemePreview(scheme: ColorScheme, cardShape: Shape, pillShape: Shape) {
  Box(
    modifier =
      Modifier.size(width = 92.dp, height = 112.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(scheme.background)
  ) {
    Box(
      Modifier.offset(x = 10.dp, y = 12.dp)
        .size(width = 40.dp, height = 7.dp)
        .clip(RoundedCornerShape(50))
        .background(scheme.onBackground.copy(alpha = 0.85f))
    )
    Box(
      Modifier.offset(x = 10.dp, y = 28.dp)
        .size(width = 72.dp, height = 44.dp)
        .clip(cardShape)
        .background(scheme.surfaceContainerHigh)
    )
    Box(
      Modifier.offset(x = 16.dp, y = 36.dp)
        .size(width = 30.dp, height = 6.dp)
        .clip(RoundedCornerShape(50))
        .background(scheme.onSurface.copy(alpha = 0.7f))
    )
    Box(
      Modifier.offset(x = 10.dp, y = 82.dp)
        .size(width = 40.dp, height = 16.dp)
        .clip(pillShape)
        .background(scheme.primary)
    )
    Box(
      Modifier.offset(x = 56.dp, y = 82.dp)
        .size(width = 26.dp, height = 16.dp)
        .clip(pillShape)
        .background(scheme.secondary)
    )
  }
}

/** The color scheme a theme produces (same recipe as the app theme), for the previews. */
@Composable
private fun rememberThemePreviewScheme(spec: FluxThemeSpec?): ColorScheme {
  val dark = spec?.forcedDarkMode ?: true
  val seed =
    spec?.let { parseThemeColor(it.colors.seed) ?: parseThemeColor(it.colors.primary) }
      ?: Color(0xFF6A3DFF)
  val base =
    rememberDynamicColorScheme(
      seedColor = seed,
      isDark = dark,
      specVersion = ColorSpec.SpecVersion.SPEC_2025,
      style = PaletteStyle.TonalSpot,
    )
  return remember(base, spec) { if (spec != null) base.withThemeColors(spec.colors) else base }
}

private fun Context.findActivity(): Activity? {
  var current: Context? = this
  while (current is ContextWrapper) {
    if (current is Activity) return current
    current = current.baseContext
  }
  return null
}

package echo.music.iad1tya.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score
import echo.music.iad1tya.constants.AppFont
import echo.music.iad1tya.constants.SelectedFontKey
import echo.music.iad1tya.utils.rememberPreference

val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun echomusicTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  pureBlack: Boolean = false,
  themeColor: Color = DefaultThemeColor,
  customTheme: FluxThemeSpec? = null,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val selectedFontValue by rememberPreference(SelectedFontKey, AppFont.SYSTEM.value)
  val customFontPathValue by rememberPreference(echo.music.iad1tya.constants.CustomFontPathKey, "")

  val brandFont =
    remember(selectedFontValue, customFontPathValue) {
      when (AppFont.fromValue(selectedFontValue)) {
        AppFont.SYSTEM -> FontFamily.Default
        AppFont.GOOGLE_SANS -> GoogleSansFontFamily
        AppFont.SANS_FLEX -> SansFlexFontFamily
        AppFont.OUTFIT -> OutfitFontFamily
        AppFont.PLUS_JAKARTA_SANS -> PlusJakartaSansFontFamily
        AppFont.CUSTOM -> {
          try {
            if (customFontPathValue.isNotEmpty() && java.io.File(customFontPathValue).exists()) {
              val typeface = android.graphics.Typeface.createFromFile(customFontPathValue)
              FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
            } else {
              FontFamily.Default
            }
          } catch (e: Exception) {
            FontFamily.Default
          }
        }
        else -> FontFamily.Default
      }
    }

  val useSystemDynamicColor =
    customTheme == null &&
      (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

  // A custom theme seeds Material You with its own color (or the album's, if it asks for it).
  val seedColor =
    if (customTheme != null && !customTheme.albumColors) {
      parseThemeColor(customTheme.colors.seed)
        ?: parseThemeColor(customTheme.colors.primary)
        ?: themeColor
    } else {
      themeColor
    }

  val baseColorScheme =
    if (useSystemDynamicColor) {

      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {

      rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = PaletteStyle.TonalSpot
      )
    }

  val colorScheme =
    remember(baseColorScheme, pureBlack, darkTheme, customTheme) {
      when {
        customTheme != null -> baseColorScheme.withThemeColors(customTheme.colors)
        darkTheme && pureBlack -> baseColorScheme.pureBlack(true)
        else -> baseColorScheme
      }
    }

  val typography =
    remember(brandFont, customTheme) {
      val themeTypography = customTheme?.typography
      val fontFamily = themeFontFamily(themeTypography?.font) ?: brandFont
      val base = getTypography(fontFamily)
      if (themeTypography != null) base.withThemeTitles(themeTypography) else base
    }

  val defaultShapes = androidx.compose.material3.MaterialTheme.shapes
  val shapes =
    remember(customTheme, defaultShapes) {
      customTheme?.shapes?.toShapes()
        ?: defaultShapes.copy(extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
    }

  CompositionLocalProvider(LocalFluxTheme provides customTheme) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = typography,
      shapes = shapes,
      content = content
    )
  }
}

fun Bitmap.extractThemeColor(): Color {
  val colorsToPopulation =
    Palette.from(this).maximumColorCount(8).generate().swatches.associate {
      it.rgb to it.population
    }
  val rankedColors = Score.score(colorsToPopulation)
  return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
  val extractedColors =
    Palette.from(this).maximumColorCount(64).generate().swatches.associate {
      it.rgb to it.population
    }

  val orderedColors =
    Score.score(extractedColors, 2, 0xff4285f4.toInt(), true).sortedByDescending {
      Color(it).luminance()
    }

  return if (orderedColors.size >= 2) listOf(Color(orderedColors[0]), Color(orderedColors[1]))
  else listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
  if (apply) copy(surface = Color.Black, background = Color.Black) else this

val ColorSaver =
  object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)

    override fun SaverScope.save(value: Color): Int = value.toArgb()
  }

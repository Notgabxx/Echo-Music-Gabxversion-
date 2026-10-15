package echo.music.iad1tya.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Flux theme file (`.json`). Everything except [id] and [name] is optional: missing colors are
 * generated from [Colors.seed] with Material You, so a theme can be as small as a seed color.
 * See THEMES.md in the repository for the full format.
 */
@Immutable
@Serializable
data class FluxThemeSpec(
  @SerialName("format") val format: String = FORMAT,
  @SerialName("version") val version: Int = 1,
  @SerialName("id") val id: String,
  @SerialName("name") val name: String,
  @SerialName("author") val author: String? = null,
  @SerialName("description") val description: String? = null,
  /** "dark", "light" or "system". */
  @SerialName("mode") val mode: String = "system",
  /** Keep tinting the app with the colors of the album that is playing. */
  @SerialName("albumColors") val albumColors: Boolean = false,
  @SerialName("colors") val colors: Colors = Colors(),
  @SerialName("shapes") val shapes: ShapesSpec? = null,
  @SerialName("typography") val typography: TypographySpec? = null,
  @SerialName("player") val player: PlayerSpec? = null,
) {
  @Immutable
  @Serializable
  data class Colors(
    @SerialName("seed") val seed: String? = null,
    @SerialName("primary") val primary: String? = null,
    @SerialName("onPrimary") val onPrimary: String? = null,
    @SerialName("primaryContainer") val primaryContainer: String? = null,
    @SerialName("onPrimaryContainer") val onPrimaryContainer: String? = null,
    @SerialName("inversePrimary") val inversePrimary: String? = null,
    @SerialName("secondary") val secondary: String? = null,
    @SerialName("onSecondary") val onSecondary: String? = null,
    @SerialName("secondaryContainer") val secondaryContainer: String? = null,
    @SerialName("onSecondaryContainer") val onSecondaryContainer: String? = null,
    @SerialName("tertiary") val tertiary: String? = null,
    @SerialName("onTertiary") val onTertiary: String? = null,
    @SerialName("tertiaryContainer") val tertiaryContainer: String? = null,
    @SerialName("onTertiaryContainer") val onTertiaryContainer: String? = null,
    @SerialName("background") val background: String? = null,
    @SerialName("onBackground") val onBackground: String? = null,
    @SerialName("surface") val surface: String? = null,
    @SerialName("onSurface") val onSurface: String? = null,
    @SerialName("surfaceVariant") val surfaceVariant: String? = null,
    @SerialName("onSurfaceVariant") val onSurfaceVariant: String? = null,
    @SerialName("surfaceTint") val surfaceTint: String? = null,
    @SerialName("inverseSurface") val inverseSurface: String? = null,
    @SerialName("inverseOnSurface") val inverseOnSurface: String? = null,
    @SerialName("surfaceBright") val surfaceBright: String? = null,
    @SerialName("surfaceDim") val surfaceDim: String? = null,
    @SerialName("surfaceContainerLowest") val surfaceContainerLowest: String? = null,
    @SerialName("surfaceContainerLow") val surfaceContainerLow: String? = null,
    @SerialName("surfaceContainer") val surfaceContainer: String? = null,
    @SerialName("surfaceContainerHigh") val surfaceContainerHigh: String? = null,
    @SerialName("surfaceContainerHighest") val surfaceContainerHighest: String? = null,
    @SerialName("outline") val outline: String? = null,
    @SerialName("outlineVariant") val outlineVariant: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("onError") val onError: String? = null,
    @SerialName("errorContainer") val errorContainer: String? = null,
    @SerialName("onErrorContainer") val onErrorContainer: String? = null,
    @SerialName("scrim") val scrim: String? = null,
  )

  @Immutable
  @Serializable
  data class ShapesSpec(
    /** "rounded" or "cut" (diagonal corners). */
    @SerialName("style") val style: String = "rounded",
    /** Multiplies the Material corner sizes (4, 8, 12, 16 and 28 dp). */
    @SerialName("scale") val scale: Float = 1f,
  )

  @Immutable
  @Serializable
  data class TypographySpec(
    /**
     * "system", "google_sans", "sans_flex", "outfit", "plus_jakarta_sans", "condensed",
     * "serif", "monospace", or any font family name installed on the device.
     */
    @SerialName("font") val font: String? = null,
    /** Weight of display, headline and title text (100–900). */
    @SerialName("titleWeight") val titleWeight: Int? = null,
    @SerialName("italicTitles") val italicTitles: Boolean = false,
  )

  @Immutable
  @Serializable
  data class PlayerSpec(
    /** Corner radius of the player artwork, in dp. */
    @SerialName("artworkCorner") val artworkCorner: Float? = null,
    /** Two or three colors for the player's gradient background, top to bottom. */
    @SerialName("background") val background: List<String>? = null,
    /** "liquid" (default) or "diamond": the play button's body. */
    @SerialName("playButton") val playButton: String? = null,
    /** Tilts the artwork, in degrees (e.g. -3). */
    @SerialName("artworkTilt") val artworkTilt: Float? = null,
    /** Frame drawn around the artwork: a color or a role ("primary", "onSurface"…). */
    @SerialName("artworkFrame") val artworkFrame: String? = null,
    /** A solid block offset behind the artwork, like a print shadow: color or role. */
    @SerialName("artworkBackdrop") val artworkBackdrop: String? = null,
    /** Hard offset shadow behind the song title: color or role. */
    @SerialName("titleShadow") val titleShadow: String? = null,
  ) {
    val diamondPlayButton: Boolean
      get() = playButton.equals("diamond", ignoreCase = true)
  }

  /** True when the theme forces dark mode, false when it forces light mode, null to follow. */
  val forcedDarkMode: Boolean?
    get() =
      when (mode.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> null
      }

  /** Player background gradient, if the theme defines a valid one. */
  fun playerBackgroundColors(): List<Color>? =
    player?.background?.mapNotNull { parseThemeColor(it) }?.takeIf { it.size >= 2 }

  companion object {
    const val FORMAT = "flux-theme"
  }
}

/** The custom theme in use, or null for the default Flux look. */
val LocalFluxTheme = staticCompositionLocalOf<FluxThemeSpec?> { null }

/** Parses "#RRGGBB" or "#AARRGGBB" (the "#" is optional). */
fun parseThemeColor(value: String?): Color? {
  val hex = value?.trim()?.removePrefix("#") ?: return null
  val argb =
    when (hex.length) {
      6 -> hex.toLongOrNull(16)?.let { it or 0xFF000000L }
      8 -> hex.toLongOrNull(16)
      else -> null
    } ?: return null
  return Color(argb.toInt())
}

/** A theme color: "#RRGGBB"/"#AARRGGBB" or a role of [scheme] ("primary", "onSurface"…). */
fun themeColorOrRole(value: String?, scheme: ColorScheme): Color? {
  val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return when (text.lowercase()) {
    "primary" -> scheme.primary
    "onprimary" -> scheme.onPrimary
    "secondary" -> scheme.secondary
    "tertiary" -> scheme.tertiary
    "surface" -> scheme.surface
    "onsurface" -> scheme.onSurface
    "background" -> scheme.background
    "onbackground" -> scheme.onBackground
    "white" -> Color.White
    "black" -> Color.Black
    else -> parseThemeColor(text)
  }
}

/** Applies every color the theme defines on top of a generated Material scheme. */
fun ColorScheme.withThemeColors(colors: FluxThemeSpec.Colors): ColorScheme {
  fun pick(value: String?, fallback: Color) = parseThemeColor(value) ?: fallback
  return copy(
    primary = pick(colors.primary, primary),
    onPrimary = pick(colors.onPrimary, onPrimary),
    primaryContainer = pick(colors.primaryContainer, primaryContainer),
    onPrimaryContainer = pick(colors.onPrimaryContainer, onPrimaryContainer),
    inversePrimary = pick(colors.inversePrimary, inversePrimary),
    secondary = pick(colors.secondary, secondary),
    onSecondary = pick(colors.onSecondary, onSecondary),
    secondaryContainer = pick(colors.secondaryContainer, secondaryContainer),
    onSecondaryContainer = pick(colors.onSecondaryContainer, onSecondaryContainer),
    tertiary = pick(colors.tertiary, tertiary),
    onTertiary = pick(colors.onTertiary, onTertiary),
    tertiaryContainer = pick(colors.tertiaryContainer, tertiaryContainer),
    onTertiaryContainer = pick(colors.onTertiaryContainer, onTertiaryContainer),
    background = pick(colors.background, background),
    onBackground = pick(colors.onBackground, onBackground),
    surface = pick(colors.surface, surface),
    onSurface = pick(colors.onSurface, onSurface),
    surfaceVariant = pick(colors.surfaceVariant, surfaceVariant),
    onSurfaceVariant = pick(colors.onSurfaceVariant, onSurfaceVariant),
    surfaceTint = pick(colors.surfaceTint, surfaceTint),
    inverseSurface = pick(colors.inverseSurface, inverseSurface),
    inverseOnSurface = pick(colors.inverseOnSurface, inverseOnSurface),
    surfaceBright = pick(colors.surfaceBright, surfaceBright),
    surfaceDim = pick(colors.surfaceDim, surfaceDim),
    surfaceContainerLowest = pick(colors.surfaceContainerLowest, surfaceContainerLowest),
    surfaceContainerLow = pick(colors.surfaceContainerLow, surfaceContainerLow),
    surfaceContainer = pick(colors.surfaceContainer, surfaceContainer),
    surfaceContainerHigh = pick(colors.surfaceContainerHigh, surfaceContainerHigh),
    surfaceContainerHighest = pick(colors.surfaceContainerHighest, surfaceContainerHighest),
    outline = pick(colors.outline, outline),
    outlineVariant = pick(colors.outlineVariant, outlineVariant),
    error = pick(colors.error, error),
    onError = pick(colors.onError, onError),
    errorContainer = pick(colors.errorContainer, errorContainer),
    onErrorContainer = pick(colors.onErrorContainer, onErrorContainer),
    scrim = pick(colors.scrim, scrim),
  )
}

/** Material shapes for the theme: rounded or cut corners, scaled. */
fun FluxThemeSpec.ShapesSpec.toShapes(): Shapes {
  val scale = scale.coerceIn(0f, 4f)
  val cut = style.equals("cut", ignoreCase = true)
  fun corner(size: Float) =
    if (cut) CutCornerShape((size * scale).dp) else RoundedCornerShape((size * scale).dp)
  return Shapes(
    extraSmall = corner(4f),
    small = corner(8f),
    medium = corner(12f),
    large = corner(16f),
    extraLarge = corner(28f),
  )
}

/** Font family named by a theme. */
fun themeFontFamily(name: String?): FontFamily? {
  val family = name?.trim().orEmpty()
  return when (family.lowercase()) {
    "" -> null
    "system",
    "default" -> FontFamily.Default
    "google_sans" -> GoogleSansFontFamily
    "sans_flex" -> SansFlexFontFamily
    "outfit" -> OutfitFontFamily
    "plus_jakarta_sans" -> PlusJakartaSansFontFamily
    "serif" -> FontFamily.Serif
    "mono",
    "monospace" -> FontFamily.Monospace
    "condensed" -> deviceFontFamily("sans-serif-condensed")
    else -> deviceFontFamily(family)
  }
}

/**
 * A font family installed on the device, declared in every weight and in italic so the theme's
 * title weight and italics reach the platform (a plain Typeface would ignore both).
 */
private fun deviceFontFamily(familyName: String): FontFamily? =
  runCatching {
      val name = androidx.compose.ui.text.font.DeviceFontFamilyName(familyName)
      val weights =
        listOf(
          FontWeight.Light,
          FontWeight.Normal,
          FontWeight.Medium,
          FontWeight.SemiBold,
          FontWeight.Bold,
          FontWeight.ExtraBold,
          FontWeight.Black,
        )
      FontFamily(
        weights.flatMap { weight ->
          listOf(
            androidx.compose.ui.text.font.Font(name, weight, FontStyle.Normal),
            androidx.compose.ui.text.font.Font(name, weight, FontStyle.Italic),
          )
        }
      )
    }
    .getOrNull()

/** Heavier and/or italic display, headline and title styles, as the theme asks. */
fun Typography.withThemeTitles(spec: FluxThemeSpec.TypographySpec): Typography {
  val weight = spec.titleWeight?.coerceIn(100, 900)?.let { FontWeight(it) }
  val style = if (spec.italicTitles) FontStyle.Italic else null
  if (weight == null && style == null) return this
  fun TextStyle.themed() =
    copy(fontWeight = weight ?: fontWeight, fontStyle = style ?: fontStyle)
  return copy(
    displayLarge = displayLarge.themed(),
    displayMedium = displayMedium.themed(),
    displaySmall = displaySmall.themed(),
    headlineLarge = headlineLarge.themed(),
    headlineMedium = headlineMedium.themed(),
    headlineSmall = headlineSmall.themed(),
    titleLarge = titleLarge.themed(),
    titleMedium = titleMedium.themed(),
    titleSmall = titleSmall.themed(),
  )
}

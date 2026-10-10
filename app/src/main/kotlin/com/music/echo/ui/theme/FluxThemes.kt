package echo.music.iad1tya.ui.theme

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import echo.music.iad1tya.constants.ActiveThemeIdKey
import echo.music.iad1tya.utils.dataStore
import echo.music.iad1tya.utils.get
import java.io.File
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Theme storage: built-in themes ship in `assets/themes`, imported ones are copied to the app's
 * private `themes` folder. The active theme is read once when the activity starts, so applying
 * one only needs the activity to be recreated — no new APK.
 */
object FluxThemes {

  class Entry(val spec: FluxThemeSpec, val builtIn: Boolean)

  private const val ASSET_DIR = "themes"
  private const val IMPORT_DIR = "themes"
  private const val MAX_THEME_BYTES = 256 * 1024

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = true
  }

  /** Parses and validates a theme file. Throws [IllegalArgumentException] with a reason. */
  fun parse(text: String): FluxThemeSpec {
    val spec =
      try {
        json.decodeFromString(FluxThemeSpec.serializer(), text)
      } catch (e: Exception) {
        throw IllegalArgumentException("JSON inválido: ${e.message?.lineSequence()?.firstOrNull()}")
      }
    require(spec.format == FluxThemeSpec.FORMAT) { "\"format\" deve ser \"${FluxThemeSpec.FORMAT}\"" }
    require(spec.id.isNotBlank()) { "\"id\" está vazio" }
    require(spec.name.isNotBlank()) { "\"name\" está vazio" }
    val colors = spec.colors
    val definesColor =
      listOf(colors.seed, colors.primary, colors.background, colors.surface).any {
        parseThemeColor(it) != null
      }
    require(definesColor || spec.albumColors) {
      "defina pelo menos \"colors.seed\" ou \"colors.primary\""
    }
    return spec.copy(id = sanitizeId(spec.id))
  }

  fun builtIns(context: Context): List<FluxThemeSpec> =
    runCatching { context.assets.list(ASSET_DIR)?.toList().orEmpty() }
      .getOrDefault(emptyList())
      .filter { it.endsWith(".json") }
      .sorted()
      .mapNotNull { name ->
        runCatching {
            context.assets.open("$ASSET_DIR/$name").use { parse(it.readBytes().decodeToString()) }
          }
          .onFailure { Timber.w(it, "Invalid built-in theme %s", name) }
          .getOrNull()
      }

  fun imported(context: Context): List<FluxThemeSpec> =
    importDir(context)
      .listFiles { file -> file.extension == "json" }
      .orEmpty()
      .sortedBy { it.name }
      .mapNotNull { file ->
        runCatching { parse(file.readText()) }
          .onFailure { Timber.w(it, "Invalid imported theme %s", file.name) }
          .getOrNull()
      }

  /** Built-in themes first; an imported theme with the same id replaces the built-in one. */
  fun all(context: Context): List<Entry> {
    val imported = imported(context)
    val importedIds = imported.map { it.id }.toSet()
    return builtIns(context).filter { it.id !in importedIds }.map { Entry(it, builtIn = true) } +
      imported.map { Entry(it, builtIn = false) }
  }

  fun find(context: Context, id: String): FluxThemeSpec? {
    if (id.isBlank()) return null
    return all(context).firstOrNull { it.spec.id == id }?.spec
  }

  /** Copies a theme picked by the user into the app, after validating it. */
  fun import(context: Context, uri: Uri): FluxThemeSpec {
    val bytes =
      context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = input.readNBytesCompat(MAX_THEME_BYTES + 1)
        require(buffer.size <= MAX_THEME_BYTES) { "o arquivo é grande demais para um tema" }
        buffer
      } ?: throw IllegalArgumentException("não foi possível abrir o arquivo")
    val spec = parse(bytes.decodeToString())
    File(importDir(context), "${spec.id}.json").writeText(json.encodeToString(FluxThemeSpec.serializer(), spec))
    return spec
  }

  fun delete(context: Context, id: String) {
    File(importDir(context), "${sanitizeId(id)}.json").delete()
  }

  fun activeId(context: Context): String =
    runCatching { context.dataStore[ActiveThemeIdKey] }.getOrNull().orEmpty()

  /** The theme to use right now, or null for the default look. Blocking but tiny. */
  fun loadActive(context: Context): FluxThemeSpec? =
    runCatching { find(context, activeId(context)) }
      .onFailure { Timber.w(it, "Unable to load the active theme") }
      .getOrNull()

  suspend fun setActive(context: Context, id: String) {
    context.dataStore.edit { it[ActiveThemeIdKey] = id }
  }

  private fun importDir(context: Context): File =
    File(context.filesDir, IMPORT_DIR).apply { mkdirs() }

  private fun sanitizeId(id: String): String =
    id.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "-").take(64).ifBlank { "theme" }

  private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (total < limit) {
      val read = read(buffer, 0, minOf(buffer.size, limit - total))
      if (read < 0) break
      out.write(buffer, 0, read)
      total += read
    }
    return out.toByteArray()
  }
}

package echo.music.iad1tya.mash

import android.content.Context
import echo.music.iad1tya.FluxBrand
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Who gives the beat. */
enum class MashMode {
  AUTO,
  FIRST_IS_BEAT,
  SECOND_IS_BEAT,
}

/** Steps shown while a mash is made. */
enum class MashStage {
  MODEL,
  DOWNLOAD,
  ANALYZE,
  SEPARATE,
  FIT,
  MIX,
}

class MashResult(
  val file: File,
  val firstIsBeat: Boolean,
  val beatBpm: Double,
  val voiceBpm: Double,
  val semitones: Int,
  val durationSec: Double,
)

/**
 * Makes a mash on the phone: downloads both songs, takes their loudest minute, separates voice
 * and beat with Spleeter, fits the voice to the beat's tempo, key and beat grid, and mixes them
 * into a WAV ready to play.
 */
class MashEngine(private val context: Context) {

  suspend fun create(
    firstId: String,
    secondId: String,
    mode: MashMode,
    onProgress: (MashStage, Float) -> Unit,
  ): MashResult =
    withContext(Dispatchers.Default) {
      var base = 0f
      if (!MashModels.isReady(context)) {
        withContext(Dispatchers.IO) {
          MashModels.download(context) { onProgress(MashStage.MODEL, it * 0.15f) }
        }
        base = 0.15f
      }
      val span = 1f - base
      fun at(fraction: Float) = base + span * fraction

      // 1. The two passages (15% of the work).
      val source = MashAudioSource(context)
      val firstMix =
        withContext(Dispatchers.IO) {
          source.loudestPassage(firstId, PASSAGE_SECONDS) {
            onProgress(MashStage.DOWNLOAD, at(0.075f * it))
          }
        }
      val secondMix =
        withContext(Dispatchers.IO) {
          source.loudestPassage(secondId, PASSAGE_SECONDS) {
            onProgress(MashStage.DOWNLOAD, at(0.075f + 0.075f * it))
          }
        }

      // 2. Tempo, beat grid and key of each passage.
      onProgress(MashStage.ANALYZE, at(0.15f))
      val first = SongPart.analyze(firstMix)
      coroutineContext.ensureActive()
      val second = SongPart.analyze(secondMix)
      onProgress(MashStage.ANALYZE, at(0.2f))

      // 3. Voice / beat separation with the AI model (most of the work).
      val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
      val job = coroutineContext.job
      val separated =
        SpleeterSeparator(MashModels.vocals(context), MashModels.accompaniment(context), threads).use { net ->
          val total = (net.chunksFor(firstMix.size) + net.chunksFor(secondMix.size)).toFloat()
          var done = 0
          fun tick() {
            done++
            onProgress(MashStage.SEPARATE, at(0.2f + 0.6f * done / total))
          }
          val keepFirstVoice = mode != MashMode.FIRST_IS_BEAT
          val keepFirstBeat = mode != MashMode.SECOND_IS_BEAT
          val a = net.separate(firstMix, keepFirstVoice, keepFirstBeat) { _, _ ->
            job.ensureActive()
            tick()
          }
          coroutineContext.ensureActive()
          val b = net.separate(secondMix, keepFirstBeat, keepFirstVoice) { _, _ ->
            job.ensureActive()
            tick()
          }
          a to b
        }
      val (firstStems, secondStems) = separated

      val firstIsBeat =
        when (mode) {
          MashMode.FIRST_IS_BEAT -> true
          MashMode.SECOND_IS_BEAT -> false
          MashMode.AUTO ->
            MashMixer.firstIsBeat(firstStems.vocalRatio, secondStems.vocalRatio, first, second)
        }
      val beatPart = if (firstIsBeat) first else second
      val voicePart = if (firstIsBeat) second else first
      val beat = (if (firstIsBeat) firstStems.accompaniment else secondStems.accompaniment)!!
      val voice = (if (firstIsBeat) secondStems.vocals else firstStems.vocals)!!

      // 4. Voice on the beat's tempo, key and grid.
      onProgress(MashStage.FIT, at(0.82f))
      val plan = MashMixer.plan(beatPart, voicePart)
      val fitted = MashMixer.fitVoice(voice, plan)
      coroutineContext.ensureActive()

      // 5. Mix and write.
      onProgress(MashStage.MIX, at(0.92f))
      val mix = MashMixer.render(beat, fitted, plan)
      val dir = File(context.cacheDir, "mash").apply { mkdirs() }
      dir.listFiles()?.forEach { it.delete() }
      val out = File(dir, "mash-$firstId-$secondId.wav")
      writeWav(out, mix)
      onProgress(MashStage.MIX, 1f)
      MashResult(
        file = out,
        firstIsBeat = firstIsBeat,
        beatBpm = plan.beatBpm,
        voiceBpm = plan.voiceBpm,
        semitones = plan.semitones,
        durationSec = mix.size.toDouble() / MASH_RATE,
      )
    }

  companion object {
    /** Passage length: 5 network chunks of 512 frames (≈ 59.4 s). */
    const val PASSAGE_SECONDS = 59
  }
}

/** The two Spleeter networks, downloaded once from the Flux releases. */
object MashModels {
  private const val VOCALS = "vocals.fp16.onnx"
  private const val ACCOMPANIMENT = "accompaniment.fp16.onnx"
  private const val TAG = "mash-models"

  private fun dir(context: Context) = File(context.filesDir, "mash-models").apply { mkdirs() }

  fun vocals(context: Context) = File(dir(context), VOCALS)

  fun accompaniment(context: Context) = File(dir(context), ACCOMPANIMENT)

  fun isReady(context: Context) =
    listOf(vocals(context), accompaniment(context)).all { it.length() > 1_000_000 }

  fun download(context: Context, onProgress: (Float) -> Unit) {
    val http = OkHttpClient()
    val files = listOf(VOCALS, ACCOMPANIMENT)
    files.forEachIndexed { index, name ->
      val target = File(dir(context), name)
      if (target.length() > 1_000_000) return@forEachIndexed
      val part = File(target.path + ".part")
      val url = "${FluxBrand.RELEASES_URL}/download/$TAG/$name"
      http.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("model HTTP ${response.code}")
        val body = response.body ?: throw IOException("empty model")
        val total = body.contentLength().takeIf { it > 0 } ?: 20_000_000L
        var done = 0L
        val buffer = ByteArray(1 shl 16)
        body.byteStream().use { input ->
          part.outputStream().use { output ->
            while (true) {
              val read = input.read(buffer)
              if (read < 0) break
              output.write(buffer, 0, read)
              done += read
              onProgress((index + (done.toFloat() / total).coerceIn(0f, 1f)) / files.size)
            }
          }
        }
        if (done < 1_000_000) throw IOException("model too small")
      }
      if (!part.renameTo(target)) throw IOException("cannot store model")
    }
    onProgress(1f)
  }
}

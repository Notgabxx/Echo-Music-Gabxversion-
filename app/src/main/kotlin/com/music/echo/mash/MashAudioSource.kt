package echo.music.iad1tya.mash

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import echo.music.iad1tya.constants.AudioQuality
import echo.music.iad1tya.utils.YTPlayerUtils
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads a song's audio, decodes it and cuts the passage the mash will use. */
internal class MashAudioSource(private val context: Context) {
  private val http = OkHttpClient()

  /**
   * The loudest [seconds]-long passage of the song [videoId] at [MASH_RATE] (choruses and drops are
   * the loudest parts), plus the song's full duration in seconds.
   */
  suspend fun loudestPassage(
    videoId: String,
    seconds: Int,
    onProgress: (Float) -> Unit,
  ): StereoPcm {
    val file = File(context.cacheDir, "mash-src-$videoId")
    try {
      download(videoId, file) { onProgress(it * 0.6f) }
      coroutineContext.ensureActive()
      val decoded = decode(file) { onProgress(0.6f + it * 0.35f) }
      coroutineContext.ensureActive()
      val window = cutLoudest(decoded, seconds)
      onProgress(1f)
      return window
    } finally {
      file.delete()
    }
  }

  private suspend fun download(videoId: String, dest: File, onProgress: (Float) -> Unit) {
    val connectivity = context.getSystemService<ConnectivityManager>() ?: throw IOException("offline")
    val playback =
      YTPlayerUtils.playerResponseForPlayback(
          videoId = videoId,
          audioQuality = AudioQuality.OPUS,
          connectivityManager = connectivity,
        )
        .getOrElse { throw IOException("stream unavailable", it) }
    val length = playback.format.contentLength ?: 10_000_000L
    val request = Request.Builder().url("${playback.streamUrl}&range=0-$length").build()
    http.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
      val body = response.body ?: throw IOException("empty body")
      val total = body.contentLength().takeIf { it > 0 } ?: length
      var done = 0L
      val buffer = ByteArray(1 shl 16)
      body.byteStream().use { input ->
        dest.outputStream().use { output ->
          while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            done += read
            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
          }
        }
      }
    }
  }

  private class Decoded(val samples: ShortArray, val frames: Int, val channels: Int, val rate: Int)

  /** MediaExtractor + MediaCodec → interleaved 16-bit PCM. */
  private suspend fun decode(file: File, onProgress: (Float) -> Unit): Decoded {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
      extractor.setDataSource(file.absolutePath)
      val track =
        (0 until extractor.trackCount).firstOrNull {
          extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IOException("no audio track")
      extractor.selectTrack(track)
      val format = extractor.getTrackFormat(track)
      val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
      var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
      codec = decoder
      decoder.configure(format, null, null, 0)
      decoder.start()

      var floatOutput = false
      var out = ShortArray(maxOf(1 shl 20, (durationUs / 1_000_000.0 * rate * channels * 1.05).toInt()))
      var size = 0
      val info = MediaCodec.BufferInfo()
      var inputDone = false
      var outputDone = false
      while (!outputDone) {
        coroutineContext.ensureActive()
        if (!inputDone) {
          val inIndex = decoder.dequeueInputBuffer(10_000)
          if (inIndex >= 0) {
            val buffer = decoder.getInputBuffer(inIndex)!!
            val read = extractor.readSampleData(buffer, 0)
            if (read < 0) {
              decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              decoder.queueInputBuffer(inIndex, 0, read, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }
        val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
        when {
          outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val f = decoder.outputFormat
            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            floatOutput =
              f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                f.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
          }
          outIndex >= 0 -> {
            if (info.size > 0) {
              val buffer = decoder.getOutputBuffer(outIndex)!!
              buffer.position(info.offset)
              buffer.limit(info.offset + info.size)
              buffer.order(ByteOrder.nativeOrder())
              if (floatOutput) {
                val floats = buffer.asFloatBuffer()
                val count = floats.remaining()
                if (size + count > out.size) out = out.copyOf(maxOf(out.size * 3 / 2, size + count))
                for (i in 0 until count) {
                  out[size + i] = (floats.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }
                size += count
              } else {
                val shorts = buffer.asShortBuffer()
                val count = shorts.remaining()
                if (size + count > out.size) out = out.copyOf(maxOf(out.size * 3 / 2, size + count))
                shorts.get(out, size, count)
                size += count
              }
              if (durationUs > 0) {
                onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
              }
            }
            decoder.releaseOutputBuffer(outIndex, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
          }
        }
      }
      val ch = channels.coerceAtLeast(1)
      return Decoded(out, size / ch, ch, rate)
    } finally {
      runCatching { codec?.stop() }
      runCatching { codec?.release() }
      extractor.release()
    }
  }

  /** Loudest window of [seconds] (by 1 s RMS blocks), as float stereo resampled to [MASH_RATE]. */
  private fun cutLoudest(d: Decoded, seconds: Int): StereoPcm {
    val block = d.rate
    val blocks = d.frames / block
    val energy = DoubleArray(blocks)
    for (b in 0 until blocks) {
      var s = 0.0
      val from = b * block * d.channels
      val to = from + block * d.channels
      for (i in from until to) {
        val v = d.samples[i] / 32768.0
        s += v * v
      }
      energy[b] = s
    }
    val span = minOf(seconds, blocks)
    var bestStart = 0
    var best = -1.0
    var sum = 0.0
    for (b in 0 until blocks) {
      sum += energy[b]
      if (b >= span) sum -= energy[b - span]
      if (b >= span - 1 && sum > best) {
        best = sum
        bestStart = b - span + 1
      }
    }
    val startFrame = bestStart * block
    val frames = minOf(span * block, d.frames - startFrame).coerceAtLeast(0)
    val left = FloatArray(frames)
    val right = FloatArray(frames)
    for (i in 0 until frames) {
      val base = (startFrame + i) * d.channels
      val l = d.samples[base] / 32768f
      val r = if (d.channels > 1) d.samples[base + 1] / 32768f else l
      left[i] = l
      right[i] = r
    }
    if (d.rate == MASH_RATE) return StereoPcm(left, right)
    val ratio = MASH_RATE.toDouble() / d.rate
    return StereoPcm(Resampler.resample(left, ratio), Resampler.resample(right, ratio))
  }
}

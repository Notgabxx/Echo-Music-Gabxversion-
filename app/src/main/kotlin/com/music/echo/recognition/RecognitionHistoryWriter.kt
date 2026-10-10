package echo.music.iad1tya.recognition

import android.content.Context
import com.music.shazamkit.models.RecognitionResult
import dagger.hilt.android.EntryPointAccessors
import echo.music.iad1tya.db.entities.RecognitionHistory
import echo.music.iad1tya.widget.RecognizerWidgetEntryPoint
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Writes a recognized song to the recognition history, once per result. */
internal suspend fun saveRecognitionToHistory(context: Context, result: RecognitionResult) {
  if (!MusicRecognitionService.claimHistorySave(result)) return
  // NonCancellable: the service may stop right after a match; the entry must still be written.
  withContext(Dispatchers.IO + NonCancellable) {
    try {
      EntryPointAccessors.fromApplication(
          context.applicationContext,
          RecognizerWidgetEntryPoint::class.java,
        )
        .databaseDao()
        .insert(result.toHistoryEntry())
    } catch (e: Exception) {
      Timber.w(e, "Unable to save recognition to history")
    }
  }
}

internal fun RecognitionResult.toHistoryEntry(): RecognitionHistory =
  RecognitionHistory(
    trackId = trackId,
    title = title,
    artist = artist,
    album = album,
    coverArtUrl = coverArtUrl,
    coverArtHqUrl = coverArtHqUrl,
    genre = genre,
    releaseDate = releaseDate,
    label = label,
    shazamUrl = shazamUrl,
    appleMusicUrl = appleMusicUrl,
    spotifyUrl = spotifyUrl,
    isrc = isrc,
    youtubeVideoId = youtubeVideoId,
    recognizedAt = LocalDateTime.now(),
  )

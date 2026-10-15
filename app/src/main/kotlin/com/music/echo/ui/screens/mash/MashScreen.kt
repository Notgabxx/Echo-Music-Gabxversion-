package echo.music.iad1tya.ui.screens.mash

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Who carries the beat in the mash. */
enum class MashRole {
  AUTO,
  FIRST_IS_BEAT,
  SECOND_IS_BEAT,
}

private data class MashSong(val id: String, val title: String, val artist: String, val art: String?)

private fun SongItem.toMashSong() =
  MashSong(id, title, artists.joinToString(", ") { it.name }, thumbnail)

/**
 * Mash com IA: pick two songs and which one gives the beat (or let the AI decide). The engine
 * that separates voice and beat on the phone ships in a later version; this screen sets it up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MashScreen(
  navController: NavController,
  songId: String,
  viewModel: MashViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
) {
  val playerConnection = LocalPlayerConnection.current
  val mashState by viewModel.state.collectAsState()
  var first by remember {
    mutableStateOf(
      playerConnection?.mediaMetadata?.value?.takeIf { it.id == songId }?.let { meta ->
        MashSong(meta.id, meta.title, meta.artists.joinToString(", ") { it.name }, meta.thumbnailUrl)
      }
    )
  }
  var second by remember { mutableStateOf<MashSong?>(null) }
  var role by rememberSaveable { mutableStateOf(MashRole.AUTO) }
  var query by rememberSaveable { mutableStateOf("") }
  var results by remember { mutableStateOf<List<MashSong>>(emptyList()) }
  var searching by remember { mutableStateOf(false) }

  LaunchedEffect(songId) {
    if (first == null) {
      first =
        withContext(Dispatchers.IO) {
          YouTube.queue(listOf(songId)).getOrNull()?.firstOrNull()?.toMashSong()
        }
    }
  }

  LaunchedEffect(query) {
    if (query.isBlank()) {
      results = emptyList()
      return@LaunchedEffect
    }
    delay(350) // wait for the user to stop typing
    searching = true
    results =
      withContext(Dispatchers.IO) {
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
          .getOrNull()
          ?.items
          ?.filterIsInstance<SongItem>()
          .orEmpty()
          .filter { it.id != songId }
          .take(12)
          .map { it.toMashSong() }
      }
    searching = false
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.mash_title), fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = { navController.navigateUp() }) {
            Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.cd_back))
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
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.fillMaxSize(),
    ) {
      item {
        val shownRole =
          (mashState as? MashUiState.Ready)?.result?.let {
            if (it.firstIsBeat) MashRole.FIRST_IS_BEAT else MashRole.SECOND_IS_BEAT
          } ?: role
        MashHero(first = first, second = second, role = shownRole)
      }

      item {
        MashActionCard(
          state = mashState,
          canCreate = first != null && second != null,
          onCreate = {
            val a = first
            val b = second
            if (a != null && b != null) viewModel.create(a.id, b.id, role)
          },
          onCancel = viewModel::cancel,
          onReset = viewModel::reset,
          onPlayStarted = { playerConnection?.player?.pause() },
        )
      }

      item {
        Text(
          stringResource(R.string.mash_roles_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          RoleOption(
            selected = role == MashRole.AUTO,
            title = stringResource(R.string.mash_role_auto),
            description = stringResource(R.string.mash_role_auto_desc),
            onClick = {
              role = MashRole.AUTO
              viewModel.reset()
            },
          )
          RoleOption(
            selected = role == MashRole.FIRST_IS_BEAT,
            title = stringResource(R.string.mash_role_beat_from, first?.title ?: "1"),
            description = stringResource(R.string.mash_role_voice_from, second?.title ?: "2"),
            onClick = {
              role = MashRole.FIRST_IS_BEAT
              viewModel.reset()
            },
          )
          RoleOption(
            selected = role == MashRole.SECOND_IS_BEAT,
            title = stringResource(R.string.mash_role_beat_from, second?.title ?: "2"),
            description = stringResource(R.string.mash_role_voice_from, first?.title ?: "1"),
            onClick = {
              role = MashRole.SECOND_IS_BEAT
              viewModel.reset()
            },
          )
        }
      }

      item {
        Text(
          stringResource(R.string.mash_pick_second),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
          placeholder = { Text(stringResource(R.string.mash_search_hint)) },
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        if (searching) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
      }

      items(results, key = { "result:" + it.id }) { song ->
        SongRow(
          song = song,
          selected = second?.id == song.id,
          onClick = {
            second = song
            query = ""
            viewModel.reset()
          },
        )
      }
    }
  }
}

/** Create button → live progress → a player for the finished mash. */
@Composable
private fun MashActionCard(
  state: MashUiState,
  canCreate: Boolean,
  onCreate: () -> Unit,
  onCancel: () -> Unit,
  onReset: () -> Unit,
  onPlayStarted: () -> Unit,
) {
  AnimatedContent(targetState = state, contentKey = { it::class }, label = "mash_action") { s ->
    when (s) {
      is MashUiState.Idle,
      is MashUiState.Failed -> {
        Column {
          if (s is MashUiState.Failed) {
            Text(
              s.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(bottom = 8.dp),
            )
          }
          Button(
            onClick = onCreate,
            enabled = canCreate,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
          ) {
            Icon(painterResource(R.drawable.mash), contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.mash_create), fontWeight = FontWeight.Bold)
          }
          Spacer(Modifier.height(8.dp))
          Text(
            stringResource(R.string.mash_engine_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      is MashUiState.Working -> MashProgressCard(s, onCancel)
      is MashUiState.Ready -> MashPlayerCard(s.result, onReset, onPlayStarted)
    }
  }
}

@Composable
private fun MashProgressCard(state: MashUiState.Working, onCancel: () -> Unit) {
  val progress by
    androidx.compose.animation.core.animateFloatAsState(
      targetValue = state.fraction,
      label = "mash_progress",
    )
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(stageLabel(state.stage)),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        Text(
          "${(progress * 100).toInt()}%",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Spacer(Modifier.height(12.dp))
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
      )
      Spacer(Modifier.height(10.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(R.string.mash_working_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel) { Text(stringResource(R.string.mash_cancel)) }
      }
    }
  }
}

private fun stageLabel(stage: echo.music.iad1tya.mash.MashStage): Int =
  when (stage) {
    echo.music.iad1tya.mash.MashStage.MODEL -> R.string.mash_stage_model
    echo.music.iad1tya.mash.MashStage.DOWNLOAD -> R.string.mash_stage_download
    echo.music.iad1tya.mash.MashStage.ANALYZE -> R.string.mash_stage_analyze
    echo.music.iad1tya.mash.MashStage.SEPARATE -> R.string.mash_stage_separate
    echo.music.iad1tya.mash.MashStage.FIT -> R.string.mash_stage_fit
    echo.music.iad1tya.mash.MashStage.MIX -> R.string.mash_stage_mix
  }

/** Plays the finished mash right away, here on the screen. */
@Composable
private fun MashPlayerCard(
  result: echo.music.iad1tya.mash.MashResult,
  onReset: () -> Unit,
  onPlayStarted: () -> Unit,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val player = remember { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
  var playing by remember { mutableStateOf(false) }
  var position by remember { mutableStateOf(0L) }
  val durationMs = (result.durationSec * 1000).toLong().coerceAtLeast(1L)

  androidx.compose.runtime.DisposableEffect(player) {
    val listener =
      object : androidx.media3.common.Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          playing = isPlaying
        }
      }
    player.addListener(listener)
    onDispose {
      player.removeListener(listener)
      player.release()
    }
  }
  LaunchedEffect(result.file) {
    player.setMediaItem(
      androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(result.file))
    )
    player.prepare()
    onPlayStarted()
    player.play()
  }
  LaunchedEffect(player) {
    while (true) {
      position = player.currentPosition
      delay(200)
    }
  }

  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          onClick = {
            if (player.isPlaying) {
              player.pause()
            } else {
              if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) player.seekTo(0)
              onPlayStarted()
              player.play()
            }
          },
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(56.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              painterResource(if (playing) R.drawable.pause else R.drawable.play),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(28.dp),
            )
          }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(
            stringResource(R.string.mash_ready_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
            stringResource(
              R.string.mash_ready_info,
              result.beatBpm.toInt(),
              if (result.semitones == 0) stringResource(R.string.mash_key_kept)
              else stringResource(R.string.mash_key_moved, "%+d".format(result.semitones)),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
          )
        }
      }
      Spacer(Modifier.height(12.dp))
      androidx.compose.material3.Slider(
        value = (position.toFloat() / durationMs).coerceIn(0f, 1f),
        onValueChange = { player.seekTo((it * durationMs).toLong()) },
      )
      Row {
        Text(
          formatTime(position),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.weight(1f),
        )
        Text(
          formatTime(durationMs),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) {
        Text(stringResource(R.string.mash_again))
      }
    }
  }
}

private fun formatTime(ms: Long): String {
  val s = (ms / 1000).toInt()
  return "%d:%02d".format(s / 60, s % 60)
}

/** The two songs side by side, joined by a glowing badge, with the role each one plays. */
@Composable
private fun MashHero(first: MashSong?, second: MashSong?, role: MashRole) {
  val primary = MaterialTheme.colorScheme.primary
  val tertiary = MaterialTheme.colorScheme.tertiary
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(28.dp))
        .background(
          Brush.linearGradient(
            listOf(primary.copy(alpha = 0.28f), tertiary.copy(alpha = 0.18f), Color.Transparent)
          )
        )
        .padding(16.dp)
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Slot(
        song = first,
        label = roleLabel(role, isFirst = true),
        modifier = Modifier.weight(1f),
      )
      Slot(
        song = second,
        label = roleLabel(role, isFirst = false),
        modifier = Modifier.weight(1f),
      )
    }
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.align(Alignment.TopCenter)
          .padding(top = 52.dp)
          .size(44.dp)
          .clip(CircleShape)
          .background(Brush.linearGradient(listOf(primary, tertiary)))
          .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
    ) {
      Icon(
        painterResource(R.drawable.mash),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(22.dp),
      )
    }
  }
}

@Composable
private fun roleLabel(role: MashRole, isFirst: Boolean): String =
  when (role) {
    MashRole.AUTO -> stringResource(R.string.mash_label_ai)
    MashRole.FIRST_IS_BEAT ->
      stringResource(if (isFirst) R.string.mash_label_beat else R.string.mash_label_voice)
    MashRole.SECOND_IS_BEAT ->
      stringResource(if (isFirst) R.string.mash_label_voice else R.string.mash_label_beat)
  }

@Composable
private fun Slot(song: MashSong?, label: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(22.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
      if (song?.art != null) {
        AsyncImage(
          model = song.art,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Icon(
          painterResource(R.drawable.music_note),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(36.dp),
        )
      }
    }
    Spacer(Modifier.height(8.dp))
    AnimatedContent(targetState = label, label = "mash_role") { text ->
      Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
      ) {
        Text(
          text,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      song?.title ?: stringResource(R.string.mash_empty_slot),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      song?.artist.orEmpty(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun RoleOption(selected: Boolean, title: String, description: String, onClick: () -> Unit) {
  val border by
    animateColorAsState(
      if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
      label = "mash_option_border",
    )
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
      else MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(if (selected) 2.dp else 1.dp, border),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
      Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun SongRow(song: MashSong, selected: Boolean, onClick: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .background(
          if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
          else Color.Transparent
        )
        .padding(8.dp),
  ) {
    AsyncImage(
      model = song.art,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
      Text(
        song.artist,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

package echo.music.iad1tya.ui.screens.mash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import echo.music.iad1tya.R
import echo.music.iad1tya.mash.MashEngine
import echo.music.iad1tya.mash.MashMode
import echo.music.iad1tya.mash.MashResult
import echo.music.iad1tya.mash.MashStage
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface MashUiState {
  data object Idle : MashUiState

  data class Working(val stage: MashStage, val fraction: Float) : MashUiState

  data class Ready(val result: MashResult) : MashUiState

  data class Failed(val message: String) : MashUiState
}

@HiltViewModel
class MashViewModel @Inject constructor(@ApplicationContext private val context: Context) :
  ViewModel() {
  private val _state = MutableStateFlow<MashUiState>(MashUiState.Idle)
  val state: StateFlow<MashUiState> = _state.asStateFlow()
  private var job: Job? = null

  fun create(firstId: String, secondId: String, role: MashRole) {
    if (_state.value is MashUiState.Working) return
    val mode =
      when (role) {
        MashRole.AUTO -> MashMode.AUTO
        MashRole.FIRST_IS_BEAT -> MashMode.FIRST_IS_BEAT
        MashRole.SECOND_IS_BEAT -> MashMode.SECOND_IS_BEAT
      }
    _state.value = MashUiState.Working(MashStage.DOWNLOAD, 0f)
    job =
      viewModelScope.launch {
        try {
          val result =
            MashEngine(context).create(firstId, secondId, mode) { stage, fraction ->
              _state.value = MashUiState.Working(stage, fraction.coerceIn(0f, 1f))
            }
          _state.value = MashUiState.Ready(result)
        } catch (e: CancellationException) {
          _state.value = MashUiState.Idle
          throw e
        } catch (t: Throwable) {
          Timber.e(t, "Mash failed")
          _state.value = MashUiState.Failed(messageFor(t))
        }
      }
  }

  fun cancel() {
    job?.cancel()
    job = null
    _state.value = MashUiState.Idle
  }

  fun reset() {
    if (_state.value !is MashUiState.Working) _state.value = MashUiState.Idle
  }

  private fun messageFor(t: Throwable): String =
    context.getString(
      when {
        t is UnsatisfiedLinkError || t is NoClassDefFoundError -> R.string.mash_error_device
        t is OutOfMemoryError -> R.string.mash_error_memory
        generateSequence(t) { it.cause }.any { it is IOException } -> R.string.mash_error_network
        else -> R.string.mash_error_generic
      }
    )
}

package echo.music.iad1tya.recognition

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import echo.music.iad1tya.MainActivity
import echo.music.iad1tya.constants.RecognitionSourceKey
import echo.music.iad1tya.extensions.toEnum
import echo.music.iad1tya.utils.dataStore
import echo.music.iad1tya.utils.get

/**
 * Invisible trampoline used by the quick-settings tile. It starts recognition with the source the
 * user picked on the recognition screen: the microphone, or the phone's own audio (which first
 * needs the system capture consent, handled by [DeviceAudioConsentActivity]).
 */
class RecognitionLaunchActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleRecognitionLaunch()
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    handleRecognitionLaunch()
  }

  private fun handleRecognitionLaunch() {
    when {
      !hasRecordPermission() -> openRecognitionPermissionFlow()
      preferredSource() == RecognitionSource.DEVICE_AUDIO && DeviceAudioCapture.isSupported ->
        startActivity(Intent(this, DeviceAudioConsentActivity::class.java))
      else -> startRecognitionService()
    }
    finish()
  }

  private fun preferredSource(): RecognitionSource =
    runCatching { dataStore[RecognitionSourceKey] }
      .getOrNull()
      .toEnum(RecognitionSource.MICROPHONE)

  private fun hasRecordPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
  }

  private fun openRecognitionPermissionFlow() {
    val intent =
      Intent(this, MainActivity::class.java).apply {
        action = MainActivity.ACTION_RECOGNITION
        putExtra(MainActivity.EXTRA_AUTO_START_RECOGNITION, true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    startActivity(intent)
  }

  private fun startRecognitionService() {
    if (!hasRecordPermission()) return

    val serviceIntent = Intent(this, RecognitionForegroundService::class.java)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        startForegroundService(serviceIntent)
      } catch (e: Exception) {
        if (
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            e is android.app.ForegroundServiceStartNotAllowedException
        ) {
          // Ignored
        } else {
          throw e
        }
      }
    } else {
      startService(serviceIntent)
    }
  }
}

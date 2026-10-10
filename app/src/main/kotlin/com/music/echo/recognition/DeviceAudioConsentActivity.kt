package echo.music.iad1tya.recognition

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import echo.music.iad1tya.R
import timber.log.Timber

/**
 * Invisible activity that shows the system capture-consent dialog and, once the user accepts,
 * starts listening to the phone's audio. Used from outside the app (quick-settings tile), where
 * there is no screen to host the dialog. It runs in its own task, so the app the user is listening
 * to stays on screen (and keeps playing) underneath.
 */
class DeviceAudioConsentActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (!DeviceAudioCapture.isSupported) {
      finish()
      return
    }
    if (savedInstanceState == null) {
      try {
        startActivityForResult(DeviceAudioCapture.consentIntent(this), REQUEST_CAPTURE_CONSENT)
      } catch (e: Exception) {
        Timber.w(e, "Unable to request audio capture consent")
        Toast.makeText(this, R.string.recognition_device_capture_unavailable, Toast.LENGTH_SHORT)
          .show()
        finish()
      }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode != REQUEST_CAPTURE_CONSENT) return
    if (resultCode == RESULT_OK && data != null) {
      DeviceAudioCapture.start(this, resultCode, data, fromApp = false)
    } else {
      Toast.makeText(this, R.string.recognition_device_permission_denied, Toast.LENGTH_SHORT).show()
    }
    finish()
  }

  private companion object {
    const val REQUEST_CAPTURE_CONSENT = 4107
  }
}

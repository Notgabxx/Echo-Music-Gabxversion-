package echo.music.iad1tya

/**
 * Single source of truth for the Flux brand and its project links.
 *
 * Flux is a fork of Echo Music (GPL-3.0). The upstream credit below is required by the license
 * ("Appropriate Legal Notices") and must stay visible in the About screen; every other link points to
 * the Flux project itself.
 */
object FluxBrand {
  const val APP_NAME = "Flux"

  const val GITHUB_OWNER = "Notgabxx"
  const val GITHUB_REPO = "Echo-Music-Gabxversion-"
  const val REPO_SLUG = "$GITHUB_OWNER/$GITHUB_REPO"

  const val REPO_URL = "https://github.com/$REPO_SLUG"
  const val RELEASES_URL = "$REPO_URL/releases"
  const val API_REPO_URL = "https://api.github.com/repos/$REPO_SLUG"
  const val RAW_MAIN_URL = "https://raw.githubusercontent.com/$REPO_SLUG/refs/heads/main"

  /** Upstream project credit (GPL-3.0 attribution — keep). */
  const val UPSTREAM_NAME = "Echo Music"
  const val UPSTREAM_URL = "https://github.com/EchoMusicApp/Echo-Music"
  const val LICENSE_NAME = "GNU General Public License v3.0"
  const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

  /** Public share links use YouTube Music, which every app and browser can open. */
  const val SHARE_BASE_URL = "https://music.youtube.com"
}

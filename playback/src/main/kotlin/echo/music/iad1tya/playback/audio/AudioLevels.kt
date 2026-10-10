package echo.music.iad1tya.playback.audio

/**
 * Lock-free bridge between the audio render thread and the UI.
 *
 * [AudioLevelProcessor] writes the most recent band energies here (normalised to 0..1) for every
 * buffer it passes through; UI visualisers read them once per frame. Analysis only runs while at
 * least one visualiser has requested it via [acquire] / [release], so there is no cost when no
 * audio-reactive UI is on screen.
 */
object AudioLevels {
  /** Low band (≈ < 150 Hz): kick, bass. */
  @Volatile var bass: Float = 0f

  /** Mid band (≈ 150 Hz – 2.5 kHz): vocals, snare body, most instruments. */
  @Volatile var mid: Float = 0f

  /** High band (≈ > 2.5 kHz): hats, sibilance, air. */
  @Volatile var treble: Float = 0f

  /** Broadband loudness. */
  @Volatile var level: Float = 0f

  /** [System.nanoTime] of the last update; lets the UI decay to rest when audio stops. */
  @Volatile var lastUpdateNanos: Long = 0L

  @Volatile private var consumers: Int = 0

  val isAnalysisRequested: Boolean
    get() = consumers > 0

  @Synchronized
  fun acquire() {
    consumers++
  }

  @Synchronized
  fun release() {
    consumers = (consumers - 1).coerceAtLeast(0)
    if (consumers == 0) reset()
  }

  /** True if the levels were updated recently enough to describe what is audible now. */
  fun isFresh(nowNanos: Long = System.nanoTime(), maxAgeNanos: Long = 250_000_000L): Boolean =
    nowNanos - lastUpdateNanos < maxAgeNanos

  fun reset() {
    bass = 0f
    mid = 0f
    treble = 0f
    level = 0f
  }
}

package com.adhan.app.domain

/**
 * How an alert sounds. Null [soundUri] selects the bundled sample.
 * Add new fields at the end, with defaults.
 */
data class SoundSettings(
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    /** When true, playback continues until stop or until [ringSeconds] elapses. */
    val loop: Boolean = false,
    /** Zero rings until stop. A positive value stops playback after that many seconds. */
    val ringSeconds: Int = 0,
    /** Zero plays at full volume. Four or ten seconds fade in from silence. */
    val fadeInSeconds: Int = 0,
) {
    init {
        require(ringSeconds in 0..600) { "Ring seconds out of range: $ringSeconds" }
        require(fadeInSeconds == 0 || fadeInSeconds == 4 || fadeInSeconds == 10) {
            "Fade-in seconds out of range: $fadeInSeconds"
        }
    }
}

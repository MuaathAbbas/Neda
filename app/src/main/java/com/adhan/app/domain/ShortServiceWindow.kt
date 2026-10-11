package com.adhan.app.domain

/**
 * Android 14 `shortService` is documented to run for about three minutes.
 * A ring length of zero means "until stop", which can outlast that window.
 */
object ShortServiceWindow {
    const val LIMIT_SECONDS = 180

    fun exceedsWindow(ringSeconds: Int): Boolean {
        require(ringSeconds in 0..600) { "Ring seconds out of range: $ringSeconds" }
        return ringSeconds == 0 || ringSeconds > LIMIT_SECONDS
    }
}

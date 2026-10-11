package com.adhan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeInTest {
    @Test
    fun offStaysAtFullVolume() {
        assertEquals(1f, FadeIn.volume(elapsedMs = 0, durationMs = 0), 0f)
    }

    @Test
    fun rampsFromSilenceToFull() {
        assertEquals(0f, FadeIn.volume(elapsedMs = 0, durationMs = 4_000), 0f)
        assertEquals(0.5f, FadeIn.volume(elapsedMs = 2_000, durationMs = 4_000), 0.001f)
        assertEquals(1f, FadeIn.volume(elapsedMs = 4_000, durationMs = 4_000), 0f)
        assertEquals(1f, FadeIn.volume(elapsedMs = 9_000, durationMs = 4_000), 0f)
    }

    @Test
    fun aNegativeElapsedStaysSilent() {
        assertEquals(0f, FadeIn.volume(elapsedMs = -1, durationMs = 10_000), 0f)
    }
}

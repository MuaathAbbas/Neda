package com.adhan.app.domain

/** Linear gain from silence to full volume. Zero duration stays at full volume. */
object FadeIn {
    fun volume(elapsedMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 1f
        if (elapsedMs <= 0L) return 0f
        return (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }
}

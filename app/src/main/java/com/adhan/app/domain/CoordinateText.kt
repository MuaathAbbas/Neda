package com.adhan.app.domain

import java.util.Locale

/**
 * Parses a coordinate the user typed. Accepts a dot, a comma, or an Arabic
 * decimal separator, and Arabic-Indic digits. Empty text is not a number.
 * [format] always uses a dot so the field round-trips through [parse].
 */
object CoordinateText {
    fun format(value: Double): String = String.format(Locale.US, "%.5f", value)

    fun parse(raw: String): Double? {
        val mapped = buildString {
            for (character in raw.trim()) {
                append(
                    when (character) {
                        in '\u0660'..'\u0669' -> '0' + (character - '\u0660')
                        '\u066B', ',', '\u060C' -> '.'
                        else -> character
                    },
                )
            }
        }
        if (mapped.isEmpty()) return null
        return mapped.toDoubleOrNull()
    }
}

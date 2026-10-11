package com.adhan.app.prayer

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Madhab
import kotlin.enums.enumEntries

/**
 * Method and madhab names accepted by [AdhanPrayerEngine].
 * [OTHER] is a library sentinel with zero angles, so it is not offered.
 */
object PrayerMethods {
    const val DEFAULT_METHOD = "UMM_AL_QURA"
    const val DEFAULT_MADHAB = "SHAFI"

    val methods: List<String> = listOf(
        "UMM_AL_QURA",
        "MUSLIM_WORLD_LEAGUE",
        "EGYPTIAN",
        "KARACHI",
        "DUBAI",
        "QATAR",
        "KUWAIT",
        "MOON_SIGHTING_COMMITTEE",
        "SINGAPORE",
        "NORTH_AMERICA",
    )

    val madhabs: List<String> = listOf("SHAFI", "HANAFI")

    fun calculationMethod(name: String): CalculationMethod {
        val method = enumValueOrNull<CalculationMethod>(name)
            ?: throw IllegalArgumentException(
                "Unknown calculation method '$name'. " +
                    "Use a batoulapps adhan CalculationMethod name.",
            )
        if (method == CalculationMethod.OTHER || name !in methods) {
            throw IllegalArgumentException(
                "Calculation method '$name' is not used. Choose one of: ${methods.joinToString()}.",
            )
        }
        return method
    }

    fun madhab(name: String): Madhab {
        val madhab = enumValueOrNull<Madhab>(name)
            ?: throw IllegalArgumentException(
                "Unknown madhab '$name'. Use a batoulapps adhan Madhab name.",
            )
        if (name !in madhabs) {
            throw IllegalArgumentException(
                "Madhab '$name' is not used. Choose one of: ${madhabs.joinToString()}.",
            )
        }
        return madhab
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? {
        return enumEntries<T>().firstOrNull { it.name == name }
    }
}

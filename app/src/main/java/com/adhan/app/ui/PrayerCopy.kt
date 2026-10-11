package com.adhan.app.ui

import com.adhan.app.R
import com.adhan.app.domain.Prayer

internal object PrayerCopy {
    fun prayerName(prayer: Prayer): Int {
        return when (prayer) {
            Prayer.FAJR -> R.string.prayer_fajr
            Prayer.SUNRISE -> R.string.prayer_sunrise
            Prayer.DHUHR -> R.string.prayer_dhuhr
            Prayer.ASR -> R.string.prayer_asr
            Prayer.MAGHRIB -> R.string.prayer_maghrib
            Prayer.ISHA -> R.string.prayer_isha
        }
    }

    fun methodName(method: String): Int {
        return when (method) {
            "UMM_AL_QURA" -> R.string.method_umm_al_qura
            "MUSLIM_WORLD_LEAGUE" -> R.string.method_muslim_world_league
            "EGYPTIAN" -> R.string.method_egyptian
            "KARACHI" -> R.string.method_karachi
            "DUBAI" -> R.string.method_dubai
            "QATAR" -> R.string.method_qatar
            "KUWAIT" -> R.string.method_kuwait
            "MOON_SIGHTING_COMMITTEE" -> R.string.method_moonsighting
            "SINGAPORE" -> R.string.method_singapore
            "NORTH_AMERICA" -> R.string.method_north_america
            else -> 0
        }
    }

    fun cityName(id: String): Int {
        return when (id) {
            "makkah" -> R.string.city_makkah
            "madinah" -> R.string.city_madinah
            "riyadh" -> R.string.city_riyadh
            "jeddah" -> R.string.city_jeddah
            "cairo" -> R.string.city_cairo
            "alexandria" -> R.string.city_alexandria
            "dubai" -> R.string.city_dubai
            "doha" -> R.string.city_doha
            "kuwait" -> R.string.city_kuwait
            "amman" -> R.string.city_amman
            "jerusalem" -> R.string.city_jerusalem
            "istanbul" -> R.string.city_istanbul
            else -> 0
        }
    }

    fun madhabName(madhab: String): Int {
        return when (madhab) {
            "SHAFI" -> R.string.madhab_shafi
            "HANAFI" -> R.string.madhab_hanafi
            else -> 0
        }
    }
}

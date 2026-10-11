package com.adhan.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.adhan.app.R

/** Launcher icons the user can switch. One alias stays enabled so the app remains on the home screen. */
object LauncherIcons {
    data class Option(
        val id: String,
        val alias: String,
        val labelRes: Int,
        val buttonId: Int,
        val defaultEnabled: Boolean,
    )

    val all: List<Option> = listOf(
        Option("crescent", "com.adhan.app.icon.LauncherCrescent", R.string.icon_crescent, R.id.iconCrescent, true),
        Option("focus", "com.adhan.app.icon.LauncherFocus", R.string.icon_focus, R.id.iconFocus, false),
        Option("study", "com.adhan.app.icon.LauncherStudy", R.string.icon_study, R.id.iconStudy, false),
        Option("leaf", "com.adhan.app.icon.LauncherLeaf", R.string.icon_leaf, R.id.iconLeaf, false),
    )

    fun selectedId(context: Context): String {
        return all.firstOrNull { active(context, it) }?.id ?: all.first().id
    }

    fun apply(context: Context, id: String) {
        val chosen = all.firstOrNull { it.id == id } ?: return
        val manager = context.packageManager
        manager.setComponentEnabledSetting(
            component(context, chosen),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        all.filter { it.id != chosen.id }.forEach { option ->
            manager.setComponentEnabledSetting(
                component(context, option),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private fun active(context: Context, option: Option): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(component(context, option))
        return when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            -> false
            else -> option.defaultEnabled
        }
    }

    private fun component(context: Context, option: Option): ComponentName {
        return ComponentName(context.packageName, option.alias)
    }
}

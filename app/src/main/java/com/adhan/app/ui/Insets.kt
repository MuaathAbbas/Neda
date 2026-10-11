package com.adhan.app.ui

import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

internal fun AppCompatActivity.applySystemBarInsets(root: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
        )
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val lightBars = night != Configuration.UI_MODE_NIGHT_YES
    WindowCompat.getInsetsController(window, root).apply {
        isAppearanceLightStatusBars = lightBars
        isAppearanceLightNavigationBars = lightBars
    }
}

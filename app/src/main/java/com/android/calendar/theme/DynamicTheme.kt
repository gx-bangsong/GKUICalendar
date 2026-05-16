package com.android.calendar.theme

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.android.calendar.theme.ThemeUtils.isPureBlackModeEnabled
import com.android.calendar.theme.model.Theme
import ws.xsoh.etar.R

val AppCompatActivity.isSystemInDarkTheme: Boolean
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

@JvmOverloads
fun AppCompatActivity.applyThemeAndPrimaryColor(useColorPrimaryForStatusBar: Boolean = false) {
    val selectedTheme = ThemeUtils.getTheme(this)
    val selectedColor = ThemeUtils.getColor()

    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager

    // Handle black theme/mode
    if (selectedTheme == Theme.SYSTEM && isPureBlackModeEnabled && isSystemInDarkTheme || selectedTheme == Theme.DARK && isPureBlackModeEnabled) {
        theme.applyStyle(R.style.colorBackgroundBlack, true)
    }

    // Apply selected primary color to the theme
    theme.applyStyle(selectedColor.resource, true)

    // Set selected theme mode to the app
    when (selectedTheme) {
        Theme.SYSTEM -> {
            setSystemBarConfiguration(light = !isSystemInDarkTheme, useColorPrimaryForStatusBar = useColorPrimaryForStatusBar)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_CUSTOM)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        Theme.LIGHT -> {
            setSystemBarConfiguration(light = true, useColorPrimaryForStatusBar = useColorPrimaryForStatusBar)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        else -> {
            setSystemBarConfiguration(light = false, useColorPrimaryForStatusBar = useColorPrimaryForStatusBar)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }
}

private fun AppCompatActivity.setSystemBarConfiguration(light: Boolean, useColorPrimaryForStatusBar: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(this.window, this.window.decorView).apply {
            // Status bar color
            isAppearanceLightStatusBars = light

            // Navigation bar color
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                isAppearanceLightNavigationBars = light
            }
        }
        window.statusBarColor = if (useColorPrimaryForStatusBar) getStyledAttributeColor(androidx.appcompat.R.attr.colorPrimary) else getStyledAttributeColor(android.R.attr.colorBackground)
        window.navigationBarColor = getStyledAttributeColor(android.R.attr.colorBackground)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = true
        }
    }
}

fun Context.getStyledAttributeColor(id: Int): Int {
    val arr = obtainStyledAttributes(intArrayOf(id))
    val styledAttr = arr.getColor(0, Color.WHITE)
    arr.recycle()
    return styledAttr
}

package com.android.calendar.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.android.calendar.Utils
import com.android.calendar.theme.model.Color
import com.android.calendar.theme.model.Theme

object ThemeUtils {

    private const val THEME_PREF = "pref_theme"
    private const val PURE_BLACK_NIGHT_MODE = "pref_pure_black_night_mode"

    val Context.isPureBlackModeEnabled: Boolean
        get() = Utils.getSharedPreference(this, PURE_BLACK_NIGHT_MODE, false)

    fun getTheme(context: Context): Theme {
        val theme = Utils.getSharedPreference(
            context,
            THEME_PREF,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Theme.SYSTEM.name else Theme.LIGHT.name
        )
        return Theme.valueOf(theme.uppercase())
    }

    /** Key of the theme the screen is using right now; compared with the key taken at creation. */
    fun effectiveThemeKey(context: Context): String = ThemeRules.effectiveKey(
        getTheme(context),
        context.isPureBlackModeEnabled,
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES,
        getColor().name
    )

    fun getColor(): Color {
        val color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Color.MONET.name else Color.TEAL.name
        return Color.valueOf(color.uppercase())
    }
}

package com.android.calendar.shift

object ShiftUtils {
    private val TITLE_REGEX = Regex("^(早班|中班|晚班|夜班|白班|Shift).*$")

    fun formatTitle(title: String): String {
        return if (TITLE_REGEX.matches(title)) {
            title
        } else {
            "Shift: $title"
        }
    }

    fun formatDescription(alarmOffset: Int, ignoreHoliday: Boolean): String {
        val metadata = StringBuilder()
        metadata.append("Alarm: -$alarmOffset")
        if (ignoreHoliday) {
            metadata.append("\n#IgnoreHoliday")
        }
        return metadata.toString()
    }
}

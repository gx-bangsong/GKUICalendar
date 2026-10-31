/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription.birthday.data

/**
 * One 农历生日 entry: a person's name plus the lunar month/day they were
 * born on. Leap months are treated as their non-leap counterpart, which is
 * the convention most Chinese calendars use for birthdays.
 */
data class LunarBirthday(
    val name: String,
    /** 1..12 */
    val lunarMonth: Int,
    /** 1..30 */
    val lunarDay: Int
) {
    fun serialize(): String = "$lunarMonth|$lunarDay|${name.replace("\n", " ")}"

    companion object {
        const val SEP = "\n"

        @JvmStatic
        fun parse(raw: String): LunarBirthday? {
            val parts = raw.split("|", limit = 3)
            if (parts.size < 3) return null
            val m = parts[0].toIntOrNull() ?: return null
            val d = parts[1].toIntOrNull() ?: return null
            if (m !in 1..12 || d !in 1..30) return null
            val name = parts[2].trim()
            if (name.isEmpty()) return null
            return LunarBirthday(name, m, d)
        }

        @JvmStatic
        fun parseAll(raw: String?): List<LunarBirthday> {
            if (raw.isNullOrEmpty()) return emptyList()
            val out = ArrayList<LunarBirthday>()
            for (line in raw.split(SEP)) {
                if (line.isEmpty()) continue
                val b = parse(line)
                if (b != null) out.add(b)
            }
            return out
        }

        @JvmStatic
        fun serializeAll(items: List<LunarBirthday>): String {
            val sb = StringBuilder()
            for (i in items.indices) {
                if (i > 0) sb.append(SEP)
                sb.append(items[i].serialize())
            }
            return sb.toString()
        }
    }
}

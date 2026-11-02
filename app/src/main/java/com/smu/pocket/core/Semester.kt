package com.smu.pocket.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 学期 / 周次换算。纯函数，无依赖，单测覆盖（SemesterTest）。
 *
 * 三条规则来自真实踩坑：
 * 1. 用 floorDiv 而不是整除，否则开学前的日期会算成第 0 周却显示成第 1 周；
 * 2. 周次必须能钳住（寒暑假期间 today 会算出 25 周，不能直接拿去查）；
 * 3. 周次串格式各家不同（"1-16" / "1,3,5" / "1-16单周" / "第3周"），必须容错。
 */
object Semester {

    /** 日期属于第几周。开学前返回 ≤ 0 */
    fun weekOf(date: LocalDate, firstMonday: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(firstMonday, date)
        return Math.floorDiv(days, 7L).toInt() + 1
    }

    fun clampWeek(week: Int, totalWeeks: Int): Int =
        if (totalWeeks <= 0) 1 else week.coerceIn(1, totalWeeks)

    fun mondayOfWeek(week: Int, firstMonday: LocalDate): LocalDate =
        firstMonday.plusWeeks((week - 1).toLong())

    fun datesOfWeek(week: Int, firstMonday: LocalDate): List<LocalDate> {
        val monday = mondayOfWeek(week, firstMonday)
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    /**
     * 把教务返回的周次串解析成集合。在拿到真实字段之前先用它兜底，
     * 拿到真实串之后再按实际格式补测试用例。
     */
    fun parseWeeks(raw: String, totalWeeks: Int = 30): Set<Int> {
        if (raw.isBlank()) return emptySet()
        val text = raw
            .replace('，', ',')
            .replace('、', ',')
            .replace('；', ',')
            .replace('（', '(')
            .replace('）', ')')
            .replace(" ", "")

        val out = linkedSetOf<Int>()
        for (token in text.split(',')) {
            if (token.isEmpty()) continue
            val oddOnly = '单' in token
            val evenOnly = '双' in token
            val digits = token.filter { it.isDigit() || it == '-' }
            if (digits.isEmpty()) continue

            val dash = digits.indexOf('-')
            val from: Int
            val to: Int
            if (dash >= 0) {
                from = digits.substring(0, dash).toIntOrNull() ?: continue
                to = digits.substring(dash + 1).toIntOrNull() ?: continue
            } else {
                from = digits.toIntOrNull() ?: continue
                to = from
            }
            if (from <= 0 || to < from) continue

            for (w in from..to) {
                if (w > totalWeeks) continue
                if (oddOnly && w % 2 == 0) continue
                if (evenOnly && w % 2 != 0) continue
                out += w
            }
        }
        return out
    }
}

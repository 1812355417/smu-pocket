package com.smu.pocket.core

import com.smu.pocket.data.model.CourseData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 时间工具：从课表数据反推学期起点，再换算周次和请求参数。
 *
 * 背景：南医大**没有独立的校历接口**，但按周查询的课表里带 `qsrq`（真实日期）。
 * 于是「第一周周一」可以从课表本身反推出来，不需要用户手填，也不需要校历。
 *
 * 反推公式（用你给的两条真实记录验证过）：
 * ```
 * 该周的周一   = qsrq - (xq - 1) 天
 * 第一周周一   = 该周的周一 - (zc - 1) 周
 * ```
 * - 形势与政策：qsrq=2026-10-23(周五), xq=5, zc=8 → 周一 2026-10-19 → 第一周 2026-08-31
 * - 临床诊断学：qsrq=2026-10-08(周四), xq=4, zc=6 → 周一 2026-10-05 → 第一周 2026-08-31
 *
 * 两条独立记录得同一个答案，说明公式和数据都对。
 */
object TimeUtils {

    private val DATE_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private const val TIME_SUFFIX = " 00:00:00"

    // ---------------------------------------------------------------------
    // 日期解析 / 格式化
    // ---------------------------------------------------------------------

    /** 兼容 "2026-10-23" 和 "2026-10-23 00:00:00" 两种写法 */
    fun parseDate(raw: String?): LocalDate? {
        val text = raw?.trim().orEmpty()
        if (text.length < 10) return null
        return runCatching { LocalDate.parse(text.take(10)) }.getOrNull()
    }

    /** 教务要求的参数格式："2026-10-26 00:00:00" */
    fun formatPayload(date: LocalDate): String = date.format(DATE_ONLY) + TIME_SUFFIX

    // ---------------------------------------------------------------------
    // 周次
    // ---------------------------------------------------------------------

    /**
     * 解析周次字段。两种形态都吃：
     * - 整学期总表：`"12,13"`、`"13,10,15,9,4"`（一串）
     * - 按周查询：`"8"`（单个）
     *
     * 顺带兼容 `"1-16"`、`"1-16单"` 这类写法（[Semester.parseWeeks] 已实现）。
     */
    fun parseWeekList(raw: String?, totalWeeks: Int = 30): Set<Int> =
        Semester.parseWeeks(raw.orEmpty(), totalWeeks)

    /**
     * 反推第一周周一。
     *
     * 实现上没有「只取 zc 最小的那一条」，而是**让每条记录投票**：
     * 同一学期里几十条记录理论上都指向同一个日期，取众数比赌单条记录稳。
     * （单条记录可能是临时调课、数据录入错误，或者 zc 是多周串算错了周。）
     *
     * @param queriedWeek 调用方**自己知道**的周次。实测按日期查询时，响应里
     *   可能根本没有 `zc` 字段，这时只能靠这个参数把「这是第几周」补上；
     *   不传且数据里也没有 zc 的话，返回 null（不要瞎猜）。
     *
     * 返回 null 表示反推不出来 —— 通常是拿到的是整学期总表（既没 `qsrq` 也没 `zc`）。
     */
    fun inferFirstMonday(records: List<CourseData>, queriedWeek: Int? = null): LocalDate? {
        val votes = mutableMapOf<LocalDate, Int>()

        for (record in records) {
            val date = parseDate(record.qsrq) ?: continue
            val day = record.xq
            if (day !in 1..7) continue

            val mondayOfThatWeek = date.minusDays((day - 1).toLong())

            val weeksFromData = parseWeekList(record.zc)
            val weeks: Set<Int> = when {
                weeksFromData.isNotEmpty() -> weeksFromData
                queriedWeek != null -> setOf(queriedWeek)
                else -> emptySet()
            }

            for (week in weeks) {
                val candidate = mondayOfThatWeek.minusWeeks((week - 1).toLong())
                votes[candidate] = (votes[candidate] ?: 0) + 1
            }
        }

        return votes.maxByOrNull { it.value }?.key
    }

    /** 当前是第几周。开学前返回 ≤ 0，调用方自己决定要不要钳住 */
    fun currentWeek(today: LocalDate, firstMonday: LocalDate): Int =
        Semester.weekOf(today, firstMonday)

    /** 第 [week] 周的周一到周日 */
    fun weekBounds(week: Int, firstMonday: LocalDate): WeekBounds {
        val monday = firstMonday.plusWeeks((week - 1).toLong())
        return WeekBounds(
            monday = monday,
            sunday = monday.plusDays(6),
            nextMonday = monday.plusDays(7),
        )
    }

    // ---------------------------------------------------------------------
    // 节次
    // ---------------------------------------------------------------------

    /**
     * 解析节次串 `"0809"` / `"060708"` → 8..9 / 6..8。
     *
     * 每两位一节。奇数长度直接放弃（宁可返回 null 让上层兜底，也不猜出个错范围）。
     */
    fun parseJcdm(jcdm: String?): IntRange? {
        val raw = jcdm?.trim().orEmpty()
        if (raw.isEmpty() || raw.length % 2 != 0 || !raw.all { it.isDigit() }) return null

        val numbers = raw.chunked(2).mapNotNull { it.toIntOrNull() }
        if (numbers.isEmpty()) return null
        return numbers.min()..numbers.max()
    }

    /**
     * 统一取节次：优先 `jcdm`（按周数据），退回 `ps`/`pe`（整学期总表）。
     * 两条查询路径的字段不一样，这层封装让 UI 不用关心走的是哪条。
     */
    fun sectionsOf(record: CourseData): IntRange? {
        parseJcdm(record.jcdm)?.let { return it }

        val start = record.ps?.trim()?.toIntOrNull() ?: return null
        val end = record.pe?.trim()?.toIntOrNull() ?: start
        return if (end >= start) start..end else start..start
    }

    /** "09:50-11:15"，比节次更直观，用于课程卡片副标题 */
    fun clockRange(record: CourseData): String? {
        val start = record.qssj?.take(5)?.takeIf { it.length == 5 } ?: return null
        val end = record.jssj?.take(5)?.takeIf { it.length == 5 } ?: return null
        return "$start-$end"
    }
}

/**
 * 一周的边界。
 *
 * `d2` 到底该传周日还是下周一，你抓包里给的是**下周一**：
 * `d1=2026-10-26 00:00:00`、`d2=2026-11-02 00:00:00`（正好差 7 天）。
 * 所以两个都给出来，实测哪个对就用哪个（见 [d2NextMonday] / [d2Sunday]）。
 */
data class WeekBounds(
    val monday: LocalDate,
    val sunday: LocalDate,
    val nextMonday: LocalDate,
) {
    /** 周一，用作 d1 */
    val d1: String get() = TimeUtils.formatPayload(monday)

    /** 周日，用作 d2（若服务端按「左闭右闭」解析） */
    val d2Sunday: String get() = TimeUtils.formatPayload(sunday)

    /** 下周一，用作 d2（你抓包里就是这个，若服务端按「左闭右开」解析） */
    val d2NextMonday: String get() = TimeUtils.formatPayload(nextMonday)

    val label: String
        get() = "${monday.monthValue}/${monday.dayOfMonth} ~ ${sunday.monthValue}/${sunday.dayOfMonth}"
}

/** 人类可读的相对时间："刚刚 / 12 分钟前 / 3 小时前 / 2 天前" */
fun humanizeAgo(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000L
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        else -> "${minutes / (60 * 24)} 天前"
    }
}

/**
 * 由「第一周周一 + 第几周 + 星期几」算出具体日期。
 *
 * 整学期总表（`zc=` 空）里**没有 `qsrq`**，但每行都有 `zc`（单周）和 `xq`，
 * 所以考试这类「只在某一周出现一次」的记录可以精确还原出日期。
 */
fun dateOf(firstMonday: LocalDate, week: Int, dayOfWeek: Int): LocalDate =
    firstMonday.plusWeeks((week - 1).toLong()).plusDays((dayOfWeek - 1).toLong())

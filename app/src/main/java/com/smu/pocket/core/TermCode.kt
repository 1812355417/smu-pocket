package com.smu.pocket.core

import java.time.LocalDate

/**
 * 学期代码（`xnxqdm`）的换算与推算。
 *
 * 规则由真实数据反推（见 smu-probe/字段字典-自动生成.md）：
 *
 * ```
 * 代码 = 学年起始年 + "0" + 学期序号
 *
 * 202601 → 2026-2027 学年 第 1 学期
 * 202502 → 2025-2026 学年 第 2 学期
 * ```
 *
 * 注意：第 2 学期的代码年份是**学年起始年**，不是结束年
 * （202502 对应 2025-2026 的第 2 学期，不是 2024-2025）。
 *
 * ## 为什么要「推算」
 *
 * 这个教务系统**没有提供查询当前学期的接口**：
 * `getXnxqList` / `getXnxq` / `getCalendar` 等候选路径全部 404，
 * 而成绩页 HTML 未登录时返回的只是登录页。好在学期边界非常规律
 * （9 月开学），按日期推算的结果与教务一致，且完全不依赖网络。
 */
object TermCode {

    /** 兜底学期：所有手段都失败时用它 */
    const val FALLBACK = "202601"

    private val SHORT = Regex("""^\d{6}$""")
    private val ACADEMIC_YEAR = Regex("""(\d{4})\s*-\s*(\d{4})""")
    private val HALF_CHINESE = Regex("""第\s*([12])\s*学期""")
    private val HALF_TRAILING = Regex("""-\s*([12])\s*$""")

    /**
     * "2026-2027-1"、"2026-2027 学年 第 1 学期"、"2025-2026-2" → 短代码。
     * 认不出来的返回 null（不会瞎猜）。
     */
    fun fromDisplayName(name: String?): String? {
        val text = name?.trim().orEmpty()
        if (text.isEmpty()) return null

        val startYear = ACADEMIC_YEAR.find(text)?.groupValues?.get(1) ?: return null
        val half = HALF_CHINESE.find(text)?.groupValues?.get(1)
            ?: HALF_TRAILING.find(text)?.groupValues?.get(1)
            ?: return null

        return "$startYear" + "0" + half
    }

    /** "202601" → "2026-2027 学年 第 1 学期" */
    fun displayName(code: String): String {
        val start = code.take(4).toIntOrNull() ?: return code
        val half = code.drop(4).toIntOrNull() ?: return code
        return "$start-${start + 1} 学年 第 ${if (half == 2) 2 else 1} 学期"
    }

    /**
     * 按日期推算当前学期代码。学年 9 月开学：
     *
     * | 月份 | 学年起始年 | 学期 |
     * |---|---|---|
     * | 9 ~ 12 | 当年 | 第 1 学期 |
     * | 1 | 上一年 | 第 1 学期 |
     * | 2 ~ 8 | 上一年 | 第 2 学期 |
     */
    fun fromDate(date: LocalDate = LocalDate.now()): String {
        val month = date.monthValue
        val startYear = if (month >= 9) date.year else date.year - 1
        val half = if (month in 2..8) 2 else 1
        return "$startYear" + "0" + half
    }

    fun isValid(code: String?): Boolean = code != null && SHORT.matches(code)

    /**
     * 这个代码像不像「真的学期」。
     *
     * 起因：教务页面的 `<select>` 里混着非学期的六位数字（实测抓到 `203402`），
     * 天真的「取最大六位数」会把它们当成学期，导致所有请求用错 `xnxqdm`、返回空数据。
     *
     * 规则：学期号只能是 1 或 2，且学年起始年离今天不超过 ±1 年。
     */
    fun isPlausible(code: String?, today: LocalDate = LocalDate.now()): Boolean {
        if (!isValid(code)) return false
        val startYear = code!!.take(4).toIntOrNull() ?: return false
        val half = code.drop(4).toIntOrNull() ?: return false
        if (half != 1 && half != 2) return false
        return kotlin.math.abs(startYear - today.year) <= 1
    }
}

package com.smu.pocket

import com.smu.pocket.core.TermCode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学期代码换算、推算与合理性校验。
 *
 * 表里的 202601 / 202502 来自真实抓包对照（见 smu-probe/字段字典-自动生成.md），
 * 203402 来自教务页面下拉里的真实取值 —— 它是个**未来学期**，
 * 说明「下拉里最大的那个」根本不是当前学期（这正是线上那个 bug 的成因）。
 */
class TermCodeTest {

    // ---------- 长串 → 短代码 ----------

    @Test
    fun convertsDashDisplayName() {
        assertEquals("202601", TermCode.fromDisplayName("2026-2027-1"))
        assertEquals("202502", TermCode.fromDisplayName("2025-2026-2"))
    }

    @Test
    fun convertsChineseDisplayName() {
        assertEquals("202601", TermCode.fromDisplayName("2026-2027 学年 第 1 学期"))
        assertEquals("202502", TermCode.fromDisplayName("2025-2026 学年 第 2 学期"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(TermCode.fromDisplayName(null))
        assertNull(TermCode.fromDisplayName(""))
        assertNull(TermCode.fromDisplayName("2026-2027"))   // 没有学期号
        assertNull(TermCode.fromDisplayName("第 1 学期"))    // 没有学年
    }

    // ---------- 短代码 → 长串 ----------

    @Test
    fun expandsShortCode() {
        assertEquals("2026-2027 学年 第 1 学期", TermCode.displayName("202601"))
        assertEquals("2025-2026 学年 第 2 学期", TermCode.displayName("202502"))
    }

    @Test
    fun roundTrip() {
        listOf("202601", "202502", "202402", "202701").forEach { code ->
            assertEquals(code, TermCode.fromDisplayName(TermCode.displayName(code)))
        }
    }

    // ---------- 按日期推算 ----------

    @Test
    fun autumnIsFirstTerm() {
        assertEquals("202601", TermCode.fromDate(LocalDate.of(2026, 9, 1)))
        assertEquals("202601", TermCode.fromDate(LocalDate.of(2026, 11, 2)))
        assertEquals("202601", TermCode.fromDate(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun januaryStillBelongsToFirstTerm() {
        assertEquals("202601", TermCode.fromDate(LocalDate.of(2027, 1, 15)))
        assertEquals("202701", TermCode.fromDate(LocalDate.of(2028, 1, 1)))
    }

    @Test
    fun springIsSecondTerm() {
        assertEquals("202602", TermCode.fromDate(LocalDate.of(2027, 2, 20)))
        assertEquals("202602", TermCode.fromDate(LocalDate.of(2027, 3, 1)))
        assertEquals("202602", TermCode.fromDate(LocalDate.of(2027, 8, 31)))
    }

    @Test
    fun computedCodeIsAlwaysPlausible() {
        var d = LocalDate.of(2025, 1, 1)
        while (d.year < 2032) {
            val code = TermCode.fromDate(d)
            assertTrue("$d 推算出 $code，但被判为不合理", TermCode.isPlausible(code, d))
            val startYear = TermCode.displayName(code).take(4).toInt()
            assertTrue("$d → $code 的学年不覆盖当年", d.year >= startYear && d.year <= startYear + 1)
            d = d.plusDays(17)
        }
    }

    // ---------- 合理性校验（回归：页面上限 203402 不是当前学期）----------

    @Test
    fun acceptsNormalTerms() {
        val today = LocalDate.of(2026, 11, 2)
        assertTrue(TermCode.isPlausible("202601", today))
        assertTrue(TermCode.isPlausible("202502", today))
        assertTrue(TermCode.isPlausible("202602", today))
        assertTrue(TermCode.isPlausible("202702", today))   // 跨一年，仍算合理
    }

    @Test
    fun rejectsFarFutureAndMalformed() {
        val today = LocalDate.of(2026, 11, 2)
        // 教务页面下拉里真的有 203402（2034-2035 第 2 学期），绝不能当当前学期
        assertFalse(TermCode.isPlausible("203402", today))
        assertFalse(TermCode.isPlausible("201801", today))
        assertFalse(TermCode.isPlausible("202603", today))   // 学期号只能是 1/2
        assertFalse(TermCode.isPlausible("202600", today))
        assertFalse(TermCode.isPlausible("2026", today))
        assertFalse(TermCode.isPlausible(null, today))
    }
}

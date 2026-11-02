package com.smu.pocket

import com.smu.pocket.core.TermCode
import com.smu.pocket.data.TermResolver
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学期代码换算与推算。
 *
 * 期望值来自真实数据：`xnxqdm=202502` ↔ `xnxqmc="2025-2026-2"`
 * （见 smu-probe/字段字典-自动生成.md）。
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
        assertNull(TermCode.fromDisplayName("2026-2027"))       // 没有学期号
        assertNull(TermCode.fromDisplayName("第 1 学期"))        // 没有学年
    }

    // ---------- 短代码 → 长串 ----------

    @Test
    fun expandsShortCode() {
        assertEquals("2026-2027 学年 第 1 学期", TermCode.displayName("202601"))
        // 第 2 学期的年份是学年起始年，不是结束年
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
    fun computedCodeIsAlwaysWellFormed() {
        var d = LocalDate.of(2025, 1, 1)
        while (d.year < 2032) {
            val code = TermCode.fromDate(d)
            assertTrue("$d 推算出非法代码 $code", TermCode.isValid(code))
            val startYear = TermCode.displayName(code).take(4).toInt()
            assertTrue("$d → $code 的学年不覆盖当年", d.year >= startYear && d.year <= startYear + 1)
            d = d.plusDays(17)
        }
    }

    // ---------- 从教务页面 HTML 里抠学期 ----------

    @Test
    fun parsesSelectedOption() {
        val html = """
            <select id="xnxqdm" name="xnxqdm">
              <option value="202502">2025-2026学年第二学期</option>
              <option value="202601" selected>2026-2027学年第一学期</option>
            </select>
        """.trimIndent()
        assertEquals("202601", TermResolver.parseTerm(html))
    }

    @Test
    fun parsesSelectedWhenSelectedComesFirst() {
        assertEquals("202601", TermResolver.parseTerm("""<option selected value='202601'>第一学期</option>"""))
    }

    @Test
    fun fallsBackToLargestWhenNothingSelected() {
        val html = """
            <option value="202502">上学期</option>
            <option value="202601">本学期</option>
        """.trimIndent()
        assertEquals("202601", TermResolver.parseTerm(html))
    }

    @Test
    fun returnsNullForLoginPage() {
        val loginPage = """
            <!DOCTYPE html><html><head><title>登录</title></head>
            <body><div class="login"></div></body></html>
        """.trimIndent()
        assertNull(TermResolver.parseTerm(loginPage))
        assertNull(TermResolver.parseTerm(""))
    }
}

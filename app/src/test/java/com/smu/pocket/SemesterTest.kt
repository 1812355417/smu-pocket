package com.smu.pocket

import com.smu.pocket.core.Semester
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 周次换算是课表的地基，错了整张表都错位。
 * 这些用例对应真实踩坑：开学前算出第 0 周、寒暑假算出第 25 周、单双周解析错。
 */
class SemesterTest {

    private val firstMonday = LocalDate.of(2025, 9, 1) // 第 1 周周一

    @Test
    fun firstMondayIsWeek1() {
        assertEquals(1, Semester.weekOf(firstMonday, firstMonday))
    }

    @Test
    fun sundayBelongsToSameWeek() {
        assertEquals(1, Semester.weekOf(LocalDate.of(2025, 9, 7), firstMonday))
    }

    @Test
    fun nextMondayIsWeek2() {
        assertEquals(2, Semester.weekOf(LocalDate.of(2025, 9, 8), firstMonday))
    }

    @Test
    fun lastSundayOfWeek8() {
        assertEquals(8, Semester.weekOf(LocalDate.of(2025, 10, 26), firstMonday))
    }

    @Test
    fun beforeTermIsNotWeek1() {
        val w = Semester.weekOf(LocalDate.of(2025, 8, 25), firstMonday)
        assertTrue("开学前不能算成第 1 周，实际=$w", w <= 0)
    }

    @Test
    fun clampKeepsInsideRange() {
        assertEquals(20, Semester.clampWeek(25, 20))
        assertEquals(1, Semester.clampWeek(0, 20))
        assertEquals(1, Semester.clampWeek(3, 0)) // 总周数未知时的兜底
    }

    @Test
    fun datesOfWeekStartsOnMonday() {
        val dates = Semester.datesOfWeek(3, firstMonday)
        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2025, 9, 15), dates.first())
        assertEquals(LocalDate.of(2025, 9, 21), dates.last())
    }

    @Test
    fun parseSimpleRange() {
        assertEquals((1..16).toSet(), Semester.parseWeeks("1-16"))
    }

    @Test
    fun parseCommaList() {
        assertEquals(setOf(1, 3, 5, 7), Semester.parseWeeks("1,3,5,7"))
    }

    @Test
    fun parseOddWeeks() {
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), Semester.parseWeeks("1-16(单)"))
    }

    @Test
    fun parseEvenWeeks() {
        assertEquals(setOf(2, 4, 6, 8, 10, 12, 14, 16), Semester.parseWeeks("2-16双"))
    }

    @Test
    fun parseSingleWeekWithChinesePrefix() {
        assertEquals(setOf(3), Semester.parseWeeks("第3周"))
    }

    @Test
    fun parseMixesFullWidthComma() {
        assertEquals(setOf(2, 4), Semester.parseWeeks("2，4"))
    }

    @Test
    fun parseRespectsTotalWeeks() {
        assertEquals((1..16).toSet(), Semester.parseWeeks("1-20", totalWeeks = 16))
    }

    @Test
    fun parseBlankIsEmpty() {
        assertTrue(Semester.parseWeeks("").isEmpty())
        assertTrue(Semester.parseWeeks("   ").isEmpty())
    }
}

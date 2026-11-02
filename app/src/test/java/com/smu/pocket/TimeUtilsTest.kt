package com.smu.pocket

import com.smu.pocket.core.TimeUtils
import com.smu.pocket.data.model.CourseData
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 时间换算的单测。
 *
 * 用例里的日期**全部来自真实抓包**（qsrq / zc / xq 都是教务返回的原值），
 * 所以这些断言不只是测代码，也是在测「我对教务数据的理解」对不对。
 */
class TimeUtilsTest {

    private val firstMonday = LocalDate.of(2026, 8, 31)

    /** 按周查询的形态：有 qsrq、zc 是数字、有 jcdm */
    private val weekRows = listOf(
        CourseData(
            kcmc = "形势与政策", zc = "8", xq = 5,
            qsrq = "2026-10-23", jcdm = "0809", ps = "08", sknrjj = "形势与政策专题二",
        ),
        CourseData(
            kcmc = "临床诊断学", zc = "6", xq = 4,
            qsrq = "2026-10-08", jcdm = "060708", ps = "06", jxcdmc = "304机房",
        ),
    )

    // ------------------------------------------------------------------
    // 反推第一周周一
    // ------------------------------------------------------------------

    @Test
    fun inferFirstMondayFromTwoIndependentRecords() {
        // 两条记录分别从第 8 周和第 6 周反推，都该得到 2026-08-31
        assertEquals(firstMonday, TimeUtils.inferFirstMonday(weekRows))
    }

    @Test
    fun inferFirstMondaySingleRecord() {
        assertEquals(firstMonday, TimeUtils.inferFirstMonday(listOf(weekRows[0])))
        assertEquals(firstMonday, TimeUtils.inferFirstMonday(listOf(weekRows[1])))
    }

    @Test
    fun inferFirstMondayReturnsNullWithoutDates() {
        // 整学期总表没有 qsrq —— 反推不出来时必须返回 null，
        // 绝不能拿「今天」当第一周，那样整张课表会静默错位
        val termRows = listOf(
            CourseData(kcmc = "形势与政策", zc = "12,13", xq = 5, ps = "03", pe = "04"),
        )
        assertNull(TimeUtils.inferFirstMonday(termRows))
    }

    @Test
    fun inferFirstMondayIgnoresRowsWithBrokenDay() {
        val broken = listOf(CourseData(kcmc = "x", zc = "8", xq = 0, qsrq = "2026-10-23"))
        assertNull(TimeUtils.inferFirstMonday(broken))
    }

    @Test
    fun inferFirstMondayWithoutZcUsesQueriedWeek() {
        // 实测：按日期查询的响应里没有 zc。此时「第几周」只能靠调用方自己传
        val rows = listOf(
            CourseData(kcmc = "形势与政策", zc = "", xq = 2, qsrq = "2026-10-27"),
            CourseData(kcmc = "临床诊断学", zc = "", xq = 4, qsrq = "2026-10-29"),
        )

        assertEquals(firstMonday, TimeUtils.inferFirstMonday(rows, queriedWeek = 9))

        // 既没有 zc 又没告诉它是第几周 → 返回 null，绝不瞎猜
        assertNull(TimeUtils.inferFirstMonday(rows))
    }

    // ------------------------------------------------------------------
    // 周次
    // ------------------------------------------------------------------

    @Test
    fun currentWeek() {
        assertEquals(1, TimeUtils.currentWeek(firstMonday, firstMonday))
        assertEquals(8, TimeUtils.currentWeek(LocalDate.of(2026, 10, 19), firstMonday))
        assertEquals(9, TimeUtils.currentWeek(LocalDate.of(2026, 10, 26), firstMonday))
        // 开学前是 0，不是 1
        assertEquals(0, TimeUtils.currentWeek(LocalDate.of(2026, 8, 24), firstMonday))
    }

    @Test
    fun currentWeekMatchesKnownRecord() {
        // 2026-10-23 那条记录 zc=8，所以这天必须落在第 8 周
        val date = TimeUtils.parseDate("2026-10-23")!!
        assertEquals(8, TimeUtils.currentWeek(date, firstMonday))
    }

    @Test
    fun parseWeekListHandlesBothShapes() {
        assertEquals(setOf(8), TimeUtils.parseWeekList("8"))
        assertEquals(setOf(12, 13), TimeUtils.parseWeekList("12,13"))
        assertEquals(setOf(4, 9, 10, 13, 15), TimeUtils.parseWeekList("13,10,15,9,4"))
        assertEquals(emptySet<Int>(), TimeUtils.parseWeekList(null))
    }

    // ------------------------------------------------------------------
    // 请求参数 d1 / d2
    // ------------------------------------------------------------------

    @Test
    fun weekBoundsMatchesCapturedPayload() {
        val bounds = TimeUtils.weekBounds(9, firstMonday)

        // 你抓到的 payload 正是 d1=2026-10-26 00:00:00, d2=2026-11-02 00:00:00
        assertEquals("2026-10-26 00:00:00", bounds.d1)
        assertEquals("2026-11-02 00:00:00", bounds.d2NextMonday)
    }

    @Test
    fun weekBoundsSundayVariant() {
        val bounds = TimeUtils.weekBounds(9, firstMonday)
        assertEquals("2026-11-01 00:00:00", bounds.d2Sunday)
        assertEquals("10/26 ~ 11/1", bounds.label)
    }

    @Test
    fun weekBoundsForFirstWeek() {
        val bounds = TimeUtils.weekBounds(1, firstMonday)
        assertEquals("2026-08-31 00:00:00", bounds.d1)
        assertEquals("2026-09-06 00:00:00", bounds.d2Sunday)
    }

    // ------------------------------------------------------------------
    // 节次
    // ------------------------------------------------------------------

    @Test
    fun parseJcdm() {
        assertEquals(8..9, TimeUtils.parseJcdm("0809"))
        assertEquals(6..8, TimeUtils.parseJcdm("060708"))
        assertNull(TimeUtils.parseJcdm("080"))   // 奇数长度：宁可放弃，不猜
        assertNull(TimeUtils.parseJcdm(""))
        assertNull(TimeUtils.parseJcdm(null))
    }

    @Test
    fun sectionsPreferJcdmOverPsPe() {
        val withBoth = CourseData(xq = 5, zc = "8", jcdm = "0809", ps = "06", pe = "07")
        assertEquals(8..9, TimeUtils.sectionsOf(withBoth))

        val onlyPsPe = CourseData(xq = 2, zc = "4", ps = "03", pe = "04")
        assertEquals(3..4, TimeUtils.sectionsOf(onlyPsPe))

        val onlyPs = CourseData(xq = 2, zc = "4", ps = "08")
        assertEquals(8..8, TimeUtils.sectionsOf(onlyPs))

        assertNull(TimeUtils.sectionsOf(CourseData(xq = 2, zc = "4")))
    }

    @Test
    fun clockRange() {
        val record = CourseData(xq = 1, zc = "1", qssj = "09:50:00", jssj = "11:15:00")
        assertEquals("09:50-11:15", TimeUtils.clockRange(record))
    }

    // ------------------------------------------------------------------
    // 日期解析
    // ------------------------------------------------------------------

    @Test
    fun parseDateToleratesTimeSuffix() {
        assertEquals(LocalDate.of(2026, 10, 23), TimeUtils.parseDate("2026-10-23"))
        assertEquals(LocalDate.of(2026, 10, 23), TimeUtils.parseDate("2026-10-23 00:00:00"))
        assertNull(TimeUtils.parseDate(null))
        assertNull(TimeUtils.parseDate(""))
        assertNull(TimeUtils.parseDate("2026/10/23"))
    }
}

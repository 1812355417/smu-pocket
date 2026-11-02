package com.smu.pocket

import com.smu.pocket.core.TimeUtils
import com.smu.pocket.data.model.JwJson
import com.smu.pocket.data.model.toCourse
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用**真实抓包脱敏后**的 fixture 测解析。
 *
 * fixture 来源：
 * - `timetable_term.json`：整学期总表，取真实 118 行里的前 30 行，姓名/工号已脱敏
 * - `timetable_week.json`：按周查询，你贴的 2 条真实记录
 * - `scores_page1.json`：成绩第 1 页，真实 20 行，学号姓名已脱敏
 */
class JwJsonTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("缺少 fixture: $name")

    // ------------------------------------------------------------------
    // 课表：整学期总表
    // ------------------------------------------------------------------

    @Test
    fun parsesTermTimetableWrapper() {
        val resp = JwJson.timetable(fixture("timetable_term.json"))

        assertEquals(0, resp.code)
        assertTrue(resp.ok)
        assertEquals(30, resp.data.size)
        assertTrue("每一行都该有星期", resp.data.all { it.xq in 1..7 })
        assertTrue("每一行都该有周次", resp.data.all { it.zc.isNotBlank() })
    }

    @Test
    fun termTimetableHasMultiWeekStrings() {
        val resp = JwJson.timetable(fixture("timetable_term.json"))
        val multiWeek = resp.data.filter { it.zc.contains(",") }

        assertTrue("整学期总表里应该有多周串（zc 是字符串不是数字）", multiWeek.isNotEmpty())
        assertTrue(TimeUtils.parseWeekList(multiWeek.first().zc).size > 1)
    }

    @Test
    fun termTimetableHasNoDates() {
        val resp = JwJson.timetable(fixture("timetable_term.json"))
        // 这是关键事实：整学期总表不返回 qsrq，所以反推第一周周一必须先发一次按周查询
        assertTrue(resp.data.all { it.qsrq == null })
        assertTrue(TimeUtils.inferFirstMonday(resp.data) == null)
    }

    @Test
    fun mapsTermTimetableToDomain() {
        val resp = JwJson.timetable(fixture("timetable_term.json"))
        val courses = resp.data.mapNotNull { it.toCourse() }

        assertEquals("30 行都该能映射成功", 30, courses.size)
        assertTrue(courses.all { it.weeks.isNotEmpty() })
        assertTrue(courses.all { it.dayOfWeek in 1..7 })
        assertTrue(courses.all { it.startSection in 1..20 && it.endSection >= it.startSection })
        assertTrue("教室字段有 25 行是空串，不能变成空字符串进 UI", courses.none { it.room == "" })
    }

    // ------------------------------------------------------------------
    // 课表：按周查询
    // ------------------------------------------------------------------

    @Test
    fun parsesWeekTimetable() {
        val resp = JwJson.timetable(fixture("timetable_week.json"))

        assertEquals(2, resp.data.size)
        assertEquals("形势与政策专题二", resp.data[0].sknrjj)
        assertEquals("2026-10-23", resp.data[0].qsrq)
        assertEquals("8", resp.data[0].zc)      // zc = 第 8 周
        assertEquals(5, resp.data[0].xq)        // xq = 星期五（2026-10-23 是周五）
        assertEquals(8..9, TimeUtils.sectionsOf(resp.data[0]))
    }

    @Test
    fun weekTimetableYieldsFirstMonday() {
        val resp = JwJson.timetable(fixture("timetable_week.json"))
        assertEquals(LocalDate.of(2026, 8, 31), TimeUtils.inferFirstMonday(resp.data))
    }

    @Test
    fun numericZcIsCoercedToString() {
        // fixture 里 zc 是数字 8，模型声明的是 String —— 柔性序列化器必须能转
        val resp = JwJson.timetable(fixture("timetable_week.json"))
        assertEquals("8", resp.data[0].zc)
    }

    // ------------------------------------------------------------------
    // 实测结论：按日期查询的响应里可能根本没有 zc
    // ------------------------------------------------------------------

    @Test
    fun missingZcFallsBackToDefaultWithoutThrowing() {
        val resp = JwJson.timetable(fixture("timetable_week_no_zc.json"))

        assertEquals(2, resp.data.size)
        // 字段缺失 → 用默认值 ""，不抛异常。这就是 zc 声明成 `String = ""` 的原因
        assertEquals("", resp.data[0].zc)
        assertEquals("", resp.data[1].zc)
    }

    @Test
    fun missingZcStillMapsWithFallbackWeek() {
        val resp = JwJson.timetable(fixture("timetable_week_no_zc.json"))
        val courses = resp.data.mapNotNull { it.toCourse(fallbackWeek = 9) }

        assertEquals(2, courses.size)
        assertTrue(courses.all { it.weeks == setOf(9) })
        // 日期来自 qsrq，跟 zc 无关
        assertEquals(LocalDate.of(2026, 10, 27), courses[0].date)
        assertEquals("304机房", courses[1].room)
    }

    @Test
    fun missingZcWithoutFallbackWeekIsDropped() {
        // 不传 fallbackWeek 时整门课会被丢掉 —— 刻意的：
        // 宁可这门课不显示，也不要画出「不属于任何一周」的课
        val resp = JwJson.timetable(fixture("timetable_week_no_zc.json"))
        assertTrue(resp.data.mapNotNull { it.toCourse() }.isEmpty())
    }

    // ------------------------------------------------------------------
    // 成绩
    // ------------------------------------------------------------------

    @Test
    fun parsesScoresWithFlatRows() {
        val resp = JwJson.scores(fixture("scores_page1.json"))

        // 真实结构是 { data:"", rows:[...], total:50 } —— rows 与 data 平级
        assertEquals(20, resp.rows.size)
        assertEquals(50, resp.total)
        assertTrue("20 < 50 说明必须翻页，否则只拿到 2/5 的成绩", resp.hasMore)
    }

    @Test
    fun scoreFieldTypesAreTolerant() {
        val first = JwJson.scores(fixture("scores_page1.json")).rows.first()

        // 学期字段实际叫 xnxqmc，不是 xnxqdmc
        assertEquals("2025-2026-2", first.xnxqmc)
        assertEquals("202502", first.xnxqdm)

        // zcjfs 在真实响应里是数字 88.2；zcj 是字符串 "88.20"（同值不同精度）
        assertEquals(88.2, first.score!!, 0.001)
        assertEquals("88.20", first.zcj)

        assertEquals(3.0, first.credit, 0.001)
        assertEquals(3.8, first.gradePoint!!, 0.001)
        assertTrue(first.passed)
    }

    @Test
    fun scoresCarryNoPersonalDataIntoModel() {
        // 模型里根本没有 xsxm / xsbh / xsdm 字段，隐私数据进不来
        val first = JwJson.scores(fixture("scores_page1.json")).rows.first()
        assertEquals("习近平新时代中国特色社会主义思想概论", first.kcmc)
    }
    // ------------------------------------------------------------------
    // 未登录时服务端返回 {"code":-401,"data":"","message":"尚未登录，请先登录"}
    // data 是空字符串而不是数组 —— 直接按 List 解会抛 JsonDecodingException
    // ------------------------------------------------------------------

    @Test
    fun authErrorEnvelopeDoesNotCrashTimetable() {
        val resp = JwJson.timetable("""{"code":-401,"data":"","message":"尚未登录，请先登录"}""")

        assertEquals(-401, resp.code)
        assertEquals("尚未登录，请先登录", resp.message)
        assertTrue("错误信封里的 data 必须当成空列表", resp.data.isEmpty())
        assertTrue("code != 0 时 ok 必须是 false", !resp.ok)
    }

    @Test
    fun authErrorEnvelopeDoesNotCrashScores() {
        val resp = JwJson.scores("""{"code":-401,"data":"","message":"尚未登录，请先登录"}""")

        assertEquals(-401, resp.code)
        assertTrue(resp.rows.isEmpty())
        assertTrue("不能假装成功", !resp.hasMore)
    }
}

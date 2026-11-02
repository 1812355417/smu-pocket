package com.smu.pocket.data

import com.smu.pocket.core.Course
import com.smu.pocket.core.Grade
import com.smu.pocket.core.Period
import com.smu.pocket.core.TimetableConfig
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 南医大教务入口。**待确认**（见 docs/capture-checklist.md §1 Q1）。
 * 现在只是占位：只用于打开登录页，不会自动请求。
 *
 * 注意扫码登录的两种形态，决定这两个值是否相同：
 * - 教务自带扫码 → LOGIN_URL 就是 BASE_URL
 * - 走学校统一身份认证（CAS）扫码 → LOGIN_URL 是 cas.* 域名，BASE_URL 仍然是教务域名
 */
object JwConfig {
    /** 扫码登录入口页 */
    const val LOGIN_URL = "https://zhjw.smu.edu.cn/"

    /** Retrofit 的 baseUrl，**必须以 / 结尾**，否则 Retrofit 启动时就抛异常 */
    const val BASE_URL = "https://zhjw.smu.edu.cn/"
}

/**
 * 教务服务契约。真实实现见 [RealJw]，P1 演示实现见 [FakeJw]。
 */
interface JwService {

    /** 学期元信息：学期名、第一周周一、总周数、作息时间 */
    suspend fun timetableConfig(): TimetableConfig

    /**
     * 课表。
     * @param week null = 当前周（真实实现）/ 整学期（演示实现）
     */
    suspend fun timetable(week: Int?): List<Course>

    /** 成绩。term 为 null 表示全部学期 */
    suspend fun grades(term: String? = null): List<Grade>

    /**
     * **整学期**课表（含考试安排）。
     *
     * 按周查询只能拿到当周，而考试分散在第 6~20 周，所以「近期考试」必须用总表。
     * 真实实现复用 bootstrap 已经拉过的那份数据，**不额外发请求**。
     */
    suspend fun termCourses(): List<Course> = emptyList()

    /** 当前使用的学期代码（如 "202601"）。演示实现返回 null。 */
    suspend fun semesterCode(): String? = null

    /**
     * 用**一次**请求判断会话是否有效（扫码登录轮询用，必须轻量）。
     * 演示实现直接返回 false —— 只有真接口才知道登录态。
     */
    suspend fun probeSession(): Boolean = false
}

/**
 * 演示实现：让 UI 不依赖接口也能跑起来、能截图、能讨论交互。
 * [AppContainer] 里换一行就切到 [RealJw]。
 */
class FakeJw : JwService {

    override suspend fun timetableConfig(): TimetableConfig {
        // 演示用：把「第一周周一」定成 5 周前，这样「当前周」= 第 6 周，看起来自然
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        return TimetableConfig(
            termName = "2025-2026 学年第二学期（演示）",
            firstMonday = monday.minusWeeks(5),
            totalWeeks = 20,
            periods = demoPeriods,
        )
    }

    override suspend fun timetable(week: Int?): List<Course> = demoCourses

    override suspend fun grades(term: String?): List<Grade> = demoGrades

    override suspend fun termCourses(): List<Course> = demoCourses + demoExams

    private companion object {

        val demoPeriods = listOf(
            Period(1, "08:00", "08:45"),
            Period(2, "08:55", "09:40"),
            Period(3, "10:00", "10:45"),
            Period(4, "10:55", "11:40"),
            Period(5, "14:00", "14:45"),
            Period(6, "14:55", "15:40"),
            Period(7, "16:00", "16:45"),
            Period(8, "16:55", "17:40"),
            Period(9, "19:00", "19:45"),
            Period(10, "19:55", "20:40"),
        )

        val allWeeks: Set<Int> = (1..16).toSet()
        val oddWeeks: Set<Int> = (1..16).filter { it % 2 == 1 }.toSet()
        val firstHalf: Set<Int> = (1..9).toSet()

        val demoCourses = listOf(
            Course(
                id = "c1", name = "系统解剖学", teacher = "王老师", room = "教学馆 301",
                dayOfWeek = 1, startSection = 1, endSection = 2, weeks = allWeeks,
                content = "纵隔与胸膜",
            ),
            Course(
                id = "c2", name = "生理学", teacher = "李老师", room = "教学馆 205",
                dayOfWeek = 1, startSection = 3, endSection = 4, weeks = firstHalf,
                content = "血液循环",
            ),
            Course(
                id = "c3", name = "大学英语", teacher = "陈老师", room = "外语楼 402",
                dayOfWeek = 2, startSection = 1, endSection = 2, weeks = allWeeks,
            ),
            Course(
                id = "c4", name = "诊断学", teacher = "周老师", room = "临床楼 114",
                dayOfWeek = 2, startSection = 5, endSection = 6, weeks = oddWeeks,
                content = "体格检查",
            ),
            Course(
                id = "c5", name = "生物化学", teacher = "赵老师", room = "基础楼 502",
                dayOfWeek = 3, startSection = 3, endSection = 4, weeks = allWeeks,
                content = "糖代谢",
            ),
            Course(
                id = "c6", name = "病理学", teacher = "孙老师", room = "基础楼 108",
                dayOfWeek = 4, startSection = 1, endSection = 3, weeks = allWeeks,
                content = "炎症与修复",
            ),
            Course(
                id = "c7", name = "医学统计学", teacher = "郑老师", room = "计算机教室 3",
                dayOfWeek = 5, startSection = 5, endSection = 6, weeks = allWeeks,
            ),
            Course(
                id = "c8", name = "体育（游泳）", teacher = "刘老师", room = "体育馆",
                dayOfWeek = 6, startSection = 1, endSection = 2, weeks = oddWeeks,
            ),
            Course(
                id = "c9", name = "医学统计学", teacher = "郑老师",
                dayOfWeek = 1, startSection = 5, endSection = 6, weeks = allWeeks,
                content = "自主学习：SPSS 上机练习", isSelfStudy = true,
            ),
        )

        /** 演示用的考试（日期是相对今天算的，保证一定在「近期」里） */
        val demoExams = listOf(
            Course(
                id = "e1", name = "药理学", room = "304机房",
                dayOfWeek = 1, startSection = 3, endSection = 4, weeks = setOf(14),
                date = LocalDate.now().plusDays(6), isExam = true,
            ),
            Course(
                id = "e2", name = "临床诊断学", room = "诊断学实验室(实验楼)(本部)",
                dayOfWeek = 4, startSection = 8, endSection = 9, weeks = setOf(17),
                date = LocalDate.now().plusDays(21), isExam = true,
            ),
        )

        val demoGrades = listOf(
            Grade("系统解剖学", "2025-2026-2", score = 91.0, gpa = 4.1, credit = 4.0, kind = "必修"),
            Grade("生理学", "2025-2026-2", score = 85.0, gpa = 3.5, credit = 3.5, kind = "必修"),
            Grade("大学英语", "2025-2026-2", score = 88.0, gpa = 3.8, credit = 3.0, kind = "必修"),
            Grade("医学统计学", "2025-2026-2", score = 79.0, gpa = 2.9, credit = 2.0, kind = "必修"),
            Grade("医德修养", "2025-2026-2", score = null, level = "优秀", gpa = 4.5, credit = 1.0, kind = "选修"),
            Grade("生物化学", "2025-2026-1", score = 93.0, gpa = 4.3, credit = 4.0, kind = "必修"),
        )
    }
}

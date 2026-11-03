package com.smu.pocket

import com.smu.pocket.core.Course
import com.smu.pocket.ui.UiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「考试安排」的回归测试。
 *
 * 起因：UI 里写死了 `take(6)`，第 7 场往后的考试（正好是 1 月那几场）被裁掉，
 * 看起来像「只排到 12 月底」。这里锁住 ViewModel 的语义：
 * **只要日期在今天及以后，一场都不许丢，按日期升序**。
 */
class ExamListTest {

    private fun exam(name: String, date: LocalDate, isExam: Boolean = true) = Course(
        id = "$name-$date",
        name = name,
        dayOfWeek = 1,
        startSection = 1,
        endSection = 2,
        weeks = setOf(1),
        date = date,
        isExam = isExam,
    )

    @Test
    fun keepsEveryFutureExamInDateOrder() {
        val today = LocalDate.now()
        val state = UiState(
            termCourses = listOf(
                exam("已考完", today.minusDays(1)),
                exam("一月第一场", today.plusDays(60)),
                exam("十二月", today.plusDays(30)),
                exam("一月第二场", today.plusDays(90)),
                exam("开学第一课（非考试）", today.plusDays(45), isExam = false),
            ),
        )

        // 过去的、非考试的都不要；未来的全部保留，并按日期升序
        assertEquals(
            listOf("十二月", "一月第一场", "一月第二场"),
            state.upcomingExams.map { it.name },
        )
    }

    @Test
    fun doesNotCapAtSixOrThirtyDays() {
        val today = LocalDate.now()
        // 10 场、跨度 100 天：如果哪天又加了 take(6) 或「未来 30 天」过滤，这条会挂
        val exams = (1..10).map { exam("E$it", today.plusDays(it.toLong() * 10)) }
        val state = UiState(termCourses = exams)

        assertEquals(10, state.upcomingExams.size)
        assertEquals("E10", state.upcomingExams.last().name)
        assertTrue(
            "必须按日期升序",
            state.upcomingExams.zipWithNext().all { (a, b) -> !b.date!!.isBefore(a.date!!) },
        )
    }
}

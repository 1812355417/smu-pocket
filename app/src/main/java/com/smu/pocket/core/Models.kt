package com.smu.pocket.core

import java.time.LocalDate

/**
 * 领域模型。字段名按「语义」定，和教务的 40 个字符段解耦 ——
 * 教务改字段名只需改 `data/model/Mappers.kt` 一个文件。
 */
data class Course(
    val id: String,
    val name: String,
    val teacher: String? = null,
    val room: String? = null,
    /** 1 = 周一 … 7 = 周日 */
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    /** 出现在哪几周。按周查询时就是 { 当前周 } */
    val weeks: Set<Int>,
    /** 上课内容，只有按周查询才有 */
    val content: String? = null,
    /** 真实日期，只有按周查询才有 */
    val date: LocalDate? = null,
    /** 教务给的上课时刻，如 "08:30-10:00"（来自 qssj/jssj，比作息表准） */
    val timeLabel: String? = null,
    /** 教务给的课程配色，如 "#1f86c7" */
    val colorHex: String? = null,
    /** 自主学习（`jxhjmc` = 自主学习）：不需要去教室 */
    val isSelfStudy: Boolean = false,
    /** 考试环节（`jxhjmc` = 考试（理论）/考试（实操）） */
    val isExam: Boolean = false,
) {
    val sectionSpan: Int get() = endSection - startSection + 1

    fun occursIn(week: Int): Boolean = week in weeks

    val cellKey: Int get() = dayOfWeek * 100 + startSection
}

data class Grade(
    val course: String,
    val term: String,
    /** 百分制分数。等级制课程（优/良/中）这里可能是 null */
    val score: Double? = null,
    val level: String? = null,
    val gpa: Double? = null,
    val credit: Double = 0.0,
    val kind: String? = null,
)

data class Period(val index: Int, val start: String, val end: String) {
    val label: String get() = "$start-$end"
}

/**
 * 课表渲染所需的元信息。
 * [firstMonday] 由「第 1 周查询的 qsrq」反推；[periods] 接口不给，用默认值。
 */
data class TimetableConfig(
    val termName: String,
    val firstMonday: LocalDate,
    val totalWeeks: Int,
    val periods: List<Period>,
) {
    val maxSection: Int get() = periods.maxOfOrNull { it.index } ?: 0
}

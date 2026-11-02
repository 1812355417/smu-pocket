package com.smu.pocket.data.model

import com.smu.pocket.core.Course
import com.smu.pocket.core.Grade
import com.smu.pocket.core.TimeUtils
import com.smu.pocket.core.dateOf
import java.time.LocalDate

/**
 * 接口模型 → 领域模型。
 *
 * UI 只认 [Course] / [Grade]，不认教务的 40 个字段。好处：
 * - 教务改字段名，只改这一个文件；
 * - 隐私字段（`xsbh` 学号、`xsxm` 姓名）根本进不了领域模型。
 */

/**
 * @param fallbackWeek 调用方知道的周次。按日期/周次查询的响应里**没有 `zc`**，
 *   必须靠它兜底，否则 `weeks` 为空 → 整门课被静默丢掉。
 */
fun CourseData.toCourse(fallbackWeek: Int? = null, firstMonday: LocalDate? = null): Course? {
    val day = xq
    if (day !in 1..7) return null

    val sections = TimeUtils.sectionsOf(this) ?: return null

    val weeksFromData = TimeUtils.parseWeekList(zc)
    val weeks: Set<Int> = when {
        weeksFromData.isNotEmpty() -> weeksFromData
        fallbackWeek != null -> setOf(fallbackWeek)
        else -> return null
    }

    val name = kcmc?.takeIf { it.isNotBlank() } ?: return null

    return Course(
        id = listOfNotNull(kcdm, kcbh, day.toString(), sections.first.toString(), zc.ifBlank { "w$fallbackWeek" })
            .joinToString("_"),
        name = name,
        teacher = teaxms?.takeIf { it.isNotBlank() },
        room = jxcdmc?.takeIf { it.isNotBlank() },
        dayOfWeek = day,
        startSection = sections.first,
        endSection = sections.last,
        weeks = weeks,
        content = sknrjj?.takeIf { it.isNotBlank() },
        // 整学期总表没有 qsrq；但若这行只落在某一周，可以用第一周周一推出来
        date = TimeUtils.parseDate(qsrq)
            ?: if (firstMonday != null && weeks.size == 1) {
                dateOf(firstMonday, weeks.first(), day)
            } else {
                null
            },
        timeLabel = TimeUtils.clockRange(this),
        colorHex = bgcolor?.takeIf { it.isNotBlank() },
        isSelfStudy = isSelfStudyKind(jxhjmc),
        isExam = isExamKind(jxhjmc) || isExamKind(khfsmc),
    )
}

/**
 * 教学环节名称判断。
 *
 * 实测 118 行真实数据里 `jxhjmc` 只有 7 种取值：
 * 理论 / 自主学习 / 实验 / 考试（理论）/ 考试（实操）/ 技能 / 见习。
 * 其中「自主学习」19 行**全部没有教室**，与其它类别完全可分。
 *
 * 同时把「线上 / 网络 / 慕课 / 在线 / 自习」也算进来，将来教务改叫法也不会漏。
 */
/** 考试环节：「考试（理论）」「考试（实操）」；顺带兼容 khfsmc 里的「考试」 */
private fun isExamKind(v: String?): Boolean = v?.contains("考试") == true

private fun isSelfStudyKind(jxhjmc: String?): Boolean {
    val v = jxhjmc?.trim().orEmpty()
    if (v.isEmpty()) return false
    return listOf("自主", "线上", "网络", "慕课", "在线", "自习").any { v.contains(it) }
}

fun List<CourseData>.toCourses(fallbackWeek: Int? = null, firstMonday: LocalDate? = null): List<Course> =
    mapNotNull { it.toCourse(fallbackWeek, firstMonday) }

fun ScoreData.toGrade(): Grade? {
    val name = kcmc?.takeIf { it.isNotBlank() } ?: return null

    val numericScore = score
    // 等级制课程（优/良/中）没有分数，等级就藏在 zcj 里
    val level = if (numericScore == null) zcj.takeIf { it.isNotBlank() } else null

    return Grade(
        course = name,
        term = xnxqmc.orEmpty(),
        score = numericScore,
        level = level,
        gpa = gradePoint,
        credit = credit,
        kind = xdfsmc?.takeIf { it.isNotBlank() },
    )
}

fun List<ScoreData>.toGrades(): List<Grade> = mapNotNull { it.toGrade() }

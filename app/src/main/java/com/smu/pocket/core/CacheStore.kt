package com.smu.pocket.core

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * 本地缓存：把最后一次成功拿到的**课表 + 成绩**序列化后加密保存。
 *
 * 注意这里有**两份课表**：
 * - [CachedSnapshot.courses]：课表页当时选中的那一周
 * - [CachedSnapshot.currentCourses]：当时**真实当前周**的课（今日页用）
 *
 * 分两份是因为「今日」必须和「课表选中周」解耦 —— 用户在课表翻到第 8 周，
 * 今日页不能跟着变成第 8 周的课。
 */

@Serializable
data class CachedPeriod(val index: Int, val start: String, val end: String)

@Serializable
data class CachedConfig(
    val termName: String,
    val firstMonday: String,
    val totalWeeks: Int,
    val periods: List<CachedPeriod> = emptyList(),
)

@Serializable
data class CachedCourse(
    val name: String,
    val teacher: String? = null,
    val room: String? = null,
    val dayOfWeek: Int = 1,
    val startSection: Int = 1,
    val endSection: Int = 1,
    val weeks: List<Int> = emptyList(),
    val content: String? = null,
    val date: String? = null,
    val timeLabel: String? = null,
    val colorHex: String? = null,
    val isSelfStudy: Boolean = false,
)

@Serializable
data class CachedGrade(
    val course: String,
    val term: String,
    val score: Double? = null,
    val level: String? = null,
    val gpa: Double? = null,
    val credit: Double = 0.0,
    val kind: String? = null,
)

@Serializable
data class CachedSnapshot(
    val version: Int = 1,
    /** 保存时刻（毫秒） */
    val savedAt: Long = 0L,
    /** 课表页缓存的是第几周 */
    val week: Int = 1,
    val courses: List<CachedCourse> = emptyList(),
    /** 今日页缓存的是哪一周（真实当前周） */
    val currentWeek: Int = 1,
    val currentCourses: List<CachedCourse> = emptyList(),
    val config: CachedConfig? = null,
    val grades: List<CachedGrade> = emptyList(),
    /** 近期考试（离线时也能看到） */
    val exams: List<CachedCourse> = emptyList(),
)

class CacheStore(context: Context) {

    private val storage = EncryptedStore(context, "snapshot")
    init {
        val legacy = File(context.filesDir, "cache_snapshot.json")
        check(!legacy.exists() || legacy.delete()) { "Cannot remove legacy private cache" }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): CachedSnapshot? {
        val encrypted = storage.read() ?: return null
        return try {
            json.decodeFromString<CachedSnapshot>(encrypted)
        } catch (e: Exception) {
            storage.clear()
            null
        }
    }

    fun save(snapshot: CachedSnapshot) {
        try {
            storage.write(json.encodeToString(snapshot))
        } catch (e: Exception) {
            // 写失败不影响主流程
        }
    }

    fun clear() {
        try {
            storage.clear()
        } catch (e: Exception) {
            // ignore
        }
    }
}

// ---------------------------------------------------------------------
// 领域模型 <-> 缓存模型
// ---------------------------------------------------------------------

private fun List<CachedCourse>.toDomainCourses(): List<Course> = mapIndexed { index, c ->
    Course(
        id = "cache_${c.dayOfWeek}_${c.startSection}_$index",
        name = c.name,
        teacher = c.teacher,
        room = c.room,
        dayOfWeek = c.dayOfWeek,
        startSection = c.startSection,
        endSection = c.endSection,
        weeks = c.weeks.toSet(),
        content = c.content,
        date = c.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        timeLabel = c.timeLabel,
        colorHex = c.colorHex,
        isSelfStudy = c.isSelfStudy,
    )
}

private fun List<Course>.toCacheCourses(): List<CachedCourse> = map {
    CachedCourse(
        name = it.name,
        teacher = it.teacher,
        room = it.room,
        dayOfWeek = it.dayOfWeek,
        startSection = it.startSection,
        endSection = it.endSection,
        weeks = it.weeks.sorted(),
        content = it.content,
        date = it.date?.toString(),
        timeLabel = it.timeLabel,
        colorHex = it.colorHex,
        isSelfStudy = it.isSelfStudy,
    )
}

fun CachedSnapshot.toConfig(): TimetableConfig? {
    val c = config ?: return null
    val monday = runCatching { LocalDate.parse(c.firstMonday) }.getOrNull() ?: return null
    val cached = c.periods.map { Period(it.index, it.start, it.end) }
    return TimetableConfig(
        termName = c.termName,
        firstMonday = monday,
        totalWeeks = c.totalWeeks,
        // 旧版本缓存里作息只有 10 节：缺 11~14 节就用默认表补齐，
        // 否则升级后不清缓存，「今日」页的晚课仍然查不到时刻
        periods = if (cached.size >= TOTAL_SECTIONS) cached else DefaultPeriods.ALL,
    )
}

/** 课表页那份（选中周） */
fun CachedSnapshot.toCourses(): List<Course> = courses.toDomainCourses()

/** 今日页那份（真实当前周） */
fun CachedSnapshot.toCurrentCourses(): List<Course> =
    if (currentCourses.isNotEmpty()) currentCourses.toDomainCourses()
    else courses.toDomainCourses()

fun CachedSnapshot.toExams(): List<Course> =
    exams.toDomainCourses().map { it.copy(isExam = true) }   // 缓存的这份本来就只装考试

fun CachedSnapshot.toGrades(): List<Grade> = grades.map {
    Grade(
        course = it.course,
        term = it.term,
        score = it.score,
        level = it.level,
        gpa = it.gpa,
        credit = it.credit,
        kind = it.kind,
    )
}

fun snapshotOf(
    config: TimetableConfig,
    scheduleCourses: List<Course>,
    currentWeekCourses: List<Course>,
    grades: List<Grade>,
    exams: List<Course>,
    week: Int,
    currentWeek: Int,
    savedAt: Long,
): CachedSnapshot = CachedSnapshot(
    savedAt = savedAt,
    week = week,
    courses = scheduleCourses.toCacheCourses(),
    currentWeek = currentWeek,
    currentCourses = currentWeekCourses.toCacheCourses(),
    config = CachedConfig(
        termName = config.termName,
        firstMonday = config.firstMonday.toString(),
        totalWeeks = config.totalWeeks,
        periods = config.periods.map { CachedPeriod(it.index, it.start, it.end) },
    ),
    exams = exams.toCacheCourses(),
    grades = grades.map {
        CachedGrade(
            course = it.course,
            term = it.term,
            score = it.score,
            level = it.level,
            gpa = it.gpa,
            credit = it.credit,
            kind = it.kind,
        )
    },
)

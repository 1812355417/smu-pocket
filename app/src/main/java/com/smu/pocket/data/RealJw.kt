package com.smu.pocket.data

import com.smu.pocket.core.Course
import com.smu.pocket.core.Grade
import com.smu.pocket.core.Period
import com.smu.pocket.core.Semester
import com.smu.pocket.core.SessionExpiredException
import com.smu.pocket.core.TermCode
import com.smu.pocket.core.TimeUtils
import com.smu.pocket.core.TimetableConfig
import com.smu.pocket.data.model.CourseData
import com.smu.pocket.data.model.JwJson
import com.smu.pocket.data.model.toCourse
import com.smu.pocket.data.model.toGrades
import android.util.Log
import java.io.IOException
import java.time.LocalDate
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * 真实教务实现。结论全部来自真机实测（见 smu-probe/教务接口规格.md）：
 *
 * 1. **`zc` 就是「第几周」**：`zc=9` → 第 9 周；`zc=` 空 → 整学期总表；`zc=201` → 空数组。
 * 2. **`Referer` 必须带**，否则 `{"code":-401,"message":"疑似非法请求…"}`。
 * 3. **未登录是 HTTP 200 + `code:-401`**，要翻译成 [SessionExpiredException]。
 * 4. 第 1 周的课表带 `qsrq`，所以「第一周周一」可以从它反推，不需要校历接口。
 * 5. **学期代码不再写死**：交给 [TermResolver] 动态解析（教务页面 → 缓存 → 日期推算 → 常量）。
 */
class RealJw(
    private val api: JwApi,
    private val terms: TermResolver,
    /**
     * 手填的「第一周周一」。填了就跳过自举。
     * 自举依赖「第 1 周查询会返回 qsrq」这个假设 —— 实测成立，
     * 但万一哪天教务改版，这个参数能兜住。
     */
    private val manualFirstMonday: LocalDate? = null,
) : JwService {

    private var anchor: TimetableConfig? = null

    /** 整学期总表缓存：bootstrap 时拉过一次，别重复请求 */
    private var termRows: List<CourseData>? = null

    /** 解析出来的学期代码，进程内缓存，避免每次请求都解析一遍 */
    private var resolvedTerm: String? = null

    /**
     * 当前学期代码。首次调用会触发 [TermResolver.resolve]（最多一次页面请求），
     * 之后走内存缓存。
     */
    private suspend fun term(): String =
        resolvedTerm ?: terms.resolve().code.also { resolvedTerm = it }

    /**
     * 探针专用：**不触发网络解析**。
     * 扫码时每 2 秒调一次 [probeSession]，绝不能因为它去抓学期页。
     */
    private fun termForProbe(): String = resolvedTerm ?: terms.peek()

    override suspend fun semesterCode(): String = term()

    override suspend fun timetableConfig(): TimetableConfig = bootstrap()

    override suspend fun timetable(week: Int?): List<Course> {
        val config = bootstrap()

        val target = Semester.clampWeek(
            week ?: TimeUtils.currentWeek(LocalDate.now(), config.firstMonday),
            config.totalWeeks,
        )

        // 响应里没有 zc（按周查的嘛），但「我查的是第 target 周」我自己知道
        val rows = fetchWeek(target)
        val mapped = rows.mapNotNull { it.toCourse(fallbackWeek = target) }
        return mapped
    }

    /**
     * 整学期课表（含考试）。数据来自 bootstrap 已经拉过的总表，通常不产生新请求。
     * 日期用「第一周周一 + zc + xq」推算（总表没有 qsrq）。
     */
    override suspend fun termCourses(): List<Course> {
        val config = bootstrap()
        return fetchAllWeeks().mapNotNull {
            it.toCourse(fallbackWeek = null, firstMonday = config.firstMonday)
        }
    }

    override suspend fun grades(term: String?): List<Grade> {
        val collected = mutableListOf<Grade>()
        var page = 1

        // 实测 rows=200 时 total=50、一次拿全；保留循环以防以后成绩变多
        while (page <= MAX_PAGES) {
            val body = requestBody { api.scores(term = term ?: term(), page = page) }
            val parsed = JwJson.scores(body)
            throwIfAuthFailed(parsed.code, parsed.message)

            collected += parsed.rows.toGrades()
            if (parsed.rows.isEmpty() || !parsed.hasMore) break
            page++
        }

        return collected
    }

    /**
     * 一次请求判断登录态：`zc=1` 是最便宜的可用请求。
     * 不走 bootstrap（那要 2~3 个请求），因为扫码时会每 2 秒探一次。
     */
    override suspend fun probeSession(): Boolean {
        val body = requestBody { api.timetable(term = termForProbe(), zc = "1") }
        return JwJson.timetable(body).code == 0
    }

    // ------------------------------------------------------------------
    // 自举：第一周周一 + 总周数
    // ------------------------------------------------------------------

    private suspend fun bootstrap(): TimetableConfig {
        anchor?.let { return it }

        manualFirstMonday?.let { return configOf(it, DEFAULT_TOTAL_WEEKS) }

        val firstMonday = try {
            deriveFirstMonday()
        } catch (e: Exception) {
            null
        } ?: FALLBACK_FIRST_MONDAY

        val totalWeeks = try {
            deriveTotalWeeks()
        } catch (e: Exception) {
            DEFAULT_TOTAL_WEEKS
        }

        return configOf(firstMonday, totalWeeks)
    }

    /**
     * 查第 1 周（`zc=1`），每行都带 `qsrq`；
     * 该行所在周的周一 = qsrq - (xq-1) 天，取最小值就是第一周周一。
     * 实测 10 条记录一致指向 2026-08-31。
     */
    private suspend fun deriveFirstMonday(): LocalDate? {
        val mondays = fetchWeek(1).mapNotNull { row ->
            TimeUtils.parseDate(row.qsrq)?.minusDays((row.xq - 1).toLong())
        }
        return mondays.minOrNull()
    }

    /** `zc` 传空 → 整学期总表 → 所有周次串里的最大周次就是总周数 */
    private suspend fun deriveTotalWeeks(): Int {
        val weeks = fetchAllWeeks().flatMap { TimeUtils.parseWeekList(it.zc) }
        return weeks.maxOrNull()?.coerceIn(MIN_WEEKS, MAX_WEEKS) ?: DEFAULT_TOTAL_WEEKS
    }

    private suspend fun configOf(firstMonday: LocalDate, totalWeeks: Int): TimetableConfig {
        val name = TermCode.displayName(term())
        return TimetableConfig(name, firstMonday, totalWeeks, DEFAULT_PERIODS)
            .also { anchor = it }
    }

    // ------------------------------------------------------------------
    // 底层请求
    // ------------------------------------------------------------------

    private suspend fun fetchWeek(week: Int): List<CourseData> {
        val body = requestBody { api.timetable(term = term(), zc = week.toString()) }
        return decodeTimetable(body)
    }

    private suspend fun fetchAllWeeks(): List<CourseData> {
        termRows?.let { return it }
        val body = requestBody { api.timetable(term = term(), zc = "") }
        return decodeTimetable(body).also { termRows = it }
    }

    private fun decodeTimetable(body: String): List<CourseData> {
        val parsed = JwJson.timetable(body)
        throwIfAuthFailed(parsed.code, parsed.message)
        if (!parsed.ok) throw IOException("教务返回 code=${parsed.code}，message=${parsed.message}")
        return parsed.data
    }

    /** 教务用 `code = -401` 表示未登录（HTTP 仍是 200），统一翻译成可捕获的异常 */
    private fun throwIfAuthFailed(code: Int, message: String) {
        if (code == -401) {
            throw SessionExpiredException(message.ifBlank { "登录态失效，请重新扫码登录" })
        }
    }

    private suspend fun requestBody(call: suspend () -> Response<ResponseBody>): String {
        val response = call()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
        return response.body()?.use { it.string() }.orEmpty()
    }

    companion object {

        private const val MAX_PAGES = 20
        private const val MIN_WEEKS = 16
        private const val MAX_WEEKS = 30
        private const val DEFAULT_TOTAL_WEEKS = 20

        /**
         * 兜底学期起始日：202601 学期第 1 周周一（由 `zc=1` 的 10 条真实记录反推得出）。
         * 自举失败时用它，保证 UI 不会因为拿不到日期而崩。
         */
        private val FALLBACK_FIRST_MONDAY: LocalDate = LocalDate.of(2026, 8, 31)

        /**
         * 作息时间。教务没有接口给这个（`qssj`/`jssj` 是**每门课**的起止时刻，不是每节课的），
         * 所以先给一份默认值，需要精确时按学校作息表改。
         */
        private val DEFAULT_PERIODS = listOf(
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
    }
}

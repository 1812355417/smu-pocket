package com.smu.pocket.data

import com.smu.pocket.core.Course
import com.smu.pocket.core.DefaultPeriods
import com.smu.pocket.core.Grade
import com.smu.pocket.core.Semester
import com.smu.pocket.core.SessionExpiredException
import com.smu.pocket.core.TermCode
import com.smu.pocket.core.TimeUtils
import com.smu.pocket.core.TimetableConfig
import com.smu.pocket.data.model.CourseData
import com.smu.pocket.data.model.JwJson
import com.smu.pocket.data.model.toCourse
import com.smu.pocket.data.model.toGrades
import kotlinx.coroutines.CancellationException
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

    /** 网考号缓存：课程名 → 网考号（`xskcrw/skrwDatas`）。一次进程内只拉一次 */
    private var examNumberCache: Map<String, String>? = null

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

    /**
     * 考试网考号：课程名 → `xkh`。
     *
     * 这是**纯展示增强**：任何失败都退化成空表，「考试安排」照常只显示课名；
     * 调用方 [com.smu.pocket.ui.AppViewModel] 外面还兜了一层 runCatching。
     * 拿到就缓存，切周 / 重进页面不重复请求。
     */
    override suspend fun examNumbers(term: String?): Map<String, String> {
        examNumberCache?.let { return it }

        val targetTerm = term ?: this.term()
        val rows = collectPages { page ->
            val parsed = JwJson.examTasks(requestBody { api.examTasks(term = targetTerm, page = page) })
            throwIfAuthFailed(parsed.code, parsed.message)
            if (!parsed.ok) throw IOException("教务返回 code=${parsed.code}")
            Page(parsed.rows, parsed.total)
        }
        val collected = LinkedHashMap<String, String>()
        rows.forEach { row ->
            val name = row.kcmc?.trim().orEmpty()
            val number = row.onlineExamNumber
            if (name.isNotEmpty() && number.isNotEmpty()) collected[name] = number
        }

        // 一条都没拿到就先别缓存，下次进「今日」还能再试一次
        if (collected.isNotEmpty()) examNumberCache = collected
        return collected
    }

    override suspend fun grades(term: String?): List<Grade> {
        // 空学期查询全部成绩。分页完成后才映射和发布，避免静默保存半份数据。
        return collectPages { page ->
            val parsed = JwJson.scores(requestBody { api.scores(term = term.orEmpty(), page = page) })
            throwIfAuthFailed(parsed.code, parsed.message)
            if (parsed.code != 0) throw IOException("教务返回 code=${parsed.code}")
            Page(parsed.rows, parsed.total)
        }.toGrades()
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

        val firstMonday = deriveFirstMonday() ?: throw IOException("无法从教务课表推算第一周周一，请稍后重试")

        val totalWeeks = try {
            deriveTotalWeeks()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            throw e
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
        return TimeUtils.inferFirstMonday(fetchWeek(1), queriedWeek = 1)
    }

    /** `zc` 传空 → 整学期总表 → 所有周次串里的最大周次就是总周数 */
    private suspend fun deriveTotalWeeks(): Int {
        val weeks = fetchAllWeeks().flatMap { TimeUtils.parseWeekList(it.zc) }
        return weeks.maxOrNull()?.coerceIn(MIN_WEEKS, MAX_WEEKS) ?: DEFAULT_TOTAL_WEEKS
    }

    private suspend fun configOf(firstMonday: LocalDate, totalWeeks: Int): TimetableConfig {
        val name = TermCode.displayName(term())
        return TimetableConfig(name, firstMonday, totalWeeks, DefaultPeriods.ALL)
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

        private const val MIN_WEEKS = 16
        private const val MAX_WEEKS = 30
        private const val DEFAULT_TOTAL_WEEKS = 20
    }
}

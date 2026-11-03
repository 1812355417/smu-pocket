package com.smu.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smu.pocket.core.CacheStore
import com.smu.pocket.core.Course
import com.smu.pocket.core.Grade
import com.smu.pocket.core.Semester
import com.smu.pocket.core.SessionExpiredException
import com.smu.pocket.core.SessionStore
import com.smu.pocket.core.TimetableConfig
import com.smu.pocket.core.snapshotOf
import com.smu.pocket.core.toConfig
import com.smu.pocket.core.toCurrentCourses
import com.smu.pocket.core.toExams
import com.smu.pocket.core.toGrades
import com.smu.pocket.core.toCourses
import com.smu.pocket.data.FakeJw
import com.smu.pocket.data.JwService
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 网考号最多等多久：超时就当没有，不能让「考试安排」一直转圈 */
private const val EXAM_NUMBERS_TIMEOUT_MS = 5_000L

/**
 * 全局状态。单一 ViewModel + StateFlow。
 *
 * [courses] 的语义是「**当前选中那一周**的课」——真实课表接口是按周拉的（`zc=第几周`）。
 * [stale] = true 表示现在显示的是**本地缓存的旧数据**，登录已过期，等用户重新扫码。
 */
data class UiState(
    val loading: Boolean = false,
    val loggedIn: Boolean = false,
    val demoMode: Boolean = false,
    val config: TimetableConfig? = null,
    /** 课表页：用户选中那一周的课（随 selectedWeek 变） */
    val scheduleCourses: List<Course> = emptyList(),
    /** 今日页：**真实当前周**的课（与 selectedWeek 完全无关） */
    val currentWeekCourses: List<Course> = emptyList(),
    /** 明日跨到下一周时，这里是下一周的课（只有今天是周日才会去拉） */
    val nextWeekCourses: List<Course> = emptyList(),
    /** currentWeekCourses 实际属于第几周。缓存是旧的周时，它 != currentWeek */
    val currentCoursesWeek: Int = 0,
    /** 整学期课表（含考试安排），供「近期考试」使用 */
    val termCourses: List<Course> = emptyList(),
    /**
     * 考试「网考号」：课程名 → 网考号（`xskcrw/skrwDatas` 的 `xkh`）。
     *
     * 只放内存、不落缓存：号码跟着学期/会话变，缓存旧号反而会误导。
     * 拉不到就是空表，考试安排照常显示课名。
     */
    val examNumbers: Map<String, String> = emptyMap(),
    /**
     * 网考号是否「已就绪」（请求成功、失败、或没有会话压根不会去请求，都算就绪）。
     *
     * false 时「考试安排」先显示 Loading，等 [examNumbers] 和考试列表**一起**渲染 ——
     * 否则列表先出现、号码过一秒再「跳」出来，看着像闪烁。
     */
    val examNumbersReady: Boolean = false,
    /** 成绩是否成功拉取过（失败时不要把缓存里的成绩覆盖成空） */
    val gradesLoaded: Boolean = false,

    /** 切周失败的原因；null = 没有错误 */
    val weekError: String? = null,

    /** 当前使用的学期代码，如 "202601" */
    val semesterCode: String? = null,
    val grades: List<Grade> = emptyList(),
    val currentWeek: Int = 1,
    val selectedWeek: Int = 1,
    val error: String? = null,
    /** 数据来自缓存（登录已过期） */
    val stale: Boolean = false,
    /** 缓存写入时间，用于「更新于 X 分钟前」 */
    val cachedAt: Long? = null,
) {
    // ---- 课表页：跟着 selectedWeek 走 ----

    val scheduleWeekCourses: List<Course>
        get() = scheduleCourses.filter { it.occursIn(selectedWeek) }

    val selectedWeekTotal: Int get() = scheduleWeekCourses.size

    // ---- 今日页：只看真实当前周，和 selectedWeek 无关 ----
    //
    // 这里绝对不能复用 scheduleCourses —— 那是「用户翻到的那一周」。
    // 用户在课表翻到第 8 周时，今日页必须仍然是第 9 周（真实当前周）的课。

    private val todayIndex: Int get() = LocalDate.now().dayOfWeek.value

    val todayCourses: List<Course>
        get() = currentWeekCourses
            .filter { it.occursIn(currentWeek) && it.dayOfWeek == todayIndex }
            .sortedBy { it.startSection }

    /** 每天几节课（真实当前周），用于「本周概览」条形图 */
    val dailyCounts: List<Int>
        get() = (1..7).map { day ->
            currentWeekCourses.count { it.occursIn(currentWeek) && it.dayOfWeek == day }
        }

    val weekTotal: Int get() = currentWeekCourses.count { it.occursIn(currentWeek) }
    // ---- 明日课程：同样只看「真实日期」，与 selectedWeek 无关 ----
    //
    // 注意跨周：今天是周日时，明天属于**下一周**，当前周的数据里没有明天的课。
    // 所以 loadAll 会在这种情况下额外拉一次下一周，存进 nextWeekCourses。

    private val tomorrowDate: LocalDate get() = LocalDate.now().plusDays(1)

    /** 明天是第几周（拿不到 config 时为 null） */
    val tomorrowWeek: Int?
        get() {
            val c = config ?: return null
            return Semester.clampWeek(Semester.weekOf(tomorrowDate, c.firstMonday), c.totalWeeks)
        }

    /** 当前周的数据是否「新鲜」（缓存可能还是上一周的） */
    val todayDataReady: Boolean
        get() = currentCoursesWeek == currentWeek

    /** 明天那一周的数据是否已就绪（跨周且没拉到下一周时为 false） */
    val tomorrowDataReady: Boolean
        get() {
            val week = tomorrowWeek ?: return false
            return if (week == currentWeek) todayDataReady else nextWeekCourses.isNotEmpty()
        }

    /** 近期考试：只要今天及以后的，按日期从近到远 */
    val upcomingExams: List<Course>
        get() {
            val today = LocalDate.now()
            return termCourses
                .filter { exam -> exam.isExam && exam.date?.isBefore(today) == false }
                .sortedBy { it.date }
        }

    val tomorrowCourses: List<Course>
        get() {
            val week = tomorrowWeek ?: return emptyList()
            val source = if (week == currentWeek) currentWeekCourses else nextWeekCourses
            return source
                .filter { it.occursIn(week) && it.dayOfWeek == tomorrowDate.dayOfWeek.value }
                .sortedBy { it.startSection }
        }

    val hasData: Boolean
        get() = scheduleCourses.isNotEmpty() || currentWeekCourses.isNotEmpty() || grades.isNotEmpty()

    val weightedGpa: Double?
        get() {
            val valid = grades.filter { it.gpa != null && it.credit > 0.0 }
            val credits = valid.sumOf { it.credit }
            if (credits <= 0.0) return null
            return valid.sumOf { (it.gpa ?: 0.0) * it.credit } / credits
        }

    val weightedScore: Double?
        get() {
            val valid = grades.filter { it.score != null && it.credit > 0.0 }
            val credits = valid.sumOf { it.credit }
            if (credits <= 0.0) return null
            return valid.sumOf { (it.score ?: 0.0) * it.credit } / credits
        }

    val totalCredit: Double get() = grades.sumOf { it.credit }

    /** 按学期分组，新的在前 */
    val gradesByTerm: List<Pair<String, List<Grade>>>
        get() = grades.groupBy { it.term }.toList().sortedByDescending { it.first }
}

class AppViewModel(
    private val jwProvider: () -> JwService,
    private val session: SessionStore,
    private val cache: CacheStore,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var jw: JwService = jwProvider()

    /** 设置页点了「同步」：这种情况下会话失效就直接回登录页，而不是挂个缓存标记 */
    private var explicitSync = false

    /** 切周的请求要能取消，否则连点几周时旧响应会覆盖新响应 */
    private var weekJob: Job? = null

    init {
        // 先亮缓存（秒开、断网也能看），再去后台刷新
        val restored = restoreFromCache()

        if (restored) {
            if (session.isLoggedIn()) {
                loadAll()
            } else {
                // cookie 已经没了，但本地还有数据 —— 温和提示，不踢回登录页
                _state.update { it.copy(stale = true) }
            }
        } else if (session.isLoggedIn()) {
            _state.update { it.copy(loggedIn = true) }
            loadAll()
        }
    }

    /** @return 是否成功恢复了缓存 */
    private fun restoreFromCache(): Boolean {
        val snapshot = cache.load() ?: return false
        if (snapshot.courses.isEmpty() && snapshot.grades.isEmpty()) return false

        val config = snapshot.toConfig() ?: return false
        val current = Semester.clampWeek(
            Semester.weekOf(LocalDate.now(), config.firstMonday),
            config.totalWeeks,
        )

        _state.update {
            it.copy(
                loggedIn = true,
                config = config,
                scheduleCourses = snapshot.toCourses(),
                currentWeekCourses = snapshot.toCurrentCourses(),
                currentCoursesWeek = snapshot.currentWeek,
                termCourses = snapshot.toExams(),
                grades = snapshot.toGrades(),
                selectedWeek = snapshot.week,
                currentWeek = current,
                cachedAt = snapshot.savedAt,
                // 有会话 → 马上会去刷新，考试列表先等号码一起出来；
                // 没会话（离线看缓存）→ 号码永远不会来，直接放行，别让列表一直转圈
                examNumbersReady = !session.isLoggedIn(),
            )
        }
        return true
    }

    /** 登录页的「先用演示数据看看」 */
    fun enterDemo() {
        jw = FakeJw()
        weekJob?.cancel()
        _state.update { it.copy(loggedIn = true, demoMode = true, stale = false) }
        loadAll()
    }

    fun onLoginCookies(host: String, cookieHeader: String) {
        session.saveRawCookieHeader("https://$host/", cookieHeader)
        jw = jwProvider()
    }

    /** 登录成功的唯一可靠判定：拿当前 cookie 真打一次接口 */
    suspend fun verifySession(): Boolean {
        if (!session.isLoggedIn()) return false

        val ok = runCatching { jw.probeSession() }.getOrDefault(false)
        if (!ok) return false

        _state.update { it.copy(loggedIn = true, error = null) }
        loadAll()
        return true
    }

    /** 用户关掉登录页但没登录成功：清掉没认证的 cookie */
    fun onLoginCancelled() {
        if (!_state.value.loggedIn) {
            session.clear()
            jw = jwProvider()
        }
    }

    /** 缓存横幅上的「重新登录」 */
    fun relogin() {
        weekJob?.cancel()
        session.clear()
        jw = jwProvider()
        _state.value = UiState()
    }

    /**
     * 设置页的「同步最新课表与成绩」。
     *
     * 静默刷新；如果这次失败是因为会话过期，就**直接回到登录页**（让用户重新扫码），
     * 而不是像自动加载那样继续挂着缓存 —— 因为是用户主动点的同步，他期待的是最新数据。
     */
    fun syncNow() {
        weekJob?.cancel()

        // 没有会话时 jwProvider() 会返回 FakeJw —— 那样"同步"会悄悄加载演示数据，
        // 界面上却标着"已接入"。宁可回登录页，也不能给假数据。
        if (!session.isLoggedIn()) {
            backToLogin(SessionExpiredException("登录已过期，请重新扫码登录"))
            return
        }

        jw = jwProvider()
        explicitSync = true
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            // 非演示模式下没有会话：直接回登录页，避免拿到 FakeJw 的假数据
            if (!_state.value.demoMode && !session.isLoggedIn()) {
                explicitSync = false
                backToLogin(SessionExpiredException("登录已过期，请重新扫码登录"))
                return@launch
            }

            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    // 有号码就继续显示（刷新时不能把已有列表藏起来，那是另一种闪烁）；
                    // 没号码就等这一轮拉回来，和考试列表一起渲染
                    examNumbersReady = it.examNumbers.isNotEmpty(),
                )
            }

            try {
                val config = jw.timetableConfig()
                val current = Semester.clampWeek(
                    Semester.weekOf(LocalDate.now(), config.firstMonday),
                    config.totalWeeks,
                )
                val courses = jw.timetable(current)

                // 今天是周日 → 明天属于下一周，得单独拉一次
                val tomorrowWeek = Semester.clampWeek(
                    Semester.weekOf(LocalDate.now().plusDays(1), config.firstMonday),
                    config.totalWeeks,
                )
                val nextCourses = if (tomorrowWeek != current) {
                    runCatching { jw.timetable(tomorrowWeek) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                // 学期代码在 update 之外取：update 的 lambda 可能因 CAS 重试执行多次
                val termCode = runCatching { jw.semesterCode() }.getOrNull()

                _state.update {
                    it.copy(
                        loading = false,
                        config = config,
                        scheduleCourses = courses,
                        currentWeekCourses = courses,
                        currentCoursesWeek = current,
                        semesterCode = termCode,
                        nextWeekCourses = nextCourses,
                        currentWeek = current,
                        selectedWeek = current,
                        stale = false,
                        cachedAt = System.currentTimeMillis(),
                    )
                }
                explicitSync = false
            } catch (e: SessionExpiredException) {
                if (explicitSync) {
                    explicitSync = false
                    backToLogin(e)
                } else {
                    onSessionExpired(e)
                }
                return@launch
            } catch (e: Exception) {
                // 网络错误：有缓存就继续显示缓存，只是标记为旧
                val hasData = _state.value.hasData
                _state.update {
                    it.copy(
                        loading = false,
                        stale = hasData,
                        error = if (hasData) null else (e.message ?: "加载失败"),
                        // 这一轮不会再有号码了，放行考试列表，别一直转圈
                        examNumbersReady = true,
                    )
                }
                return@launch
            }

            // 网考号：**和考试安排一起就位**。先并发发起，等 termCourses 也拿到后一次性更新，
            // 否则考试列表先渲染、号码过一秒再「跳」出来（实测 ~1s，很像闪烁）。
            // 加超时兜底：号码拉不到就当没有，绝不能因为它让考试名单一直转圈。
            val examNumbersJob = async {
                withTimeoutOrNull(EXAM_NUMBERS_TIMEOUT_MS) {
                    runCatching { jw.examNumbers() }.getOrNull().orEmpty()
                }.orEmpty()
            }

            // 考试列表和号码在同一帧出现；成绩慢的话不等它，先让「考试安排」稳定下来
            val term = runCatching { jw.termCourses() }.getOrNull()
            val numbers = examNumbersJob.await()

            _state.update {
                it.copy(
                    // termCourses 失败时保留上一份，不能把考试列表清空
                    termCourses = term ?: it.termCourses,
                    examNumbers = if (numbers.isNotEmpty()) numbers else it.examNumbers,
                    examNumbersReady = true,
                )
            }

            try {
                val g = jw.grades(null)
                _state.update { it.copy(grades = g, gradesLoaded = true) }
            } catch (e: Exception) {
                // 成绩失败不影响课表
            }

            persistCache()
        }
    }

    /**
     * 登录过期时的处理，**分两种情况**：
     * - 本地有缓存：保留数据，打上 stale 标记，等用户点「重新登录」
     * - 本地没缓存：只能回登录页（否则是一屏空白）
     */
    /** 用户主动同步时失败：清掉登录态，直接回登录页 */
    private fun backToLogin(e: SessionExpiredException) {
        session.clear()
        _state.value = UiState(error = e.message)
    }

    private fun onSessionExpired(e: SessionExpiredException) {
        session.clear()
        val hasData = _state.value.hasData
        _state.update {
            it.copy(
                loading = false,
                stale = hasData,
                loggedIn = hasData || it.loggedIn,
                error = if (hasData) null else e.message,
                // 会话已失效，这一轮不会再拿到号码；考试列表照常显示（只是没有号码）
                examNumbersReady = true,
            )
        }
        if (!hasData) {
            _state.update { it.copy(loggedIn = false) }
        }
    }

    fun selectWeek(week: Int) {
        val s = _state.value
        val target = Semester.clampWeek(week, s.config?.totalWeeks ?: week)
        if (target == s.selectedWeek) return

        _state.update { it.copy(selectedWeek = target, weekError = null) }
        loadWeek(target)
    }

    fun prevWeek() = selectWeek(_state.value.selectedWeek - 1)

    fun nextWeek() = selectWeek(_state.value.selectedWeek + 1)

    private fun loadWeek(week: Int) {
        weekJob?.cancel()
        weekJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val courses = jw.timetable(week)
                _state.update {
                    it.copy(scheduleCourses = courses, loading = false, stale = false, weekError = null)
                }
                persistCache()
            } catch (e: CancellationException) {
                // 快速连点切周时上一个请求被取消，属正常流程，不是错误
                throw e
            } catch (e: SessionExpiredException) {
                onSessionExpired(e)
            } catch (e: Exception) {
                // 关键：失败不能表现成「该周 0 节」，否则用户会以为这周没课
                _state.update {
                    it.copy(loading = false, weekError = e.message ?: "加载失败，请检查网络后重试")
                }
        }
    }
    }

    private fun persistCache() {
        val s = _state.value
        val config = s.config ?: return
        if (s.demoMode) return      // 演示数据不要污染缓存
        if (!session.isLoggedIn()) return   // 没会话时拿到的可能是 FakeJw，更不能缓存

        cache.save(
            snapshotOf(
                config = config,
                scheduleCourses = s.scheduleWeekCourses,
                currentWeekCourses = s.currentWeekCourses,
                // 这次没拉到成绩（请求失败）→ 沿用上一份缓存，别把已有成绩冲成空
                grades = if (s.gradesLoaded) s.grades else (cache.load()?.toGrades() ?: s.grades),
                exams = s.upcomingExams,
                week = s.selectedWeek,
                currentWeek = s.currentWeek,
                savedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun logout() {
        weekJob?.cancel()
        session.clear()
        cache.clear()
        jw = jwProvider()
        _state.value = UiState()
    }
}

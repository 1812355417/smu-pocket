import { Course, Grade, TimetableConfig } from '../core/Models';
import { Semester } from '../core/Semester';
import { LocalDate } from '../core/LocalDate';
import { SessionExpiredException } from '../core/Http';
import { SessionStore } from '../core/Session';
import {
  CacheStore,
  snapshotOf,
  snapshotToConfig,
  snapshotToCourses,
  snapshotToCurrentCourses,
  snapshotToExams,
  snapshotToGrades,
} from '../core/CacheStore';
import { FakeJw, JwService } from '../data/JwService';

/** 网考号最多等多久：超时就当没有，不能让「考试安排」一直转圈 */
const EXAM_NUMBERS_TIMEOUT_MS: number = 5000;

function delay(ms: number): Promise<void> {
  return new Promise((resolve: () => void) => {
    setTimeout(resolve, ms);
  });
}

export interface TermGrades {
  term: string;
  list: Grade[];
}

/**
 * 全局状态中枢。单一 @Observed ViewModel（对应 Android 端 ui/AppViewModel.kt）。
 *
 * scheduleCourses 的语义是「**当前选中那一周**的课」——课表接口是按周拉的（zc=第几周）。
 * stale = true 表示现在显示的是**本地缓存的旧数据**，登录已过期，等用户重新扫码。
 */
@Observed
export class AppViewModel {
  // ---- 观察状态（对应 Kotlin UiState） ----
  loading: boolean = false;
  loggedIn: boolean = false;
  demoMode: boolean = false;
  config: TimetableConfig | null = null;
  /** 课表页：用户选中那一周的课（随 selectedWeek 变） */
  scheduleCourses: Course[] = [];
  /** 今日页：**真实当前周**的课（与 selectedWeek 完全无关） */
  currentWeekCourses: Course[] = [];
  /** 明日跨到下一周时，这里是下一周的课（只有今天是周日才会去拉） */
  nextWeekCourses: Course[] = [];
  /** currentWeekCourses 实际属于第几周 */
  currentCoursesWeek: number = 0;
  /** 整学期课表（含考试安排），供「近期考试」使用 */
  termCourses: Course[] = [];
  /** 考试「网考号」：课程名 → 网考号。只放内存、不落缓存 */
  examNumbers: Map<string, string> = new Map<string, string>();
  /** 网考号是否「已就绪」：false 时考试列表先等号码一起渲染，避免闪烁 */
  examNumbersReady: boolean = false;
  /** 成绩是否成功拉取过（失败时不要把缓存里的成绩覆盖成空） */
  gradesLoaded: boolean = false;
  /** 切周失败的原因；null = 没有错误 */
  weekError: string | null = null;
  /** 当前使用的学期代码，如 "202601" */
  semesterCode: string | null = null;
  grades: Grade[] = [];
  currentWeek: number = 1;
  selectedWeek: number = 1;
  error: string | null = null;
  /** 数据来自缓存（登录已过期） */
  stale: boolean = false;
  /** 缓存写入时间，用于「更新于 X 分钟前」 */
  cachedAt: number | null = null;

  // ---- 依赖与内部状态 ----
  private readonly jwProvider: () => JwService;
  private readonly session: SessionStore;
  private readonly cache: CacheStore;
  private jw: JwService;

  /** 设置页点了「同步」：这种情况下会话失效就直接回登录页 */
  private explicitSync: boolean = false;
  /** 切周请求的版本号：连点几周时，旧响应不得覆盖新响应 */
  private weekRequestSeq: number = 0;
  /** loadAll 重入保护标记 */
  private loadingAll: boolean = false;

  constructor(jwProvider: () => JwService, session: SessionStore, cache: CacheStore) {
    this.jwProvider = jwProvider;
    this.session = session;
    this.cache = cache;
    this.jw = jwProvider();
  }

  // -------------------------------------------------------------------
  // 派生状态（对应 Kotlin UiState 的计算属性）
  // -------------------------------------------------------------------

  get scheduleWeekCourses(): Course[] {
    return this.scheduleCourses.filter((c) => c.occursIn(this.selectedWeek));
  }

  get selectedWeekTotal(): number {
    return this.scheduleWeekCourses.length;
  }

  private get todayIndex(): number {
    return LocalDate.now().dayOfWeek;
  }

  get todayCourses(): Course[] {
    return this.currentWeekCourses
      .filter((c) => c.occursIn(this.currentWeek) && c.dayOfWeek === this.todayIndex)
      .sort((a, b) => a.startSection - b.startSection);
  }

  /** 每天几节课（真实当前周），用于「本周概览」条形图 */
  get dailyCounts(): number[] {
    const out: number[] = [];
    for (let day = 1; day <= 7; day++) {
      out.push(this.currentWeekCourses
        .filter((c) => c.occursIn(this.currentWeek) && c.dayOfWeek === day).length);
    }
    return out;
  }

  get weekTotal(): number {
    return this.currentWeekCourses.filter((c) => c.occursIn(this.currentWeek)).length;
  }

  private get tomorrowDate(): LocalDate {
    return LocalDate.now().plusDays(1);
  }

  /** 明天是第几周（拿不到 config 时为 null） */
  get tomorrowWeek(): number | null {
    if (this.config === null) {
      return null;
    }
    return Semester.clampWeek(
      Semester.weekOf(this.tomorrowDate, this.config.firstMonday), this.config.totalWeeks);
  }

  /** 当前周的数据是否「新鲜」（缓存可能还是上一周的） */
  get todayDataReady(): boolean {
    return this.currentCoursesWeek === this.currentWeek;
  }

  /** 明天那一周的数据是否已就绪（跨周且没拉到下一周时为 false） */
  get tomorrowDataReady(): boolean {
    const week = this.tomorrowWeek;
    if (week === null) {
      return false;
    }
    return week === this.currentWeek ? this.todayDataReady : this.nextWeekCourses.length > 0;
  }

  /** 近期考试：只要今天及以后的，按日期从近到远 */
  get upcomingExams(): Course[] {
    const today = LocalDate.now();
    return this.termCourses
      .filter((exam) => exam.isExam && (exam.date === null || !exam.date.isBefore(today)))
      .sort((a, b) => (a.date?.epochDay ?? 0) - (b.date?.epochDay ?? 0));
  }

  get tomorrowCourses(): Course[] {
    const week = this.tomorrowWeek;
    if (week === null) {
      return [];
    }
    const source = week === this.currentWeek ? this.currentWeekCourses : this.nextWeekCourses;
    return source
      .filter((c) => c.occursIn(week) && c.dayOfWeek === this.tomorrowDate.dayOfWeek)
      .sort((a, b) => a.startSection - b.startSection);
  }

  get hasData(): boolean {
    return this.scheduleCourses.length > 0 || this.currentWeekCourses.length > 0 ||
      this.grades.length > 0;
  }

  get weightedGpa(): number | null {
    const valid = this.grades.filter((g) => g.gpa !== null && g.credit > 0);
    const credits = valid.reduce((sum, g) => sum + g.credit, 0);
    if (credits <= 0) {
      return null;
    }
    return valid.reduce((sum, g) => sum + (g.gpa ?? 0) * g.credit, 0) / credits;
  }

  get weightedScore(): number | null {
    const valid = this.grades.filter((g) => g.score !== null && g.credit > 0);
    const credits = valid.reduce((sum, g) => sum + g.credit, 0);
    if (credits <= 0) {
      return null;
    }
    return valid.reduce((sum, g) => sum + (g.score ?? 0) * g.credit, 0) / credits;
  }

  get totalCredit(): number {
    return this.grades.reduce((sum, g) => sum + g.credit, 0);
  }

  /** 按学期分组，新的在前 */
  get gradesByTerm(): TermGrades[] {
    const groups = new Map<string, Grade[]>();
    for (const g of this.grades) {
      const list = groups.get(g.term) ?? [];
      list.push(g);
      groups.set(g.term, list);
    }
    const out: TermGrades[] = [];
    groups.forEach((list, term) => out.push({ term: term, list: list }));
    out.sort((a, b) => b.term.localeCompare(a.term));
    return out;
  }

  // -------------------------------------------------------------------
  // 生命周期
  // -------------------------------------------------------------------

  /**
   * 启动：先亮缓存（秒开、断网也能看），再去后台刷新。
   * 在 UI 的 aboutToAppear 里调用一次。
   */
  async restore(): Promise<void> {
    const restored = await this.restoreFromCache();
    if (restored) {
      if (this.session.isLoggedIn()) {
        this.loadAll();
      } else {
        // cookie 已经没了，但本地还有数据 —— 温和提示，不踢回登录页
        this.stale = true;
      }
    } else if (this.session.isLoggedIn()) {
      this.loggedIn = true;
      this.loadAll();
    }
  }

  /** @returns 是否成功恢复了缓存 */
  private async restoreFromCache(): Promise<boolean> {
    const snapshot = await this.cache.load();
    if (snapshot === null) {
      return false;
    }
    if ((snapshot.courses ?? []).length === 0 && (snapshot.grades ?? []).length === 0) {
      return false;
    }
    const config = snapshotToConfig(snapshot);
    if (config === null) {
      return false;
    }

    const current = Semester.clampWeek(
      Semester.weekOf(LocalDate.now(), config.firstMonday), config.totalWeeks);

    this.loggedIn = true;
    this.config = config;
    this.scheduleCourses = snapshotToCourses(snapshot);
    this.currentWeekCourses = snapshotToCurrentCourses(snapshot);
    this.currentCoursesWeek = snapshot.currentWeek;
    this.termCourses = snapshotToExams(snapshot);
    this.grades = snapshotToGrades(snapshot);
    this.selectedWeek = snapshot.week;
    this.currentWeek = current;
    this.cachedAt = snapshot.savedAt;
    // 有会话 → 马上会去刷新，考试列表先等号码一起出来；
    // 没会话（离线看缓存）→ 号码永远不会来，直接放行，别让列表一直转圈
    this.examNumbersReady = !this.session.isLoggedIn();
    return true;
  }

  // -------------------------------------------------------------------
  // 登录 / 退出
  // -------------------------------------------------------------------

  /** 登录页的「先用演示数据看看」 */
  enterDemo(): void {
    this.jw = new FakeJw();
    this.weekRequestSeq++;
    this.loggedIn = true;
    this.demoMode = true;
    this.stale = false;
    this.loadAll();
  }

  async onLoginCookies(host: string, cookieHeader: string): Promise<void> {
    await this.session.saveRawCookieHeader(`https://${host}/`, cookieHeader);
    this.jw = this.jwProvider();
  }

  /** 登录成功的唯一可靠判定：拿当前 cookie 真打一次接口 */
  async verifySession(): Promise<boolean> {
    if (!this.session.isLoggedIn()) {
      return false;
    }
    let ok = false;
    try {
      ok = await this.jw.probeSession();
    } catch (e) {
      ok = false;
    }
    if (!ok) {
      return false;
    }
    this.loggedIn = true;
    this.error = null;
    this.loadAll();
    return true;
  }

  /** 用户关掉登录页但没登录成功：清掉没认证的 cookie */
  onLoginCancelled(): void {
    if (!this.loggedIn) {
      this.session.clear();
      this.jw = this.jwProvider();
    }
  }

  /** 缓存横幅上的「重新登录」 */
  async relogin(): Promise<void> {
    this.weekRequestSeq++;
    await this.session.clear();
    this.jw = this.jwProvider();
    this.resetState();
  }

  /**
   * 设置页的「同步最新课表与成绩」。
   * 静默刷新；如果这次失败是因为会话过期，就**直接回到登录页**（让用户重新扫码）。
   */
  syncNow(): void {
    this.weekRequestSeq++;

    // 没有会话时 jwProvider() 会返回 FakeJw —— 那样"同步"会悄悄加载演示数据，
    // 界面上却标着"已接入"。宁可回登录页，也不能给假数据。
    if (!this.session.isLoggedIn()) {
      this.backToLogin(new SessionExpiredException('登录已过期，请重新扫码登录'));
      return;
    }

    this.jw = this.jwProvider();
    this.explicitSync = true;
    this.loadAll();
  }

  async logout(): Promise<void> {
    this.weekRequestSeq++;
    await this.session.clear();
    this.cache.clear();
    this.jw = this.jwProvider();
    this.resetState();
  }

  private resetState(): void {
    this.loading = false;
    this.loggedIn = false;
    this.demoMode = false;
    this.config = null;
    this.scheduleCourses = [];
    this.currentWeekCourses = [];
    this.nextWeekCourses = [];
    this.currentCoursesWeek = 0;
    this.termCourses = [];
    this.examNumbers = new Map<string, string>();
    this.examNumbersReady = false;
    this.gradesLoaded = false;
    this.weekError = null;
    this.semesterCode = null;
    this.grades = [];
    this.currentWeek = 1;
    this.selectedWeek = 1;
    this.error = null;
    this.stale = false;
    this.cachedAt = null;
  }

  // -------------------------------------------------------------------
  // 加载
  // -------------------------------------------------------------------

  private loadAll(): void {
    // 重入保护：连点同步、restore 与手动同步撞车时，后到的直接跳过，
    // 避免两路并发把课表/成绩交错覆盖（按钮虽置灰，但 restore 路径绕得过）
    if (this.loadingAll) {
      return;
    }
    this.loadingAll = true;
    this.loadAllAsync().finally(() => {
      this.loadingAll = false;
    });
  }

  private async loadAllAsync(): Promise<void> {
    // 非演示模式下没有会话：直接回登录页，避免拿到 FakeJw 的假数据
    if (!this.demoMode && !this.session.isLoggedIn()) {
      this.explicitSync = false;
      this.backToLogin(new SessionExpiredException('登录已过期，请重新扫码登录'));
      return;
    }

    this.loading = true;
    this.error = null;
    // 有号码就继续显示（刷新时不能把已有列表藏起来）；
    // 没号码就等这一轮拉回来，和考试列表一起渲染
    this.examNumbersReady = this.examNumbers.size > 0;

    let config: TimetableConfig;
    let current: number;
    try {
      config = await this.jw.timetableConfig();
      current = Semester.clampWeek(
        Semester.weekOf(LocalDate.now(), config.firstMonday), config.totalWeeks);
      const courses = await this.jw.timetable(current);

      // 今天是周日 → 明天属于下一周，得单独拉一次
      const tomorrowWeek = Semester.clampWeek(
        Semester.weekOf(LocalDate.now().plusDays(1), config.firstMonday), config.totalWeeks);
      let nextCourses: Course[] = [];
      if (tomorrowWeek !== current) {
        try {
          nextCourses = await this.jw.timetable(tomorrowWeek);
        } catch (e) {
          nextCourses = [];
        }
      }

      let termCode: string | null = null;
      try {
        termCode = await this.jw.semesterCode();
      } catch (e) {
        termCode = null;
      }

      this.loading = false;
      this.config = config;
      this.scheduleCourses = courses;
      this.currentWeekCourses = courses;
      this.currentCoursesWeek = current;
      this.semesterCode = termCode;
      this.nextWeekCourses = nextCourses;
      this.currentWeek = current;
      this.selectedWeek = current;
      this.stale = false;
      this.cachedAt = Date.now();
      this.explicitSync = false;
    } catch (e) {
      if (e instanceof SessionExpiredException) {
        if (this.explicitSync) {
          this.explicitSync = false;
          this.backToLogin(e);
        } else {
          this.onSessionExpired(e);
        }
        return;
      }
      // 网络错误：有缓存就继续显示缓存，只是标记为旧
      const hasData = this.hasData;
      this.loading = false;
      this.stale = hasData;
      this.error = hasData ? null : AppViewModel.messageOf(e as Object, '加载失败');
      // 这一轮不会再有号码了，放行考试列表，别一直转圈
      this.examNumbersReady = true;
      return;
    }

    // 网考号：**和考试安排一起就位**，否则列表先渲染、号码过一秒再「跳」出来。
    // 加超时兜底：号码拉不到就当没有，绝不能让考试名单一直转圈。
    // 三个请求并发发起：考试列表、网考号、成绩互不依赖，串行等只会白白多转两个 RTT
    const numbersPromise = this.fetchExamNumbersWithTimeout();
    const gradesPromise = this.fetchGradesQuietly();
    let term: Course[] | null = null;
    try {
      term = await this.jw.termCourses();
    } catch (e) {
      term = null;
    }
    const numbers = await numbersPromise;

    // termCourses 失败时保留上一份，不能把考试列表清空
    if (term !== null) {
      this.termCourses = term;
    }
    if (numbers.size > 0) {
      this.examNumbers = numbers;
    }
    this.examNumbersReady = true;

    // 成绩失败不影响课表（gradesLoaded 保持 false，缓存沿用上一份）
    const g = await gradesPromise;
    if (g !== null) {
      this.grades = g;
      this.gradesLoaded = true;
    }

    await this.persistCache();
  }

  private async fetchGradesQuietly(): Promise<Grade[] | null> {
    try {
      return await this.jw.grades(null);
    } catch (e) {
      return null;
    }
  }

  private async fetchExamNumbersWithTimeout(): Promise<Map<string, string>> {
    const empty = new Map<string, string>();
    try {
      const result = await Promise.race([
        this.jw.examNumbers(null),
        delay(EXAM_NUMBERS_TIMEOUT_MS).then(() => empty),
      ]);
      return result;
    } catch (e) {
      return empty;
    }
  }

  /** 用户主动同步时失败：清掉登录态，直接回登录页 */
  private backToLogin(e: SessionExpiredException): void {
    this.session.clear();
    this.resetState();
    this.error = e.message;
  }

  /**
   * 登录过期时的处理，**分两种情况**：
   * - 本地有缓存：保留数据，打上 stale 标记，等用户点「重新登录」
   * - 本地没缓存：只能回登录页（否则是一屏空白）
   */
  private onSessionExpired(e: SessionExpiredException): void {
    this.session.clear();
    const hasData = this.hasData;
    this.loading = false;
    this.stale = hasData;
    this.loggedIn = hasData || this.loggedIn;
    this.error = hasData ? null : e.message;
    // 会话已失效，这一轮不会再拿到号码；考试列表照常显示（只是没有号码）
    this.examNumbersReady = true;
    if (!hasData) {
      this.loggedIn = false;
    }
  }

  // -------------------------------------------------------------------
  // 切周
  // -------------------------------------------------------------------

  selectWeek(week: number): void {
    const target = Semester.clampWeek(week, this.config?.totalWeeks ?? week);
    if (target === this.selectedWeek) {
      return;
    }
    this.selectedWeek = target;
    this.weekError = null;
    this.loadWeek(target);
  }

  prevWeek(): void {
    this.selectWeek(this.selectedWeek - 1);
  }

  nextWeek(): void {
    this.selectWeek(this.selectedWeek + 1);
  }

  private loadWeek(week: number): void {
    const seq = ++this.weekRequestSeq;
    this.loadWeekAsync(week, seq);
  }

  private async loadWeekAsync(week: number, seq: number): Promise<void> {
    this.loading = true;
    this.error = null;
    try {
      const courses = await this.jw.timetable(week);
      // 连点几周时旧响应不得覆盖新响应
      if (seq !== this.weekRequestSeq) {
        return;
      }
      this.scheduleCourses = courses;
      this.loading = false;
      this.stale = false;
      this.weekError = null;
      await this.persistCache();
    } catch (e) {
      if (seq !== this.weekRequestSeq) {
        return;
      }
      if (e instanceof SessionExpiredException) {
        this.onSessionExpired(e);
        return;
      }
      // 关键：失败不能表现成「该周 0 节」，否则用户会以为这周没课
      this.loading = false;
      this.weekError = AppViewModel.messageOf(e as Object, '加载失败，请检查网络后重试');
    }
  }

  // -------------------------------------------------------------------
  // 缓存
  // -------------------------------------------------------------------

  private async persistCache(): Promise<void> {
    const config = this.config;
    if (config === null) {
      return;
    }
    if (this.demoMode) {
      return; // 演示数据不要污染缓存
    }
    if (!this.session.isLoggedIn()) {
      return; // 没会话时拿到的可能是 FakeJw，更不能缓存
    }

    let grades = this.grades;
    if (!this.gradesLoaded) {
      // 这次没拉到成绩（请求失败）→ 沿用上一份缓存，别把已有成绩冲成空
      const previous = await this.cache.load();
      if (previous !== null) {
        grades = snapshotToGrades(previous);
      }
    }

    await this.cache.save(snapshotOf(
      config,
      this.scheduleWeekCourses,
      this.currentWeekCourses,
      grades,
      this.upcomingExams,
      this.selectedWeek,
      this.currentWeek,
      Date.now(),
    ));
  }

  private static messageOf(e: Object, fallback: string): string {
    if (e instanceof Error && e.message.length > 0) {
      return e.message;
    }
    return fallback;
  }
}

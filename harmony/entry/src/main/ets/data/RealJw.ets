import { Course, Grade, TimetableConfig } from '../core/Models';
import { DefaultPeriods } from '../core/Periods';
import { JwHttp, HttpException, SessionExpiredException } from '../core/Http';
import { LocalDate } from '../core/LocalDate';
import { Semester } from '../core/Semester';
import { TermCode } from '../core/TermCode';
import { currentWeek, inferFirstMonday, parseWeekList } from '../core/TimeUtils';
import { CourseData, onlineExamNumber } from './JwModels';
import { JwJson } from './JwJson';
import { toCourse, toGrade } from './Mappers';
import { collectPages, Page } from './Pagination';
import { TermResolver } from './TermResolver';
import { BASE_URL, JwService } from './JwService';

const MIN_WEEKS: number = 16;
const MAX_WEEKS: number = 30;
const DEFAULT_TOTAL_WEEKS: number = 20;

/**
 * 真实教务实现（对应 Android 端 data/RealJw.kt）。结论全部来自真机实测：
 *
 * 1. **zc 就是「第几周」**：zc=9 → 第 9 周；zc= 空 → 整学期总表；zc=201 → 空数组。
 * 2. **Referer 必须带**，否则 {"code":-401,"message":"疑似非法请求…"}。
 * 3. **未登录是 HTTP 200 + code:-401**，要翻译成 SessionExpiredException。
 * 4. 第 1 周的课表带 qsrq，「第一周周一」可以从它反推，不需要校历接口。
 * 5. **学期代码不写死**：交给 TermResolver 动态解析（缓存 → 日期推算 → 常量）。
 */
export class RealJw implements JwService {
  private readonly http: JwHttp;
  private readonly terms: TermResolver;
  private readonly manualFirstMonday: LocalDate | null;

  private anchor: TimetableConfig | null = null;
  /** 整学期总表缓存：bootstrap 时拉过一次，别重复请求 */
  private termRows: CourseData[] | null = null;
  /** 自举时已拉过的周次数据：给紧接着的首次加载复用一次，避免重复请求 */
  private readonly prefetchedWeeks: Map<number, CourseData[]> = new Map();
  /** 网考号缓存：课程名 → 网考号。一次进程内只拉一次 */
  private examNumberCache: Map<string, string> | null = null;
  /** 解析出来的学期代码，进程内缓存 */
  private resolvedTerm: string | null = null;

  constructor(http: JwHttp, terms: TermResolver, manualFirstMonday: LocalDate | null = null) {
    this.http = http;
    this.terms = terms;
    this.manualFirstMonday = manualFirstMonday;
  }

  private term(): string {
    if (this.resolvedTerm === null) {
      this.resolvedTerm = this.terms.resolve().code;
    }
    return this.resolvedTerm;
  }

  /** 探针专用：用缓存或日期推算的学期，不触发任何额外请求 */
  private termForProbe(): string {
    return this.resolvedTerm ?? this.terms.peek();
  }

  semesterCode(): Promise<string | null> {
    return Promise.resolve(this.term());
  }

  timetableConfig(): Promise<TimetableConfig> {
    return this.bootstrap();
  }

  async timetable(week: number | null): Promise<Course[]> {
    const config = await this.bootstrap();
    const target = Semester.clampWeek(
      week ?? currentWeek(LocalDate.now(), config.firstMonday),
      config.totalWeeks,
    );
    const rows = await this.fetchWeek(target);
    const out: Course[] = [];
    for (const row of rows) {
      const course = toCourse(row, target, null);
      if (course !== null) {
        out.push(course);
      }
    }
    return out;
  }

  /**
   * 整学期课表（含考试）。数据来自 bootstrap 已经拉过的总表，通常不产生新请求。
   * 日期用「第一周周一 + zc + xq」推算（总表没有 qsrq）。
   */
  async termCourses(): Promise<Course[]> {
    const config = await this.bootstrap();
    const rows = await this.fetchAllWeeks();
    const out: Course[] = [];
    for (const row of rows) {
      const course = toCourse(row, null, config.firstMonday);
      if (course !== null) {
        out.push(course);
      }
    }
    return out;
  }

  /**
   * 考试网考号：课程名 → 网考号（xskcrw/skrwDatas）。
   * 纯展示增强：任何失败都退化成空表，「考试安排」照常只显示课名。
   */
  async examNumbers(term: string | null): Promise<Map<string, string>> {
    if (this.examNumberCache !== null) {
      return this.examNumberCache;
    }

    const targetTerm = term ?? this.term();
    const rows = await collectPages(async (page: number) => {
      const parsed = JwJson.examTasks(await this.postForm('new/student/xskcrw/skrwDatas', {
        'xnxqdm': targetTerm,
        'page': `${page}`,
        'rows': '200',
      }));
      throwIfAuthFailed(parsed.code, parsed.message);
      if (!parsed.ok) {
        throw new HttpException(0, `教务返回 code=${parsed.code}`);
      }
      return new Page(parsed.rows, parsed.total);
    });

    const collected = new Map<string, string>();
    for (const row of rows) {
      const name = (row.kcmc ?? '').trim();
      const number = onlineExamNumber(row);
      if (name.length > 0 && number.length > 0) {
        collected.set(name, number);
      }
    }

    // 一条都没拿到就先别缓存，下次进「今日」还能再试一次
    if (collected.size > 0) {
      this.examNumberCache = collected;
    }
    return collected;
  }

  async grades(term: string | null): Promise<Grade[]> {
    // 空学期查询全部成绩。分页完成后才映射和发布，避免静默保存半份数据。
    const rows = await collectPages(async (page: number) => {
      const parsed = JwJson.scores(await this.postForm('new/student/xskccj/kccjDatas', {
        'xnxqdm': term ?? '',
        'source': 'kccjlist',
        'ismax': '1',
        'page': `${page}`,
        'rows': '200',
      }));
      throwIfAuthFailed(parsed.code, parsed.message);
      if (parsed.code !== 0) {
        throw new HttpException(0, `教务返回 code=${parsed.code}`);
      }
      return new Page(parsed.rows, parsed.total);
    });

    const out: Grade[] = [];
    for (const row of rows) {
      const grade = toGrade(row);
      if (grade !== null) {
        out.push(grade);
      }
    }
    return out;
  }

  /**
   * 一次请求判断登录态：zc=1 是最便宜的可用请求。
   * 不走 bootstrap（那要 2~3 个请求），因为扫码时会每 2 秒探一次。
   */
  async probeSession(): Promise<boolean> {
    const body = await this.postForm('new/student/xsgrkb/getCalendarWeekDatas', {
      'xnxqdm': this.termForProbe(),
      'zc': '1',
    });
    return JwJson.timetable(body).code === 0;
  }

  // ------------------------------------------------------------------
  // 自举：第一周周一 + 总周数
  // ------------------------------------------------------------------

  private async bootstrap(): Promise<TimetableConfig> {
    if (this.anchor !== null) {
      return this.anchor;
    }

    if (this.manualFirstMonday !== null) {
      return this.configOf(this.manualFirstMonday, DEFAULT_TOTAL_WEEKS);
    }

    const firstMonday = await this.deriveFirstMonday();
    if (firstMonday === null) {
      throw new HttpException(0, '无法从教务课表推算第一周周一，请稍后重试');
    }

    let totalWeeks = DEFAULT_TOTAL_WEEKS;
    try {
      totalWeeks = await this.deriveTotalWeeks();
    } catch (e) {
      if (e instanceof SessionExpiredException) {
        throw e;
      }
      // 总周数推不出来就用默认值，不阻塞课表
    }

    return this.configOf(firstMonday, totalWeeks);
  }

  /**
   * 查第 1 周（zc=1），每行都带 qsrq；
   * 该行所在周的周一 = qsrq - (xq-1) 天，取众数就是第一周周一。
   * 拉到的数据留给首次「当前周」加载复用（第 1 周开学时正好少一次请求）。
   */
  private async deriveFirstMonday(): Promise<LocalDate | null> {
    const rows = await this.fetchWeek(1);
    this.prefetchedWeeks.set(1, rows);
    return inferFirstMonday(rows, 1);
  }

  /** zc 传空 → 整学期总表 → 所有周次串里的最大周次就是总周数 */
  private async deriveTotalWeeks(): Promise<number> {
    const rows = await this.fetchAllWeeks();
    let max = 0;
    for (const row of rows) {
      for (const w of parseWeekList(row.zc)) {
        if (w > max) {
          max = w;
        }
      }
    }
    if (max === 0) {
      return DEFAULT_TOTAL_WEEKS;
    }
    return Math.min(Math.max(max, MIN_WEEKS), MAX_WEEKS);
  }

  private configOf(firstMonday: LocalDate, totalWeeks: number): TimetableConfig {
    const name = TermCode.displayName(this.term());
    this.anchor = new TimetableConfig(name, firstMonday, totalWeeks, DefaultPeriods.ALL);
    return this.anchor;
  }

  // ------------------------------------------------------------------
  // 底层请求
  // ------------------------------------------------------------------

  private async fetchWeek(week: number): Promise<CourseData[]> {
    // 只复用一次：之后再次切到这一周仍然拉最新数据（课表会临时调整）
    const prefetched = this.prefetchedWeeks.get(week);
    if (prefetched !== undefined) {
      this.prefetchedWeeks.delete(week);
      return prefetched;
    }
    const body = await this.postForm('new/student/xsgrkb/getCalendarWeekDatas', {
      'xnxqdm': this.term(),
      'zc': `${week}`,
    });
    return this.decodeTimetable(body);
  }

  private async fetchAllWeeks(): Promise<CourseData[]> {
    if (this.termRows !== null) {
      return this.termRows;
    }
    const body = await this.postForm('new/student/xsgrkb/getCalendarWeekDatas', {
      'xnxqdm': this.term(),
      'zc': '',
    });
    this.termRows = this.decodeTimetable(body);
    return this.termRows;
  }

  private decodeTimetable(body: string): CourseData[] {
    const parsed = JwJson.timetable(body);
    throwIfAuthFailed(parsed.code, parsed.message);
    if (!parsed.ok) {
      throw new HttpException(0, `教务返回 code=${parsed.code}，message=${parsed.message}`);
    }
    return parsed.data;
  }

  private postForm(path: string, form: Record<string, string>): Promise<string> {
    return this.http.postForm(BASE_URL + path, form);
  }
}

/** 教务用 code = -401 表示未登录（HTTP 仍是 200），统一翻译成可捕获的异常 */
function throwIfAuthFailed(code: number, message: string): void {
  if (code === -401) {
    throw new SessionExpiredException(
      message.length > 0 ? message : '登录态失效，请重新扫码登录');
  }
}

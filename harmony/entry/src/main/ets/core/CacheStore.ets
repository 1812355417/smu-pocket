import { common } from '@kit.AbilityKit';
import { EncryptedStore } from './EncryptedStore';
import { Course, Grade, Period, TimetableConfig, TOTAL_SECTIONS } from './Models';
import { DefaultPeriods } from './Periods';
import { LocalDate } from './LocalDate';

/**
 * 本地缓存：把最后一次成功拿到的**课表 + 成绩 + 考试**序列化后加密保存。
 * （对应 Android 端 core/CacheStore.kt）
 *
 * 两份课表：courses 是课表页当时选中的一周；currentCourses 是**真实当前周**
 * 的课（今日页用）。「今日」必须和「课表选中周」解耦。
 */

export interface CachedPeriod {
  index: number;
  start: string;
  end: string;
}

export interface CachedConfig {
  termName: string;
  firstMonday: string;
  totalWeeks: number;
  periods: CachedPeriod[];
}

export interface CachedCourse {
  name: string;
  teacher: string | null;
  room: string | null;
  dayOfWeek: number;
  startSection: number;
  endSection: number;
  weeks: number[];
  content: string | null;
  date: string | null;
  timeLabel: string | null;
  colorHex: string | null;
  isSelfStudy: boolean;
}

export interface CachedGrade {
  course: string;
  term: string;
  score: number | null;
  level: string | null;
  gpa: number | null;
  credit: number;
  kind: string | null;
}

export interface CachedSnapshot {
  version: number;
  /** 保存时刻（毫秒） */
  savedAt: number;
  /** 课表页缓存的是第几周 */
  week: number;
  courses: CachedCourse[];
  /** 今日页缓存的是哪一周（真实当前周） */
  currentWeek: number;
  currentCourses: CachedCourse[];
  config: CachedConfig | null;
  grades: CachedGrade[];
  /** 近期考试（离线时也能看到） */
  exams: CachedCourse[];
}

export class CacheStore {
  private readonly storage: EncryptedStore;

  constructor(context: common.Context) {
    this.storage = new EncryptedStore(context, 'snapshot');
  }

  async load(): Promise<CachedSnapshot | null> {
    const raw = await this.storage.read();
    if (raw === null) {
      return null;
    }
    try {
      return JSON.parse(raw) as CachedSnapshot;
    } catch (e) {
      this.storage.clear();
      return null;
    }
  }

  async save(snapshot: CachedSnapshot): Promise<void> {
    try {
      await this.storage.write(JSON.stringify(snapshot));
    } catch (e) {
      // 写失败不影响主流程
    }
  }

  clear(): void {
    this.storage.clear();
  }
}

// ---------------------------------------------------------------------
// 领域模型 <-> 缓存模型
// ---------------------------------------------------------------------

function toDomainCourses(list: CachedCourse[]): Course[] {
  return list.map((c, index) => {
    const course = new Course();
    course.id = `cache_${c.dayOfWeek}_${c.startSection}_${index}`;
    course.name = c.name;
    course.teacher = c.teacher;
    course.room = c.room;
    course.dayOfWeek = c.dayOfWeek;
    course.startSection = c.startSection;
    course.endSection = c.endSection;
    course.weeks = new Set<number>(c.weeks);
    course.content = c.content;
    course.date = LocalDate.parse(c.date);
    course.timeLabel = c.timeLabel;
    course.colorHex = c.colorHex;
    course.isSelfStudy = c.isSelfStudy;
    return course;
  });
}

function toCacheCourses(list: Course[]): CachedCourse[] {
  return list.map((c) => {
    const cached: CachedCourse = {
      name: c.name,
      teacher: c.teacher,
      room: c.room,
      dayOfWeek: c.dayOfWeek,
      startSection: c.startSection,
      endSection: c.endSection,
      weeks: Array.from(c.weeks).sort((a, b) => a - b),
      content: c.content,
      date: c.date !== null ? c.date.toString() : null,
      timeLabel: c.timeLabel,
      colorHex: c.colorHex,
      isSelfStudy: c.isSelfStudy,
    };
    return cached;
  });
}

export function snapshotToConfig(snapshot: CachedSnapshot): TimetableConfig | null {
  const c = snapshot.config;
  if (c === null) {
    return null;
  }
  const monday = LocalDate.parse(c.firstMonday);
  if (monday === null) {
    return null;
  }
  const cached = (c.periods ?? []).map((p) => new Period(p.index, p.start, p.end));
  return new TimetableConfig(
    c.termName,
    monday,
    c.totalWeeks,
    // 旧版本缓存里作息可能只有 10 节：缺 11~14 节就用默认表补齐
    cached.length >= TOTAL_SECTIONS ? cached : DefaultPeriods.ALL,
  );
}

/** 课表页那份（选中周） */
export function snapshotToCourses(snapshot: CachedSnapshot): Course[] {
  return toDomainCourses(snapshot.courses ?? []);
}

/** 今日页那份（真实当前周） */
export function snapshotToCurrentCourses(snapshot: CachedSnapshot): Course[] {
  const current = snapshot.currentCourses ?? [];
  return toDomainCourses(current.length > 0 ? current : (snapshot.courses ?? []));
}

export function snapshotToExams(snapshot: CachedSnapshot): Course[] {
  const exams = toDomainCourses(snapshot.exams ?? []);
  for (const e of exams) {
    e.isExam = true;
  }
  return exams;
}

export function snapshotToGrades(snapshot: CachedSnapshot): Grade[] {
  return (snapshot.grades ?? []).map((g) => {
    const grade = new Grade();
    grade.course = g.course;
    grade.term = g.term;
    grade.score = g.score;
    grade.level = g.level;
    grade.gpa = g.gpa;
    grade.credit = g.credit;
    grade.kind = g.kind;
    return grade;
  });
}

export function snapshotOf(
  config: TimetableConfig,
  scheduleCourses: Course[],
  currentWeekCourses: Course[],
  grades: Grade[],
  exams: Course[],
  week: number,
  currentWeek: number,
  savedAt: number,
): CachedSnapshot {
  const cachedConfig: CachedConfig = {
    termName: config.termName,
    firstMonday: config.firstMonday.toString(),
    totalWeeks: config.totalWeeks,
    periods: config.periods.map((p) => {
      const cp: CachedPeriod = { index: p.index, start: p.start, end: p.end };
      return cp;
    }),
  };
  return {
    version: 1,
    savedAt: savedAt,
    week: week,
    courses: toCacheCourses(scheduleCourses),
    currentWeek: currentWeek,
    currentCourses: toCacheCourses(currentWeekCourses),
    config: cachedConfig,
    grades: grades.map((g) => {
      const cg: CachedGrade = {
        course: g.course,
        term: g.term,
        score: g.score,
        level: g.level,
        gpa: g.gpa,
        credit: g.credit,
        kind: g.kind,
      };
      return cg;
    }),
    exams: toCacheCourses(exams),
  };
}

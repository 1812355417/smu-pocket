import { Course, Grade, TimetableConfig } from '../core/Models';
import { DefaultPeriods } from '../core/Periods';
import { LocalDate } from '../core/LocalDate';

/** 扫码登录入口页 */
export const LOGIN_URL: string = 'https://zhjw.smu.edu.cn/';

/** 教务 baseUrl（必须以 / 结尾） */
export const BASE_URL: string = 'https://zhjw.smu.edu.cn/';

/**
 * 教务服务契约。真实实现见 RealJw，演示实现见 FakeJw。
 * （对应 Android 端 data/Jw.kt）
 */
export interface JwService {
  /** 学期元信息：学期名、第一周周一、总周数、作息时间 */
  timetableConfig(): Promise<TimetableConfig>;

  /** 课表。week 为 null = 当前周（真实实现）/ 整学期（演示实现） */
  timetable(week: number | null): Promise<Course[]>;

  /** 成绩。term 为 null 表示全部学期 */
  grades(term: string | null): Promise<Grade[]>;

  /** 整学期课表（含考试安排），供「近期考试」使用 */
  termCourses(): Promise<Course[]>;

  /** 考试「网考号」：课程名 → 网考号。拿不到就返回空表 */
  examNumbers(term: string | null): Promise<Map<string, string>>;

  /** 当前使用的学期代码（如 "202601"）。演示实现返回 null */
  semesterCode(): Promise<string | null>;

  /** 用一次轻量请求判断会话是否有效（扫码登录轮询用） */
  probeSession(): Promise<boolean>;
}

function allWeeks(): Set<number> {
  const s = new Set<number>();
  for (let i = 1; i <= 16; i++) {
    s.add(i);
  }
  return s;
}

function oddWeeks(): Set<number> {
  const s = new Set<number>();
  for (let i = 1; i <= 16; i += 2) {
    s.add(i);
  }
  return s;
}

function firstHalf(): Set<number> {
  const s = new Set<number>();
  for (let i = 1; i <= 9; i++) {
    s.add(i);
  }
  return s;
}

function demo(id: string, name: string, teacher: string | null, room: string | null,
  dayOfWeek: number, startSection: number, endSection: number, weeks: Set<number>,
  content: string | null = null, isSelfStudy: boolean = false, isExam: boolean = false,
  date: LocalDate | null = null): Course {
  const c = new Course();
  c.id = id;
  c.name = name;
  c.teacher = teacher;
  c.room = room;
  c.dayOfWeek = dayOfWeek;
  c.startSection = startSection;
  c.endSection = endSection;
  c.weeks = weeks;
  c.content = content;
  c.isSelfStudy = isSelfStudy;
  c.isExam = isExam;
  c.date = date;
  return c;
}

function demoCourses(): Course[] {
  const all = allWeeks();
  const odd = oddWeeks();
  const half = firstHalf();
  return [
    demo('c1', '系统解剖学', '王老师', '教学馆 301', 1, 1, 2, all, '纵隔与胸膜'),
    demo('c2', '生理学', '李老师', '教学馆 205', 1, 3, 4, half, '血液循环'),
    demo('c3', '大学英语', '陈老师', '外语楼 402', 2, 1, 2, all),
    demo('c4', '诊断学', '周老师', '临床楼 114', 2, 5, 6, odd, '体格检查'),
    demo('c5', '生物化学', '赵老师', '基础楼 502', 3, 3, 4, all, '糖代谢'),
    demo('c6', '病理学', '孙老师', '基础楼 108', 4, 1, 3, all, '炎症与修复'),
    demo('c7', '医学统计学', '郑老师', '计算机教室 3', 5, 5, 6, all),
    demo('c8', '体育（游泳）', '刘老师', '体育馆', 6, 1, 2, odd),
    demo('c9', '医学统计学', '郑老师', null, 1, 5, 6, all, '自主学习：SPSS 上机练习', true),
    demo('c10', '马克思主义基本原理', '吴老师', '教学馆 101', 2, 11, 12, all, '专题讨论'),
    demo('c11', '医学文献检索', '何老师', '图书馆 401', 4, 11, 12, all),
    demo('c12', '临床技能训练', '林老师', '临床技能中心', 6, 13, 14, odd, '夜间开放练习'),
  ];
}

function demoExams(): Course[] {
  const today = LocalDate.now();
  return [
    demo('e1', '药理学', null, '304机房', 1, 3, 4, new Set<number>([14]), null, false, true,
      today.plusDays(6)),
    demo('e2', '临床诊断学', null, '诊断学实验室(实验楼)(本部)', 4, 8, 9, new Set<number>([17]),
      null, false, true, today.plusDays(21)),
  ];
}

function demoGrades(): Grade[] {
  const make = (course: string, term: string, score: number | null, level: string | null,
    gpa: number | null, credit: number, kind: string): Grade => {
    const g = new Grade();
    g.course = course;
    g.term = term;
    g.score = score;
    g.level = level;
    g.gpa = gpa;
    g.credit = credit;
    g.kind = kind;
    return g;
  };
  return [
    make('系统解剖学', '2025-2026-2', 91.0, null, 4.1, 4.0, '必修'),
    make('生理学', '2025-2026-2', 85.0, null, 3.5, 3.5, '必修'),
    make('大学英语', '2025-2026-2', 88.0, null, 3.8, 3.0, '必修'),
    make('医学统计学', '2025-2026-2', 79.0, null, 2.9, 2.0, '必修'),
    make('医德修养', '2025-2026-2', null, '优秀', 4.5, 1.0, '选修'),
    make('生物化学', '2025-2026-1', 93.0, null, 4.3, 4.0, '必修'),
  ];
}

/**
 * 演示实现：让 UI 不依赖接口也能跑起来、能截图、能讨论交互。
 * （对应 Android 端 FakeJw）
 */
export class FakeJw implements JwService {
  private readonly courses: Course[] = demoCourses();
  private readonly exams: Course[] = demoExams();

  timetableConfig(): Promise<TimetableConfig> {
    // 演示用：把「第一周周一」定成 5 周前，这样「当前周」= 第 6 周
    const today = LocalDate.now();
    const monday = today.minusDays(today.dayOfWeek - 1);
    return Promise.resolve(new TimetableConfig(
      '2025-2026 学年第二学期（演示）',
      monday.plusWeeks(-5),
      20,
      DefaultPeriods.ALL,
    ));
  }

  timetable(week: number | null): Promise<Course[]> {
    return Promise.resolve(this.courses);
  }

  grades(term: string | null): Promise<Grade[]> {
    return Promise.resolve(demoGrades());
  }

  termCourses(): Promise<Course[]> {
    return Promise.resolve(this.courses.concat(this.exams));
  }

  /** 演示用网考号：和 demoExams 的课名对上，方便截图看效果 */
  examNumbers(term: string | null): Promise<Map<string, string>> {
    const map = new Map<string, string>();
    map.set('药理学', '7667619');
    map.set('临床诊断学', '7667621');
    return Promise.resolve(map);
  }

  semesterCode(): Promise<string | null> {
    return Promise.resolve(null);
  }

  probeSession(): Promise<boolean> {
    return Promise.resolve(false);
  }
}

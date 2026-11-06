import { LocalDate } from './LocalDate';

/**
 * 领域模型。字段名按「语义」定，和教务的 40 个字符段解耦 ——
 * 教务改字段名只需改 data/Mappers.ets 一个文件。
 * （与 Android 端 core/Models.kt 一一对应）
 */

/** 课表固定渲染的节次数：1..14。晚课排到 11~14 节也不能丢。 */
export const TOTAL_SECTIONS: number = 14;

export class Course {
  id: string = '';
  name: string = '';
  teacher: string | null = null;
  room: string | null = null;
  /** 1 = 周一 … 7 = 周日 */
  dayOfWeek: number = 1;
  startSection: number = 1;
  endSection: number = 1;
  /** 出现在哪几周。按周查询时就是 { 当前周 } */
  weeks: Set<number> = new Set<number>();
  /** 上课内容，只有按周查询才有 */
  content: string | null = null;
  /** 真实日期 */
  date: LocalDate | null = null;
  /** 教务给的上课时刻，如 "08:30-10:00"（来自 qssj/jssj，比作息表准） */
  timeLabel: string | null = null;
  /** 教务给的课程配色，如 "#1f86c7" */
  colorHex: string | null = null;
  /** 自主学习（jxhjmc = 自主学习）：不需要去教室 */
  isSelfStudy: boolean = false;
  /** 考试环节（jxhjmc = 考试（理论）/考试（实操）） */
  isExam: boolean = false;

  get sectionSpan(): number {
    return this.endSection - this.startSection + 1;
  }

  occursIn(week: number): boolean {
    return this.weeks.has(week);
  }
}

export class Grade {
  course: string = '';
  term: string = '';
  /** 百分制分数。等级制课程（优/良/中）这里可能是 null */
  score: number | null = null;
  level: string | null = null;
  gpa: number | null = null;
  credit: number = 0;
  kind: string | null = null;
}

export class Period {
  index: number;
  start: string;
  end: string;

  constructor(index: number, start: string, end: string) {
    this.index = index;
    this.start = start;
    this.end = end;
  }

  get label(): string {
    return `${this.start}-${this.end}`;
  }
}

/**
 * 课表渲染所需的元信息。
 * firstMonday 由「第 1 周查询的 qsrq」反推；periods 接口不给，用默认值。
 */
export class TimetableConfig {
  termName: string;
  firstMonday: LocalDate;
  totalWeeks: number;
  periods: Period[];

  constructor(termName: string, firstMonday: LocalDate, totalWeeks: number, periods: Period[]) {
    this.termName = termName;
    this.firstMonday = firstMonday;
    this.totalWeeks = totalWeeks;
    this.periods = periods;
  }
}

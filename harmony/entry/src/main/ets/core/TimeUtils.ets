import { LocalDate } from './LocalDate';
import { Semester } from './Semester';
import { CourseData } from '../data/JwModels';

/**
 * 时间工具：从课表数据反推学期起点，再换算周次和请求参数。
 * （与 Android 端 core/TimeUtils.kt 一一对应）
 *
 * 背景：南医大**没有独立的校历接口**，但按周查询的课表里带 qsrq（真实日期）。
 * 「第一周周一」可以从课表本身反推：该周的周一 = qsrq - (xq - 1) 天，
 * 第一周周一 = 该周的周一 - (zc - 1) 周。
 */

const TIME_SUFFIX: string = ' 00:00:00';

/** 教务要求的参数格式："2026-10-26 00:00:00" */
export function formatPayload(date: LocalDate): string {
  return date.toString() + TIME_SUFFIX;
}

/** 解析周次字段："12,13"（整学期总表）或 "8"（按周查询） */
export function parseWeekList(raw: string | null | undefined, totalWeeks: number = 30): Set<number> {
  return Semester.parseWeeks(raw ?? '', totalWeeks);
}

/**
 * 反推第一周周一。每条记录都投票，取众数，比赌单条记录稳
 * （单条可能是临时调课、数据录入错误）。
 *
 * @param queriedWeek 调用方自己知道的周次（按周查询的响应里可能没有 zc）
 * 返回 null 表示反推不出来（通常是整学期总表：既没 qsrq 也没 zc）。
 */
export function inferFirstMonday(records: CourseData[], queriedWeek: number | null = null): LocalDate | null {
  const votes = new Map<number, number>();

  for (const record of records) {
    const date = LocalDate.parse(record.qsrq);
    if (date === null) {
      continue;
    }
    const day = record.xq;
    if (day < 1 || day > 7) {
      continue;
    }

    const mondayOfThatWeek = date.minusDays(day - 1);

    const weeksFromData = parseWeekList(record.zc);
    let weeks: number[] = [];
    if (weeksFromData.size > 0) {
      weeks = Array.from(weeksFromData);
    } else if (queriedWeek !== null) {
      weeks = [queriedWeek];
    }

    for (const week of weeks) {
      const candidate = mondayOfThatWeek.plusWeeks(1 - week).epochDay;
      votes.set(candidate, (votes.get(candidate) ?? 0) + 1);
    }
  }

  let best: number | null = null;
  let bestCount = -1;
  votes.forEach((count, day) => {
    if (count > bestCount) {
      bestCount = count;
      best = day;
    }
  });
  return best === null ? null : LocalDate.fromEpochDay(best);
}

/** 当前是第几周。开学前返回 ≤ 0，调用方自己决定要不要钳住 */
export function currentWeek(today: LocalDate, firstMonday: LocalDate): number {
  return Semester.weekOf(today, firstMonday);
}

/**
 * 一周的边界。d1 传周一；d2 抓包里是**下周一**（正好差 7 天）。
 */
export class WeekBounds {
  readonly monday: LocalDate;
  readonly sunday: LocalDate;
  readonly nextMonday: LocalDate;

  constructor(week: number, firstMonday: LocalDate) {
    this.monday = firstMonday.plusWeeks(week - 1);
    this.sunday = this.monday.plusDays(6);
    this.nextMonday = this.monday.plusDays(7);
  }

  /** 周一，用作 d1 */
  get d1(): string {
    return formatPayload(this.monday);
  }

  /** 周日，用作 d2（若服务端按「左闭右闭」解析） */
  get d2Sunday(): string {
    return formatPayload(this.sunday);
  }

  /** 下周一，用作 d2（实测抓包是这个） */
  get d2NextMonday(): string {
    return formatPayload(this.nextMonday);
  }

  get label(): string {
    return `${this.monday.month}/${this.monday.day} ~ ${this.sunday.month}/${this.sunday.day}`;
  }
}

/**
 * 解析节次串 "0809" / "060708" → [8, 9] / [6, 8]。
 * 每两位一节。奇数长度直接放弃（宁可返回 null 让上层兜底，也不猜出个错范围）。
 */
export function parseJcdm(jcdm: string | null | undefined): number[] | null {
  const raw = (jcdm ?? '').trim();
  if (raw.length === 0 || raw.length % 2 !== 0 || !/^\d+$/.test(raw)) {
    return null;
  }
  const numbers: number[] = [];
  for (let i = 0; i < raw.length; i += 2) {
    numbers.push(Number(raw.substring(i, i + 2)));
  }
  if (numbers.length === 0) {
    return null;
  }
  return [Math.min(...numbers), Math.max(...numbers)];
}

/**
 * 统一取节次：优先 jcdm（按周数据），退回 ps/pe（整学期总表）。
 * 返回 [起始节, 结束节]。
 */
export function sectionsOf(record: CourseData): number[] | null {
  const byJcdm = parseJcdm(record.jcdm);
  if (byJcdm !== null) {
    return byJcdm;
  }
  const ps = (record.ps ?? '').trim();
  if (ps.length === 0) {
    return null;
  }
  const start = Number(ps);
  if (!Number.isInteger(start)) {
    return null;
  }
  const pe = (record.pe ?? '').trim();
  const end = pe.length > 0 && Number.isInteger(Number(pe)) ? Number(pe) : start;
  return end >= start ? [start, end] : [start, start];
}

/** "09:50-11:15"，比节次更直观，用于课程卡片副标题 */
export function clockRange(record: CourseData): string | null {
  const start = (record.qssj ?? '').substring(0, 5);
  const end = (record.jssj ?? '').substring(0, 5);
  if (start.length !== 5 || end.length !== 5) {
    return null;
  }
  return `${start}-${end}`;
}

/** 人类可读的相对时间："刚刚 / 12 分钟前 / 3 小时前 / 2 天前" */
export function humanizeAgo(millis: number): string {
  const minutes = Math.floor((Date.now() - millis) / 60000);
  if (minutes < 1) {
    return '刚刚';
  }
  if (minutes < 60) {
    return `${minutes} 分钟前`;
  }
  if (minutes < 60 * 24) {
    return `${Math.floor(minutes / 60)} 小时前`;
  }
  return `${Math.floor(minutes / (60 * 24))} 天前`;
}

/**
 * 由「第一周周一 + 第几周 + 星期几」算出具体日期。
 * 整学期总表里没有 qsrq，但每行都有 zc（单周）和 xq，
 * 所以考试这类「只在某一周出现一次」的记录可以精确还原出日期。
 */
export function dateOf(firstMonday: LocalDate, week: number, dayOfWeek: number): LocalDate {
  return firstMonday.plusWeeks(week - 1).plusDays(dayOfWeek - 1);
}

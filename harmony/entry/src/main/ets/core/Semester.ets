import { LocalDate } from './LocalDate';

/**
 * 学期 / 周次换算。纯函数，无依赖。
 * （与 Android 端 core/Semester.kt 一一对应）
 */
export class Semester {

  /** 日期属于第几周。开学前返回 ≤ 0 */
  static weekOf(date: LocalDate, firstMonday: LocalDate): number {
    const days = date.epochDay - firstMonday.epochDay;
    return Math.floor(days / 7) + 1;
  }

  static clampWeek(week: number, totalWeeks: number): number {
    if (totalWeeks <= 0) {
      return 1;
    }
    return Math.min(Math.max(week, 1), totalWeeks);
  }

  static mondayOfWeek(week: number, firstMonday: LocalDate): LocalDate {
    return firstMonday.plusWeeks(week - 1);
  }

  static datesOfWeek(week: number, firstMonday: LocalDate): LocalDate[] {
    const monday = Semester.mondayOfWeek(week, firstMonday);
    const out: LocalDate[] = [];
    for (let i = 0; i < 7; i++) {
      out.push(monday.plusDays(i));
    }
    return out;
  }

  /**
   * 把教务返回的周次串解析成集合。格式各家不同（"1-16" / "1,3,5" / "1-16单周"），必须容错。
   */
  static parseWeeks(raw: string, totalWeeks: number = 30): Set<number> {
    const out = new Set<number>();
    if (!raw || raw.trim().length === 0) {
      return out;
    }
    const text = raw
      .replace(/，/g, ',')
      .replace(/、/g, ',')
      .replace(/；/g, ',')
      .replace(/（/g, '(')
      .replace(/）/g, ')')
      .replace(/ /g, '');

    for (const token of text.split(',')) {
      if (token.length === 0) {
        continue;
      }
      const oddOnly = token.indexOf('单') >= 0;
      const evenOnly = token.indexOf('双') >= 0;
      let digits = '';
      for (const ch of token) {
        if ((ch >= '0' && ch <= '9') || ch === '-') {
          digits += ch;
        }
      }
      if (digits.length === 0) {
        continue;
      }

      const dash = digits.indexOf('-');
      let from: number;
      let to: number;
      if (dash >= 0) {
        from = Number(digits.substring(0, dash));
        to = Number(digits.substring(dash + 1));
      } else {
        from = Number(digits);
        to = from;
      }
      if (!Number.isInteger(from) || !Number.isInteger(to) || from <= 0 || to < from) {
        continue;
      }

      const upper = Math.min(to, totalWeeks);
      for (let w = from; w <= upper; w++) {
        if (oddOnly && w % 2 === 0) {
          continue;
        }
        if (evenOnly && w % 2 !== 0) {
          continue;
        }
        out.add(w);
      }
    }
    return out;
  }
}

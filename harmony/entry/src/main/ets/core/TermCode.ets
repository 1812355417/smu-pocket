import { LocalDate } from './LocalDate';

/**
 * 学期代码（xnxqdm）的换算与推算。
 * （与 Android 端 core/TermCode.kt 一一对应）
 *
 * 代码 = 学年起始年 + "0" + 学期序号：202601 → 2026-2027 学年第 1 学期。
 * 教务没有查询当前学期的接口，按日期推算（9 月开学），完全不依赖网络。
 */
export class TermCode {

  /** 兜底学期：所有手段都失败时用它 */
  static readonly FALLBACK: string = '202601';

  private static readonly SHORT: RegExp = /^\d{6}$/;
  private static readonly ACADEMIC_YEAR: RegExp = /(\d{4})\s*-\s*(\d{4})/;
  private static readonly HALF_CHINESE: RegExp = /第\s*([12])\s*学期/;
  private static readonly HALF_TRAILING: RegExp = /-\s*([12])\s*$/;

  /**
   * "2026-2027-1"、"2026-2027 学年 第 1 学期" → 短代码。认不出来返回 null。
   */
  static fromDisplayName(name: string | null | undefined): string | null {
    const text = (name ?? '').trim();
    if (text.length === 0) {
      return null;
    }
    const yearMatch = TermCode.ACADEMIC_YEAR.exec(text);
    if (!yearMatch) {
      return null;
    }
    const halfMatch = TermCode.HALF_CHINESE.exec(text) ?? TermCode.HALF_TRAILING.exec(text);
    if (!halfMatch) {
      return null;
    }
    return `${yearMatch[1]}0${halfMatch[1]}`;
  }

  /** "202601" → "2026-2027 学年 第 1 学期" */
  static displayName(code: string): string {
    const start = Number(code.substring(0, 4));
    const half = Number(code.substring(4));
    if (!Number.isInteger(start) || !Number.isInteger(half)) {
      return code;
    }
    return `${start}-${start + 1} 学年 第 ${half === 2 ? 2 : 1} 学期`;
  }

  /**
   * 按日期推算当前学期代码。学年 9 月开学：
   * 9~12 月 → 当年第 1 学期；1 月 → 上一年第 1 学期；2~8 月 → 上一年第 2 学期。
   */
  static fromDate(date: LocalDate = LocalDate.now()): string {
    const month = date.month;
    const startYear = month >= 9 ? date.year : date.year - 1;
    const half = month >= 2 && month <= 8 ? 2 : 1;
    return `${startYear}0${half}`;
  }

  static isValid(code: string | null | undefined): boolean {
    return code != null && TermCode.SHORT.test(code);
  }

  /**
   * 这个代码像不像「真的学期」：学期号只能是 1 或 2，且学年起始年离今天不超过 ±1 年。
   * （教务页面里混着 203402 这类非学期数字，防止再被当成学期。）
   */
  static isPlausible(code: string | null | undefined, today: LocalDate = LocalDate.now()): boolean {
    if (!TermCode.isValid(code)) {
      return false;
    }
    const startYear = Number(code!.substring(0, 4));
    const half = Number(code!.substring(4));
    if (!Number.isInteger(startYear) || (half !== 1 && half !== 2)) {
      return false;
    }
    return Math.abs(startYear - today.year) <= 1;
  }
}

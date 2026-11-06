/**
 * 极简本地日期类型：用「距 1970-01-01 的天数」表示，避开 JS Date 的时区陷阱。
 *
 * ArkTS 没有 java.time；课表的周次/日期换算全部是「天」级别的整数运算，
 * 用 epochDay 整数实现与 Android 端 `java.time.LocalDate` 完全一致的语义。
 */

const DAYS_0000_TO_1970: number = 719528; // 保留常量说明格里高利历换算基准

/** 格里高利历（公历）年月日 → epochDay。Howard Hinnant 的 days_from_civil */
function daysFromCivil(y: number, m: number, d: number): number {
  const yy = m <= 2 ? y - 1 : y;
  const era = Math.floor((yy >= 0 ? yy : yy - 399) / 400);
  const yoe = yy - era * 400;
  const mp = (m + 9) % 12;
  const doy = Math.floor((153 * mp + 2) / 5) + d - 1;
  const doe = yoe * 365 + Math.floor(yoe / 4) - Math.floor(yoe / 100) + doy;
  return era * 146097 + doe - 719468;
}

/** epochDay → [year, month, day]。civil_from_days */
function civilFromDays(z: number): number[] {
  const zz = z + 719468;
  const era = Math.floor((zz >= 0 ? zz : zz - 146096) / 146097);
  const doe = zz - era * 146097;
  const yoe = Math.floor((doe - Math.floor(doe / 1460) + Math.floor(doe / 36524) - Math.floor(doe / 146096)) / 365);
  const y = yoe + era * 400;
  const doy = doe - (365 * yoe + Math.floor(yoe / 4) - Math.floor(yoe / 100));
  const mp = Math.floor((5 * doy + 2) / 153);
  const d = doy - Math.floor((153 * mp + 2) / 5) + 1;
  const m = mp < 10 ? mp + 3 : mp - 9;
  return [m <= 2 ? y + 1 : y, m, d];
}

function pad2(n: number): string {
  return n < 10 ? `0${n}` : `${n}`;
}

export class LocalDate {
  /** 距 1970-01-01 的天数，可为负 */
  readonly epochDay: number;

  private constructor(epochDay: number) {
    this.epochDay = epochDay;
  }

  /** 今天（本地时区） */
  static now(): LocalDate {
    const d = new Date();
    return LocalDate.of(d.getFullYear(), d.getMonth() + 1, d.getDate());
  }

  static of(year: number, month: number, day: number): LocalDate {
    return new LocalDate(daysFromCivil(year, month, day));
  }

  static fromEpochDay(epochDay: number): LocalDate {
    return new LocalDate(epochDay);
  }

  /** 兼容 "2026-10-23" 和 "2026-10-23 00:00:00" 两种写法；解析失败返回 null */
  static parse(raw: string | null | undefined): LocalDate | null {
    const text = (raw ?? '').trim();
    if (text.length < 10) {
      return null;
    }
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(text);
    if (!m) {
      return null;
    }
    const y = Number(m[1]);
    const mo = Number(m[2]);
    const d = Number(m[3]);
    if (mo < 1 || mo > 12 || d < 1 || d > 31) {
      return null;
    }
    return LocalDate.of(y, mo, d);
  }

  get year(): number {
    return civilFromDays(this.epochDay)[0];
  }

  get month(): number {
    return civilFromDays(this.epochDay)[1];
  }

  get day(): number {
    return civilFromDays(this.epochDay)[2];
  }

  /** 1 = 周一 … 7 = 周日（1970-01-01 是周四） */
  get dayOfWeek(): number {
    const mod = ((this.epochDay % 7) + 7) % 7;
    return ((mod + 3) % 7) + 1;
  }

  plusDays(days: number): LocalDate {
    return new LocalDate(this.epochDay + days);
  }

  plusWeeks(weeks: number): LocalDate {
    return new LocalDate(this.epochDay + weeks * 7);
  }

  minusDays(days: number): LocalDate {
    return new LocalDate(this.epochDay - days);
  }

  isBefore(other: LocalDate): boolean {
    return this.epochDay < other.epochDay;
  }

  /** yyyy-MM-dd */
  toString(): string {
    const c = civilFromDays(this.epochDay);
    return `${c[0]}-${pad2(c[1])}-${pad2(c[2])}`;
  }

  /** M月D日 */
  monthDayLabel(): string {
    return `${this.month}月${this.day}日`;
  }
}

import { HttpException } from '../core/Http';

export class Page<T> {
  rows: T[];
  total: number;

  constructor(rows: T[], total: number) {
    this.rows = rows;
    this.total = total;
  }
}

/**
 * 分页收集（对应 Android 端 data/Pagination.kt）。
 *
 * 这类 *Datas 接口默认只给 20 行，不翻页会**静默少数据**。
 * 只接受完整结果：拒绝重复页、提前结束的空页、总数回退和超过安全上限的半份数据。
 */
export async function collectPages<T>(
  fetch: (page: number) => Promise<Page<T>>,
  maxPages: number = 20,
): Promise<T[]> {
  const collected: T[] = [];
  const seenPages = new Set<string>();
  for (let number = 1; number <= maxPages; number++) {
    const page = await fetch(number);
    if (page.total < collected.length + page.rows.length) {
      throw new HttpException(0, '教务分页总数不一致，请重试');
    }
    if (page.rows.length === 0) {
      if (collected.length < page.total) {
        throw new HttpException(0, '教务分页提前结束，请重试');
      }
      return collected;
    }
    const fingerprint = JSON.stringify(page.rows);
    if (seenPages.has(fingerprint)) {
      throw new HttpException(0, '教务返回重复分页，请重试');
    }
    seenPages.add(fingerprint);
    for (const row of page.rows) {
      collected.push(row);
    }
    if (collected.length >= page.total) {
      return collected;
    }
  }
  throw new HttpException(0, '教务分页超过安全上限，本次结果未保存');
}

import { TermCode } from '../core/TermCode';
import { TermStore } from '../core/TermStore';

export enum TermSource {
  CACHE,
  DATE,
  FALLBACK,
}

export class ResolvedTerm {
  code: string;
  source: TermSource;

  constructor(code: string, source: TermSource) {
    this.code = code;
    this.source = source;
  }
}

/**
 * 解析「当前学期代码」（对应 Android 端 data/TermResolver.kt）。
 *
 * 教务系统拿不到「当前学期」：所有候选接口全部 404，成绩页 HTML 的学期下拉
 * 是全量列表且没有 selected 标记。所以用日期推算（9 月开学，边界固定），
 * 推算结果与教务一致，完全不依赖网络、不会因页面改版失效。
 */
export class TermResolver {
  private readonly store: TermStore;

  constructor(store: TermStore) {
    this.store = store;
  }

  resolve(): ResolvedTerm {
    const cached = this.store.load();
    if (cached !== null) {
      return new ResolvedTerm(cached, TermSource.CACHE);
    }

    const byDate = TermCode.fromDate();
    if (TermCode.isPlausible(byDate)) {
      this.store.save(byDate, 'date');
      return new ResolvedTerm(byDate, TermSource.DATE);
    }

    return new ResolvedTerm(TermCode.FALLBACK, TermSource.FALLBACK);
  }

  /** 不联网的「当前学期」，探针用 */
  peek(): string {
    return this.store.load() ?? TermCode.fromDate();
  }
}

import { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import { TermCode } from './TermCode';

const PREF: string = 'smu_term';
const KEY_CODE: string = 'term_code_v2';
const KEY_SOURCE: string = 'term_source';
const KEY_SAVED_AT: string = 'term_saved_at';

/**
 * 当前学期代码的本地存储（对应 Android 端 core/TermStore.kt）。
 *
 * 单独一个 preferences 文件，不跟 cookie 混在一起：cookie 是账号凭证，
 * 学期代码不是，两者的生命周期不同。
 */
export class TermStore {
  private readonly prefs: preferences.Preferences;

  constructor(context: common.Context) {
    this.prefs = preferences.getPreferencesSync(context, { name: PREF });
  }

  /** 只认「像学期」的代码，坏值当作没有（否则一次抓错会永久污染缓存） */
  load(): string | null {
    const value = this.prefs.getSync(KEY_CODE, '') as string;
    return TermCode.isPlausible(value) ? value : null;
  }

  save(code: string, source: string): void {
    this.prefs.putSync(KEY_CODE, code);
    this.prefs.putSync(KEY_SOURCE, source);
    this.prefs.putSync(KEY_SAVED_AT, Date.now());
    this.prefs.flush();
  }

  /** 最近一次学期代码是从哪来的：web / cache / date / fallback */
  source(): string {
    return this.prefs.getSync(KEY_SOURCE, '') as string;
  }

  clear(): void {
    this.prefs.deleteSync(KEY_CODE);
    this.prefs.deleteSync(KEY_SOURCE);
    this.prefs.deleteSync(KEY_SAVED_AT);
    this.prefs.flush();
  }
}

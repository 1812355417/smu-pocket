import { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import { formBindingData, formProvider } from '@kit.FormKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { Course, TimetableConfig } from './Models';
import { DEFAULT_THEME, KEY_THEME_PRIMARY } from './ThemeStore';
import {
  WidgetSnapshotStore,
  WidgetSnapshotData,
  WidgetFormData,
  buildSnapshot,
  emptyFormData,
  summarizeForNow,
} from './WidgetSnapshot';

const TAG: string = 'SMUPocket';
const DOMAIN: number = 0x0a05;

const PREF_NAME: string = 'widget_forms';
const PREF_KEY_FORMS: string = 'forms';

/** 已添加卡片的 formId → 尺寸（"2*2"/"2*4"）。FormAbility 写，App 主进程读 */
export function readFormMap(context: common.Context): Map<string, string> {
  const out = new Map<string, string>();
  try {
    const prefs = preferences.getPreferencesSync(context, { name: PREF_NAME });
    const raw = prefs.getSync(PREF_KEY_FORMS, '[]') as string;
    const entries = JSON.parse(raw) as Array<[string, string]>;
    for (const pair of entries) {
      if (pair.length === 2) {
        out.set(pair[0], pair[1]);
      }
    }
  } catch (e) {
    // 登记表损坏就当没有卡片，下次添加卡片时重建
  }
  return out;
}

export function writeFormMap(context: common.Context, forms: Map<string, string>): void {
  try {
    const prefs = preferences.getPreferencesSync(context, { name: PREF_NAME });
    prefs.putSync(PREF_KEY_FORMS, JSON.stringify(Array.from(forms.entries())));
    prefs.flush();
  } catch (e) {
    hilog.warn(DOMAIN, TAG, `卡片登记表写入失败: ${JSON.stringify(e)}`);
  }
}

function currentThemePrimary(): string {
  return AppStorage.get<string>(KEY_THEME_PRIMARY) ?? DEFAULT_THEME.primary;
}

/**
 * App 主进程 → 卡片的桥：维护快照、把「今日摘要」推送到所有已添加的卡片。
 * 卡片进程不读这里（它走 WidgetSnapshotStore + onUpdateForm 自算），
 * 只共享 readFormMap/writeFormMap 两个登记表函数。
 */
export class WidgetBridge {
  private readonly context: common.Context;
  private readonly store: WidgetSnapshotStore;

  constructor(context: common.Context) {
    this.context = context;
    this.store = new WidgetSnapshotStore(context);
  }

  /**
   * 课表同步成功后调用：重写快照并推送。
   * courses 优先整学期课表（跨周自算全靠它），拿不到时用当前周兜底。
   */
  publish(config: TimetableConfig, termCourses: Course[], fallbackCourses: Course[]): void {
    const courses = termCourses.length > 0 ? termCourses : fallbackCourses;
    if (courses.length === 0) {
      return;
    }
    this.store.write(buildSnapshot(config, courses, currentThemePrimary()));
    this.pushNow();
  }

  /** 主题切换后调用：快照里换主题色并重推（课程数据不动） */
  refreshTheme(): void {
    const snapshot = this.store.read();
    if (snapshot !== null) {
      snapshot.themePrimary = currentThemePrimary();
      snapshot.savedAt = Date.now();
      this.store.write(snapshot);
    }
    this.pushNow();
  }

  /** 退出登录：清快照，卡片回到引导占位 */
  clear(): void {
    this.store.clear();
    this.pushNow();
  }

  /** 读快照算今日摘要，逐卡片推送（尺寸按登记表逐卡片并入） */
  pushNow(): void {
    const forms = readFormMap(this.context);
    if (forms.size === 0) {
      return;
    }
    const snapshot: WidgetSnapshotData | null = this.store.read();
    forms.forEach((dim, formId) => {
      const data: WidgetFormData = snapshot === null
        ? emptyFormData('打开 App 同步课表', currentThemePrimary())
        : summarizeForNow(snapshot, new Date());
      data.dim = dim;
      try {
        formProvider.updateForm(formId, formBindingData.createFormBindingData(data));
      } catch (e) {
        hilog.warn(DOMAIN, TAG, `卡片推送失败 formId=${formId}: ${JSON.stringify(e)}`);
      }
    });
  }
}

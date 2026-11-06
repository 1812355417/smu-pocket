import { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import { ConfigurationConstant } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';

const TAG: string = 'SMUPocket';
const DOMAIN: number = 0x0a07;

const PREF_NAME: string = 'settings';
const PREF_KEY_THEME: string = 'theme_preset_id';

/** AppStorage 键：各页面用 @StorageProp/@StorageLink 订阅 */
export const KEY_THEME_PRIMARY: string = 'themePrimary';
export const KEY_THEME_SOFT: string = 'themeSoft';

/** EntryAbility.onCreate 里 Environment.envProp 注入的系统深浅色键 */
export const KEY_COLOR_MODE: string = 'colorMode';

export interface ThemePreset {
  id: string;
  name: string;
  /** 浅色模式强调色 */
  primary: string;
  /** 浅色模式弱强调（选中底色等） */
  soft: string;
  /** 深色模式强调色（提亮保证对比度） */
  primaryDark: string;
  softDark: string;
}

/** 第一套必须与资源文件 app.color.app_primary（#0A59F7）一致，作为回退默认 */
export const THEME_PRESETS: ThemePreset[] = [
  { id: 'blue',   name: '蓝', primary: '#0A59F7', soft: '#400A59F7', primaryDark: '#4C82F7', softDark: '#334C82F7' },
  { id: 'indigo', name: '靛', primary: '#4F5BD5', soft: '#404F5BD5', primaryDark: '#7C88E5', softDark: '#337C88E5' },
  { id: 'purple', name: '紫', primary: '#7C3AED', soft: '#407C3AED', primaryDark: '#9D6BF5', softDark: '#339D6BF5' },
  { id: 'pink',   name: '粉', primary: '#DB2777', soft: '#40DB2777', primaryDark: '#F06BA8', softDark: '#33F06BA8' },
  { id: 'red',    name: '红', primary: '#DC2626', soft: '#40DC2626', primaryDark: '#F06464', softDark: '#33F06464' },
  { id: 'orange', name: '橙', primary: '#D96C0B', soft: '#40D96C0B', primaryDark: '#F59A4E', softDark: '#33F59A4E' },
  { id: 'green',  name: '绿', primary: '#16803C', soft: '#4016803C', primaryDark: '#4CAF6E', softDark: '#334CAF6E' },
  { id: 'teal',   name: '青', primary: '#0E7C86', soft: '#400E7C86', primaryDark: '#3FB0BC', softDark: '#333FB0BC' },
];

export const DEFAULT_THEME: ThemePreset = THEME_PRESETS[0];

export function presetById(id: string | null): ThemePreset {
  if (id !== null) {
    for (const p of THEME_PRESETS) {
      if (p.id === id) {
        return p;
      }
    }
  }
  return DEFAULT_THEME;
}

/**
 * 主题强调色：8 套预设，preferences 持久化，AppStorage 广播给所有页面。
 * 只替换 app_primary / app_primary_soft 两个角色；背景、文字、错误红、
 * 自主学习绿等语义色仍走资源文件，教务下发的课程 colorHex 不受影响。
 */
export class ThemeStore {
  /** 启动时调用：读持久化选择并写入 AppStorage。失败一律回退默认蓝 */
  static async load(context: common.Context): Promise<ThemePreset> {
    let id: string | null = null;
    try {
      const prefs = preferences.getPreferencesSync(context, { name: PREF_NAME });
      const raw = prefs.getSync(PREF_KEY_THEME, '') as string;
      id = raw.length > 0 ? raw : null;
    } catch (e) {
      hilog.warn(DOMAIN, TAG, `主题读取失败，用默认蓝: ${JSON.stringify(e)}`);
    }
    const preset = presetById(id);
    ThemeStore.apply(preset, ThemeStore.isDark());
    return preset;
  }

  /** 设置页选择主题：持久化 + 立即生效 */
  static async set(context: common.Context, preset: ThemePreset): Promise<void> {
    try {
      const prefs = preferences.getPreferencesSync(context, { name: PREF_NAME });
      prefs.putSync(PREF_KEY_THEME, preset.id);
      prefs.flush();
    } catch (e) {
      hilog.warn(DOMAIN, TAG, `主题保存失败: ${JSON.stringify(e)}`);
    }
    ThemeStore.apply(preset, ThemeStore.isDark());
  }

  /** 系统深/浅色切换时重算（preset 不变，换 dark 变体值） */
  static apply(preset: ThemePreset, isDark: boolean): void {
    AppStorage.setOrCreate(KEY_THEME_PRIMARY, isDark ? preset.primaryDark : preset.primary);
    AppStorage.setOrCreate(KEY_THEME_SOFT, isDark ? preset.softDark : preset.soft);
  }

  /**
   * 依赖 EntryAbility.onCreate 里的 Environment.envProp('colorMode', …) 注入；
   * 注入失败/未注入时按浅色处理
   */
  static isDark(): boolean {
    const mode = AppStorage.get<number>(KEY_COLOR_MODE);
    return mode === ConfigurationConstant.ColorMode.COLOR_MODE_DARK;
  }
}

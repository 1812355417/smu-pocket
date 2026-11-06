import { AbilityConstant, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { webview } from '@kit.ArkWeb';

const TAG: string = 'SMUPocket';
const DOMAIN: number = 0x0a02;

/** 课表卡片点击拉起 App 时，want.parameters 里带的目标 Tab（0 = 今日） */
export const KEY_TARGET_TAB: string = 'targetTab';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(DOMAIN, TAG, 'EntryAbility onCreate');
    // 系统深/浅色注入 AppStorage（ThemeStore.isDark 依赖它）
    Environment.envProp('colorMode', ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT);
    this.forwardTargetTab(want);
  }

  onNewWant(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    // App 已在后台时点击卡片走这里
    this.forwardTargetTab(want);
  }

  private forwardTargetTab(want: Want): void {
    const tab = want?.parameters?.[KEY_TARGET_TAB];
    if (typeof tab === 'number') {
      AppStorage.setOrCreate(KEY_TARGET_TAB, tab);
    }
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    // ArkWeb 内核初始化（登录页 Web 组件依赖它）
    try {
      webview.WebviewController.initializeWebEngine();
    } catch (e) {
      hilog.warn(DOMAIN, TAG, `initializeWebEngine 失败: ${JSON.stringify(e)}`);
    }

    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        hilog.error(DOMAIN, TAG, `loadContent 失败: ${JSON.stringify(err)}`);
        return;
      }
      hilog.info(DOMAIN, TAG, 'loadContent 成功');
    });
  }
}

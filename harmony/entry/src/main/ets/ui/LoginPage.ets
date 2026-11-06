import { webview } from '@kit.ArkWeb';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { AppViewModel } from '../viewmodel/AppViewModel';
import { errorText } from '../core/EncryptedStore';
import { hostOf } from '../core/Session';
import { LOGIN_URL } from '../data/JwService';

const TAG: string = 'SMUPocket';
const DOMAIN: number = 0x0a03;
const POLL_INTERVAL_MS: number = 2000;
const MAX_POLLS: number = 45; // 45 × 2s = 90 秒
const HINT_AFTER_POLLS: number = 15; // 30 秒后提示一下

/**
 * 登录页（对应 Android 端 ui/login/LoginScreen.kt）。
 *
 * 核心决定：**不在 App 里 POST 账号密码**，而是开学校自己的登录页让用户扫码。
 * 收益：极验滑块、密码前端加密、CAS 多跳全部免适配，我们只要结果（cookie）。
 *
 * 流程：
 * 1. ArkWeb 打开教务首页（带扫码登录）
 * 2. 每 2 秒从 WebCookieManager 捞一次 cookie 存进 SessionStore
 * 3. 顺带发一个**轻量**请求（zc=1）探一次会话是否有效
 * 4. 探通了 → ViewModel 把 loggedIn 置 true → RootView 自动换到主页
 */
@Component
export struct LoginPage {
  @ObjectLink vm: AppViewModel;
  @StorageProp('themePrimary') readonly themePrimary: string = '#0A59F7';
  @State showWeb: boolean = false;

  build() {
    if (this.showWeb) {
      LoginWeb({
        vm: this.vm,
        onClose: () => {
          this.vm.onLoginCancelled();
          this.showWeb = false;
        },
      })
    } else {
      Column() {
        Text('南医大口袋')
          .fontSize(28)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.app_text_primary'))
        Text('非官方客户端 · 课表 / 成绩')
          .fontSize(13)
          .fontColor($r('app.color.app_text_secondary'))
          .margin({ top: 4 })

        Text('登录在学校自己的页面里完成，App 不接触密码、不保存密码。')
          .fontSize(15)
          .fontColor($r('app.color.app_text_primary'))
          .margin({ top: 24 })
        Text('点下面的按钮打开教务首页，用手机扫码登录；登录成功后会自动跳回来，不需要手动关闭。')
          .fontSize(13)
          .fontColor($r('app.color.app_text_secondary'))
          .margin({ top: 6 })

        Button('打开教务系统登录（扫码）')
          .width('100%')
          .margin({ top: 24 })
          .onClick(() => {
            this.showWeb = true;
          })
        Button('先用演示数据看看')
          .width('100%')
          .margin({ top: 8 })
          .backgroundColor(Color.Transparent)
          .fontColor(this.themePrimary)
          .onClick(() => {
            this.vm.enterDemo();
          })

        if (this.vm.loading) {
          Text('正在加载…')
            .fontSize(13)
            .fontColor($r('app.color.app_text_secondary'))
            .margin({ top: 16 })
        }

        if (this.vm.error !== null) {
          Text(this.vm.error ?? '')
            .fontSize(13)
            .fontColor($r('app.color.app_error'))
            .margin({ top: 16 })
        }
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Start)
      .padding(24)
      .backgroundColor($r('app.color.app_page_bg'))
    }
  }
}

@Component
struct LoginWeb {
  @ObjectLink vm: AppViewModel;
  @StorageProp('themePrimary') readonly themePrimary: string = '#0A59F7';
  onClose: () => void = () => {
  };

  private controller: webview.WebviewController = new webview.WebviewController();
  @State currentUrl: string = LOGIN_URL;
  @State hint: string = '扫码完成后会自动进入，无需手动操作';
  @State done: boolean = false;
  private polling: boolean = false;
  private closed: boolean = false;

  aboutToAppear(): void {
    // CAS / 统一身份认证跨域回调时，第三方 cookie 必须开
    try {
      webview.WebCookieManager.putAcceptCookieEnabled(true);
      webview.WebCookieManager.putAcceptThirdPartyCookieEnabled(true);
    } catch (e) {
      hilog.warn(DOMAIN, TAG, `CookieManager 配置失败: ${JSON.stringify(e)}`);
    }
    this.startPolling();
  }

  aboutToDisappear(): void {
    this.closed = true;
    this.polling = false;
    this.clearWebLoginData();
  }

  private clearWebLoginData(): void {
    try {
      webview.WebCookieManager.clearAllCookiesSync();
      webview.WebCookieManager.clearSessionCookieSync();
    } catch (e) {
      hilog.warn(DOMAIN, TAG, `清理 Web 登录数据失败: ${JSON.stringify(e)}`);
    }
  }

  private async startPolling(): Promise<void> {
    if (this.polling) {
      return;
    }
    this.polling = true;

    for (let polls = 1; polls <= MAX_POLLS; polls++) {
      await this.sleep(POLL_INTERVAL_MS);
      if (this.closed || !this.polling) {
        return;
      }

      // 同时捞当前页和登录页：CAS 场景下凭证可能落在另一个 host 上
      const harvested = await this.harvest([this.currentUrl, LOGIN_URL]);
      if (harvested.size === 0) {
        if (polls === HINT_AFTER_POLLS) {
          this.hint = '还没拿到 cookie，确认页面是否加载完了';
        }
        continue;
      }

      try {
        const tasks: Promise<void>[] = [];
        harvested.forEach((header, host) => {
          tasks.push(this.vm.onLoginCookies(host, header));
        });
        for (const task of tasks) {
          await task;
        }
      } catch (e) {
        hilog.error(DOMAIN, TAG, `保存登录凭据失败: ${errorText(e)}`);
        this.clearWebLoginData();
        this.hint = '无法安全保存登录凭据，请关闭后重试';
        return;
      }

      let ok = false;
      try {
        ok = await this.vm.verifySession();
      } catch (e) {
        ok = false;
      }
      hilog.info(DOMAIN, TAG, `第 ${polls} 次探测：${ok ? '已登录' : '未登录'}`);

      if (ok) {
        this.hint = '登录成功，正在加载课表…';
        this.done = true;
        return;
      }

      if (polls === HINT_AFTER_POLLS) {
        this.hint = '还没检测到登录成功，请确认已经完成扫码';
      }
    }

    if (!this.closed) {
      this.hint = '等了 90 秒仍未登录成功，可以点「关闭」重试';
    }
  }

  /** 按 URL 逐个捞 cookie，返回 host → "a=1; b=2"；同一 host 取更完整的一份 */
  private async harvest(urls: string[]): Promise<Map<string, string>> {
    // 先把内存里的 cookie 刷到磁盘再读（对应 Android 端 CookieManager.flush()）
    try {
      await webview.WebCookieManager.saveCookieAsync();
    } catch (e) {
      // 刷盘失败不阻塞读取
    }

    const byHost = new Map<string, string>();
    for (const url of urls) {
      const host = hostOf(url);
      if (host === null) {
        continue;
      }
      let header = '';
      try {
        // 不能靠 JS 读 cookie：教务的会话 cookie 是 HttpOnly 的，只能走 CookieManager
        header = await webview.WebCookieManager.fetchCookie(url);
      } catch (e) {
        header = '';
      }
      if (header.trim().length === 0) {
        continue;
      }
      const old = byHost.get(host);
      if (old === undefined || header.length > old.length) {
        byHost.set(host, header);
      }
    }
    return byHost;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve: () => void) => {
      setTimeout(resolve, ms);
    });
  }

  build() {
    Column() {
      Row() {
        Column() {
          Text('教务系统登录')
            .fontSize(17)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.app_text_primary'))
          Text(this.hint)
            .fontSize(11)
            .fontColor(this.done
              ? this.themePrimary
              : $r('app.color.app_text_secondary'))
            .margin({ top: 2 })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)

        Button('关闭')
          .backgroundColor(Color.Transparent)
          .fontColor(this.themePrimary)
          .onClick(() => {
            this.onClose();
          })
      }
      .width('100%')
      .padding({ left: 12, right: 12, top: 6, bottom: 6 })

      Web({ src: LOGIN_URL, controller: this.controller })
        .javaScriptAccess(true)
        .domStorageAccess(true)
        .mixedMode(MixedMode.None)
        .cacheMode(CacheMode.None)
        .onPageBegin((event) => {
          if (event) {
            this.currentUrl = event.url;
          }
        })
        .onPageEnd((event) => {
          if (event) {
            this.currentUrl = event.url;
          }
        })
        .width('100%')
        .layoutWeight(1)
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.app_page_bg'))
  }
}

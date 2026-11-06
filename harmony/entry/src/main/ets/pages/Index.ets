import { common } from '@kit.AbilityKit';
import { CacheStore } from '../core/CacheStore';
import { JwHttp } from '../core/Http';
import { SessionStore } from '../core/Session';
import { HttpTransport } from '../core/Transport';
import { TermStore } from '../core/TermStore';
import { BASE_URL, FakeJw, JwService } from '../data/JwService';
import { RealJw } from '../data/RealJw';
import { TermResolver } from '../data/TermResolver';
import { AppViewModel } from '../viewmodel/AppViewModel';
import { RootView } from '../ui/RootView';

/**
 * 应用入口：手写依赖容器（对应 Android 端 App.kt / MainActivity.kt）。
 *
 * 刻意的取舍和 Android 端一致：不引框架，整个 App 就两三个对象，
 * 手写容器比引框架便宜；接口改版时只改 data/Mappers.ets 一个文件。
 */
@Entry
@Component
struct Index {
  @State vm: AppViewModel | null = null;

  aboutToAppear(): void {
    this.setup();
  }

  private async setup(): Promise<void> {
    const context = getContext(this) as common.Context;
    const session = await SessionStore.create(context);
    const cache = new CacheStore(context);
    const terms = new TermResolver(new TermStore(context));

    // 没有会话时给 FakeJw（登录页演示模式用）；
    // 调用方（ViewModel）保证不会在真实模式下用 FakeJw 的结果冒充真数据
    const jwProvider = (): JwService => {
      if (!session.isLoggedIn()) {
        return new FakeJw();
      }
      return new RealJw(new JwHttp(new HttpTransport(session), BASE_URL), terms);
    };

    const vm = new AppViewModel(jwProvider, session, cache);
    this.vm = vm;
    await vm.restore();
  }

  build() {
    Stack() {
      if (this.vm !== null) {
        RootView({ vm: this.vm })
      } else {
        Column() {
          LoadingProgress()
            .width(48)
            .height(48)
          Text('正在启动…')
            .fontSize(13)
            .fontColor($r('app.color.app_text_secondary'))
            .margin({ top: 12 })
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.app_page_bg'))
  }
}

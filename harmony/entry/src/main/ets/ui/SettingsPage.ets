import { pasteboard } from '@kit.BasicServicesKit';
import { promptAction } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { humanizeAgo } from '../core/TimeUtils';
import { DEFAULT_THEME, ThemePreset, THEME_PRESETS, ThemeStore } from '../core/ThemeStore';
import { AppViewModel } from '../viewmodel/AppViewModel';
import { CardBox, LabelValue, ScreenHeader } from './Common';

/** 更新通知 / 问题反馈 QQ 群。点一下复制到剪贴板 */
const QQ_GROUP: string = '1128162850';

/** App 版本号。改版本时和 AppScope/app.json5 的 versionName 一起改 */
const APP_VERSION: string = 'v1.0.0';

/**
 * 设置页（对应 Android 端 ui/settings/SettingsScreen.kt）：
 * 手动同步 · 接入状态 · 开发者与反馈（QQ 群一键复制）· 赞赏与支持 · 版本与开源信息。
 */
@Component
export struct SettingsPage {
  @ObjectLink vm: AppViewModel;
  @StorageProp('themePrimary') readonly themePrimary: string = '#0A59F7';
  @State currentThemeId: string = DEFAULT_THEME.id;
  private supportDialog: CustomDialogController = new CustomDialogController({
    builder: SupportDialog({}),
    autoCancel: true,
    customStyle: false,
  });

  aboutToAppear(): void {
    ThemeStore.load(getContext(this) as common.Context).then((preset) => {
      this.currentThemeId = preset.id;
    });
  }

  build() {
    Scroll() {
      Column() {
        ScreenHeader({
          title: '设置',
          subtitle: '非官方客户端 · 数据直连学校系统 · 不经过第三方服务器',
        })

        // ---- 同步入口 ----
        CardBox() {
          Button(this.vm.loading ? '同步中…' : '同步最新课表与成绩')
            .width('100%')
            .enabled(!this.vm.loading)
            .onClick(() => {
              this.vm.syncNow();
            })

          Text(this.syncHint())
            .fontSize(11)
            .fontColor($r('app.color.app_text_secondary'))
            .margin({ top: 8 })
        }

        // ---- 主题色 ----
        CardBox() {
          Text('主题色')
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.app_text_primary'))
            .margin({ bottom: 10 })

          Flex({ wrap: FlexWrap.Wrap }) {
            ForEach(THEME_PRESETS, (preset: ThemePreset) => {
              Column() {
                Column()
                  .width(28)
                  .height(28)
                  .borderRadius(14)
                  .backgroundColor(preset.primary)
              }
              .padding(3)
              .borderRadius(20)
              .border({
                width: 2,
                color: this.currentThemeId === preset.id ? this.themePrimary : Color.Transparent,
              })
              .margin({ right: 10, top: 4, bottom: 4 })
              .onClick(() => {
                this.pickTheme(preset);
              })
            }, (preset: ThemePreset) => preset.id)
          }
          .width('100%')

          Text('按钮、选中态等强调色会跟随切换，课程配色不变')
            .fontSize(11)
            .fontColor($r('app.color.app_text_secondary'))
            .margin({ top: 6 })
        }

        // ---- 接入状态 ----
        CardBox() {
          Text('接入状态')
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.app_text_primary'))
            .margin({ bottom: 8 })

          LabelValue({ label: '教务系统', value: this.jwStatus() })
          LabelValue({ label: '当前学期', value: this.vm.config?.termName ?? '—' })
          // 只有真的在演示模式才敢标「演示数据」：缓存恢复时 semesterCode 是空的
          LabelValue({
            label: '学期代码',
            value: this.vm.semesterCode ?? (this.vm.demoMode ? '—（演示数据）' : '—'),
          })
          LabelValue({ label: '第一周周一', value: this.vm.config?.firstMonday.toString() ?? '—' })
          LabelValue({ label: '总周数', value: this.vm.config?.totalWeeks.toString() ?? '—' })
          LabelValue({ label: '课表数据', value: `${this.vm.scheduleCourses.length} 条` })
          LabelValue({ label: '成绩数据', value: `${this.vm.grades.length} 条` })
        }

        // ---- 退出登录 ----
        CardBox() {
          Button('退出登录（清空登录态）')
            .width('100%')
            .backgroundColor(Color.Transparent)
            .fontColor($r('app.color.app_error'))
            .onClick(() => {
              this.vm.logout();
            })
          Text('退出后会同时清空本地缓存，下次需要重新扫码。')
            .fontSize(11)
            .fontColor($r('app.color.app_text_secondary'))
            .margin({ top: 4 })
        }

        // ---- 关于 ----
        CardBox() {
          Text('关于')
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.app_text_primary'))
            .margin({ bottom: 6 })
          Text(`南医大口袋 ${APP_VERSION}`)
            .fontSize(13)
            .fontColor($r('app.color.app_text_primary'))
          Text('账号密码只在学校的登录页输入，App 不保存密码、不上传任何数据。')
            .fontSize(13)
            .fontColor($r('app.color.app_text_secondary'))
            .margin({ top: 4 })
          Text('本项目仅供学习交流与个人使用，请遵守学校信息系统使用规定，不要高频请求。')
            .fontSize(11)
            .fontColor($r('app.color.app_text_secondary'))
            .margin({ top: 6 })
        }

        // ---- 开发者与反馈 ----
        CardBox() {
          Text('开发者：QIU 鸿蒙迁移适配者：sleepinlava')
            .fontSize(11)
            .fontColor($r('app.color.app_text_secondary'))

          Row() {
            Text('更新通知 / 问题反馈 QQ 群：')
              .fontSize(11)
              .fontColor($r('app.color.app_text_secondary'))
            Text(QQ_GROUP)
              .fontSize(11)
              .fontColor($r('app.color.app_text_secondary'))
              .decoration({ type: TextDecorationType.Underline })
          }
          .margin({ top: 4 })
          .onClick(() => {
            this.copyQqGroup();
          })

          Button('☕ 赞赏与支持')
            .fontSize(12)
            .fontColor($r('app.color.app_text_secondary'))
            .backgroundColor(Color.Transparent)
            .margin({ top: 4 })
            .onClick(() => {
              this.supportDialog.open();
            })
        }

        // ---- 版本 / 开源 ----
        Text(`南医大口袋 ${APP_VERSION} · 遵循 GPL-3.0 开源协议`)
          .fontSize(10)
          .fontColor($r('app.color.app_text_secondary'))
          .opacity(0.7)
          .textAlign(TextAlign.Center)
          .width('100%')
          .margin({ top: 8, bottom: 24 })
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.app_page_bg'))
  }

  private async pickTheme(preset: ThemePreset): Promise<void> {
    this.currentThemeId = preset.id;
    await ThemeStore.set(getContext(this) as common.Context, preset);
    // 卡片标题色也跟随主题
    this.vm.refreshWidgetTheme();
  }

  private syncHint(): string {
    const tail = '如发现课程有变动或无法加载，请重新扫码登录。';
    if (this.vm.cachedAt !== null) {
      return `当前数据更新于 ${humanizeAgo(this.vm.cachedAt)}，${tail}`;
    }
    return tail;
  }

  private jwStatus(): string {
    if (this.vm.demoMode) {
      return '演示数据（未接入）';
    }
    if (this.vm.stale) {
      return '已接入 · 当前显示缓存';
    }
    return '已接入';
  }

  private async copyQqGroup(): Promise<void> {
    try {
      const board = pasteboard.getSystemPasteboard();
      const data = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, QQ_GROUP);
      await board.setData(data);
      promptAction.showToast({ message: 'QQ群号已复制' });
    } catch (e) {
      promptAction.showToast({ message: `复制失败：${QQ_GROUP}` });
    }
  }
}

/** 赞赏弹窗：文案 + 微信收款码 + 关闭按钮（纯自愿，和学校无关） */
@CustomDialog
struct SupportDialog {
  @StorageProp('themePrimary') readonly themePrimary: string = '#0A59F7';
  controller: CustomDialogController = new CustomDialogController({ builder: SupportDialog({}) });

  build() {
    Column() {
      Text('支持开发者')
        .fontSize(17)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.app_text_primary'))
        .margin({ bottom: 12 })

      Text('感谢使用南医大口袋！如果觉得这个 App 帮到了你，可以支持一下。纯属自愿，与学校无关。')
        .fontSize(14)
        .fontColor($r('app.color.app_text_primary'))

      Image($r('app.media.wechat_pay'))
        .width(200)
        .objectFit(ImageFit.Contain)
        .margin({ top: 16 })

      Button('关闭')
        .backgroundColor(Color.Transparent)
        .fontColor(this.themePrimary)
        .margin({ top: 12 })
        .onClick(() => {
          this.controller.close();
        })
    }
    .padding(20)
    .backgroundColor($r('app.color.app_card_bg'))
    .borderRadius(20)
  }
}

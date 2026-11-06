import { AppViewModel } from '../viewmodel/AppViewModel';
import { LoginPage } from './LoginPage';
import { TodayPage } from './TodayPage';
import { SchedulePage } from './SchedulePage';
import { GradesPage } from './GradesPage';
import { SettingsPage } from './SettingsPage';

enum TabIndex {
  TODAY = 0,
  SCHEDULE = 1,
  GRADES = 2,
  SETTINGS = 3,
}

/**
 * 根页面：未登录显示登录页，已登录显示底部四 Tab。
 * （对应 Android 端 ui/RootScreen.kt）
 *
 * 主界面保持绝对干净：没有任何提示条。
 * 缓存/失效状态只在「设置」里体现，并提供「同步最新课表与成绩」入口。
 */
@Component
export struct RootView {
  @ObjectLink vm: AppViewModel;
  @StorageProp('themePrimary') readonly themePrimary: string = '#0A59F7';
  /** 卡片点击拉起时 EntryAbility 写入的目标 Tab */
  @StorageProp('targetTab') @Watch('onTargetTab') targetTab: number = 0;
  @State currentTab: number = TabIndex.TODAY;

  private onTargetTab(): void {
    this.currentTab = this.targetTab;
  }

  @Builder
  tabLabel(label: string, index: number) {
    Text(label)
      .fontSize(16)
      .fontWeight(this.currentTab === index ? FontWeight.Medium : FontWeight.Normal)
      .fontColor(this.currentTab === index
        ? this.themePrimary
        : $r('app.color.app_text_secondary'))
      .padding({ top: 10, bottom: 10 })
  }

  build() {
    if (!this.vm.loggedIn) {
      LoginPage({ vm: this.vm })
    } else {
      Tabs({ barPosition: BarPosition.End, index: this.currentTab }) {
        TabContent() {
          TodayPage({ vm: this.vm })
        }
        .tabBar(this.tabLabel('今日', TabIndex.TODAY))

        TabContent() {
          SchedulePage({ vm: this.vm, selectedWeek: this.vm.selectedWeek })
        }
        .tabBar(this.tabLabel('课表', TabIndex.SCHEDULE))

        TabContent() {
          GradesPage({ vm: this.vm })
        }
        .tabBar(this.tabLabel('成绩', TabIndex.GRADES))

        TabContent() {
          SettingsPage({ vm: this.vm })
        }
        .tabBar(this.tabLabel('设置', TabIndex.SETTINGS))
      }
      .barWidth('100%')
      .barHeight(56)
      .scrollable(false)
      .animationDuration(0)
      .onChange((index: number) => {
        this.currentTab = index;
      })
      .backgroundColor($r('app.color.app_page_bg'))
    }
  }
}

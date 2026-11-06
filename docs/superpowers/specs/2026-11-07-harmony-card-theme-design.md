# 鸿蒙端课表卡片 + 主题强调色 设计文档

日期：2026-11-07
范围：`harmony/` 鸿蒙端（ArkTS，单 entry 模块）
已明确排除：实况窗（Live View）功能——需华为白名单审核，本期不做。

## 背景

`harmony/` 是安卓原生「南医大口袋」的鸿蒙迁移版。当前状态：

- 页面：今日 / 课表 / 成绩 / 设置（`ets/ui/*.ets`），数据中枢为单一 `@Observed` 的 `AppViewModel`
- 缓存：`CacheStore` → `EncryptedStore`（加密），存课表、成绩、考试快照
- 颜色：全部走资源文件 `$r('app.color.app_*')`（base + dark 两套），无主题切换机制
- 无 FormExtensionAbility（卡片）、无实况窗代码

## 功能一：负一屏课表卡片

### 目标

用户在负一屏/桌面添加卡片后，不用打开 App 即可看到今日课程；App 每次同步或读缓存后卡片自动更新；跨午夜后卡片自行切换到第二天。

### 组件

| 文件 | 职责 |
|---|---|
| `ets/formability/ScheduleFormAbility.ets`（新增） | 卡片生命周期：`onAddForm` 记录 formId 并立即渲染最新快照；`onRemoveForm` 清理 formId；`onUpdateForm`（系统定时触发）从快照重渲染 |
| `ets/schedulecard/pages/ScheduleCard.ets`（新增） | 卡片 UI，按卡片尺寸切换 2x2 / 2x4 布局 |
| `ets/core/WidgetSnapshot.ets`（新增） | 卡片专用快照读写：纯 JSON 文件存应用沙箱，**不加密**（课程名非敏感信息；卡片进程访问 HUKS 密钥有风险，刻意避开 EncryptedStore） |
| `ets/viewmodel/AppViewModel.ets`（改） | `persistCache()` 与 `restoreFromCache()` 成功后调 `WidgetSnapshot.write()`，并遍历已注册 formId 调 `formProvider.updateForm()` |
| `ets/entryability/EntryAbility.ets`（改） | `onCreate` / `onNewWant` 解析卡片 router 事件带来的 want 参数，通知 RootView 切到「今日」页 |
| `entry/src/main/module.json5`（改） | 注册 FormExtensionAbility |
| `resources/base/profile/form_config.json`（新增） | 卡片配置：`supportDimensions: ["2*2", "2*4"]`、`updateDuration: 1`（30 分钟周期刷新，由 `onUpdateForm` 重算当天课程，跨午夜切换也由此覆盖，不再设 `scheduledUpdateTime`） |

### 快照内容

存**整学期全部课程（含每周 weeks 模式）+ 学期元信息**，而不是只存「今天的课」或「当前周」：

```json
{
  "version": 1,
  "savedAt": 1730000000000,
  "termName": "2025-2026-1",
  "firstMonday": "2026-08-31",
  "totalWeeks": 18,
  "themePrimary": "#0A59F7",
  "courses": [
    { "name": "...", "room": "...", "dayOfWeek": 1, "startSection": 1, "endSection": 2,
      "weeks": [1, 2, 3], "timeLabel": "08:30-10:00", "colorHex": "#1f86c7", "isSelfStudy": false }
  ]
}
```

这样卡片进程在午夜跨天、甚至跨周时都能按 `firstMonday` + `weeks` 自行算出「今天是第几周、今天上哪些课」，完全不依赖 App 被打开（只存当前周的话，周日午夜后卡片会错误显示「没课」）。整学期课程直接复用 `AppViewModel.termCourses`（本来就会拉取），JSON 体积在可接受范围。`themePrimary` 让卡片标题强调色跟随功能二的主题色。

注意分层：**文件里的快照是全量源数据**，而 `formProvider.updateForm()` 推送的 formData 只放当天渲染所需的精简摘要（updateForm 数据量有限制，全学期数据只走文件）。

### 数据流

```
教务同步 / 缓存恢复（App 主进程）
  → WidgetSnapshot.write(整学期课程 + 学期元信息 + 主题色)
  → formProvider.updateForm(formId, formData)   // 立即推送当天精简摘要，秒级生效

系统定时（updateDuration，每 30 分钟）
  → FormAbility.onUpdateForm
  → 读 WidgetSnapshot → 按今天日期重算当天课程 → 渲染
```

### 卡片 UI

- **2x4**：标题行（「今日课程 · 第 N 周」+ 日期）+ 课程行列表（时间、课程名、教室，左侧用课程 `colorHex` 色条）；无课显示「今天没课」
- **2x2**：今天共 N 节课 + 下一节课的名称/时间/教室
- 两者点击均 `postCardAction` router 事件拉起 App 直达「今日」页

### 错误处理

- 无快照（从未登录过）→ 卡片显示「打开 App 同步课表」占位
- 快照日期无法重算出当天（如学期结束）→ 显示缓存内容并标注「数据可能过期」
- formId 列表持久化在 preferences；`onRemoveForm` 时清理

## 功能二：主题强调色多选

### 目标

设置页提供 8 套预设强调色，即点即换，全局按钮/高亮/选中态跟随，重启后保持；其余语义色（背景、文字、错误红、自主学习绿）不变；教务下发的课程 `colorHex` 不受影响。

### 组件

| 文件 | 职责 |
|---|---|
| `ets/core/ThemeStore.ets`（新增） | 预设色板（蓝/靛/紫/粉/红/橙/绿/青，每套含 primary + soft，深色模式提供变体值）；preferences 持久化用户选择；读取失败/非法值回退默认蓝 `#0A59F7` |
| 根组件（`RootView.ets` / `Index.ets`，改） | 启动时从 ThemeStore 加载，写入 `AppStorage`（`themePrimary` / `themeSoft`） |
| 各 UI 页（改） | `$r('app.color.app_primary')` / `app_primary_soft` 引用改为读取 AppStorage 中的主题色 |
| `SettingsPage.ets`（改） | 新增「主题色」卡片：一行圆形色块选择器，选中态加描边，即点即换 |

### 深色模式

预设色在 dark 下若对比度不足，ThemeStore 为每套提供 dark 变体；切换逻辑跟随系统 `colorMode`。

## 验证方式

项目无单测基建，不新增测试框架，以模拟器手动验证为准：

1. 负一屏添加 2x2 / 2x4 卡片，显示今日课程
2. App 内点「同步」后卡片秒级更新
3. 修改系统时间跨午夜，卡片自动切到第二天课程
4. 修改系统时间跨过周日→周一（进入下一周），不打开 App，卡片显示新一周的课程（验证整学期快照的跨周自算能力）
5. 点击卡片拉起 App 到今日页
6. 未登录时添加卡片显示引导占位
7. 设置页切换主题色，各页面即时生效；杀进程重启后保持；深色模式下对比度正常

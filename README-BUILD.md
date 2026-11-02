# SMU_Pocket · 本机构建说明

> 这个目录是**自包含**的：JDK 17、Gradle 8.9、Android SDK 全在 `.toolchain\` 里，
> 不动你的系统环境变量，删掉 `.toolchain` 就等于完全卸载。

## 已验证的结果（本机实测）

| 项目 | 结果 |
|---|---|
| `assembleDebug` | ✅ BUILD SUCCESSFUL |
| APK | ✅ `app\build\outputs\apk\debug\app-debug.apk`（16.55 MB） |
| 单元测试 | ✅ 49 项全过（`testDebugUnitTest`） |
| JDK | `openjdk 17.0.2`（`.toolchain\jdk\jdk-17.0.2`） |
| Gradle | `8.9`（`.toolchain\gradle\gradle-8.9`） |
| SDK | platforms;android-35 · build-tools;35.0.0 · build-tools;34.0.0(AGP 自动补) · platform-tools 37.0.1 |

## 三个一键脚本

双击即可，不用配任何环境变量：

| 脚本 | 作用 |
|---|---|
| `build.bat` | 编译 debug APK |
| `test.bat` | 跑 49 项单元测试，报告在 `app\build\reports\tests\testDebugUnitTest\index.html` |
| `install-and-log.bat` | 装到连着 USB 的手机上，并实时打印冒烟测试日志（tag `JwSmoke`） |

## 下一步：让冒烟测试跑起来

1. 手机开「开发者选项 → USB 调试」，连电脑
2. 双击 `install-and-log.bat`
3. 看日志里这几行：
   - `HTTP 200` → 会话有效
   - `body 长度=...` + `N 行` → 解析成功
   - `第 9 周共 X 门课` → 端到端通了

**注意**：`MainActivity.kt` 里的 `debugCookie` 是抓包当时的会话凭证，**几小时内就会过期**。
如果日志里出现 `body 前 300 字` 是登录页 HTML，就重新扫码、从
DevTools → Application → Cookies 再抄一份，替换掉那个常量再重装。

## 现在还没做的

| 事项 | 状态 |
|---|---|
| 扫码登录（P2） | 未做。`AppViewModel.verifySession()` 还恒返回 false，所以进 App 看到的是「演示数据」 |
| 真实接口接进 UI（P3） | 未做。`AppContainer.jw` 仍是 `FakeJw()`，改成 `RealJw(...)` 才能显示真数据 |
| 切周重新请求 | 未做。现在 VM 是「一次拉全学期再过滤」，真接口按周拉之后要改 |
| 电费（P5） | 未做 |
| 模拟器 | **没装**。要图形界面跑 App / 调试 UI，需要自己装 Android Studio |

## 安全提醒

- `MainActivity.kt` 里的 cookie 等于你的账号，**别把整个目录打包发给别人**，也别提交到 git
- 跑通后把 `debugCookie` 那一行和 `lifecycleScope.launch { ... }` 那段删掉
- `.toolchain\downloads\` 里还有约 460 MB 的安装包，确认构建正常后可以删

---

## 更新（2026-10-30）：已接入真实数据

App 现在显示的是**真实课表 / 真实成绩**，不再是演示数据。

| 能力 | 状态 |
|---|---|
| 课表（按周） | ✅ 真实数据，含上课内容 / 教室 / 真实日期 |
| 切换周次 | ✅ 每次切周重新请求（`zc=第几周`），带请求取消防抖 |
| 成绩 | ✅ 真实 50 条，跨 4 个学期 |
| 学期名 | ✅ `202601` → 「2026-2027 学年 第 1 学期」 |
| 登录态失效 | ✅ 抓到 `code:-401` 自动清 cookie 并退回登录页 |
| 电费 | ❌ 仍是演示数据（真实接口未知） |
| 扫码登录 | ❌ 未接（目前靠 `MainActivity` 里写死的 `debugCookie`） |

### 冒烟测试已默认关闭

`MainActivity` 里加了 `runSmokeTest = false`。
**原因**：它每次启动都会用写死的 `debugCookie` 覆盖 `SessionStore` 里的登录态 ——
一旦你扫码登录成功，它会把新鲜 cookie 冲掉。排查接口问题时再临时改回 `true`。

### cookie 过期怎么办

App 启动时会先用已存的 cookie 试探一次；如果教务返回 `code:-401`，
会自动清空会话并退回登录页（不再是一屏报错卡住）。届时重新扫码、替换
`debugCookie`、重新 `build.bat` 即可 —— 这只是扫码登录接上之前的过渡方案。

### 界面上的三处修复

1. **跨节课程合并成一个色块**（原来是一格有色 + 三格空白）
2. **老师名字截断**（教务把 12 个名字拼在一个字段里 → 只显示前 3 个 + 等N人）
3. **周条自动滚到选中周**（原来第 9 周藏在屏幕右边看不见）

---

## 更新（2026-10-30 晚）：扫码登录已接入

现在**不需要再手动粘贴 cookie 了**。

### 流程

1. 启动 App → 登录页 → 点「打开教务系统登录（扫码）」
2. WebView 打开教务首页（真实页面，含二维码；已截图验证）
3. 每 2 秒：从 `CookieManager` 捞 cookie 存进 `SessionStore`，并发一个**轻量探针**（`zc=1`）判断会话
4. 探针返回 `code == 0` → `loggedIn = true` → RootScreen 自动切到主页，WebView 被销毁
5. 90 秒没探到 → 提示超时，可点「关闭」重试

### 为什么探针只发一个请求

`probeSession()` 直接打 `zc=1`，不走 `bootstrap()`（那要 2~3 个请求）。
扫码时每 2 秒探一次，不能每次都拖三个请求。

### 关掉登录页会清 cookie

`onLoginCancelled()`：没登录成功就 `session.clear()`。
否则下次启动会拿着没认证的 cookie 试一次、失败、再弹回登录页。

### 已修的两个细节

- 登录成功后 ViewModel 直接 `loadAll()`，不需要用户手动刷新
- `probeSession()` 加在 `JwService` 接口上，默认 `false`，`FakeJw` 不用改

### 还没验证的一步

**真正的"扫码"这一步没法自动验证**（需要手机扫）。
已验证的是：登录页能打开、二维码正常显示、轮询在跑、探针正确判断"未登录"。
如果扫完没反应，看 Logcat 的 `JwLogin` tag：探针一直"未登录"说明 cookie 没更新；
如果日志里出现新的 cookie 长度变化，说明登录成功了但探针判断有问题。

---

## 更新（2026-10-30）：电费确认无法自动查

学校电费系统 `zwczndbcz.smu.edu.cn/cgdz/...` 走**微信 OAuth**（`scope=snsapi_base`），
身份是 WeChat openid，App 拿不到 —— 电脑浏览器打不开也是这个原因。

电费页已改成「复制链接 → 微信打开」的三步引导，并显示了完整链接。
详细调研（接口清单、实测返回、以后什么条件下能接）见
`smu-probe/电费接口规格.md`。

---

## 更新（2026-10-30 收官）：移除电费 + UI 优化

### 电费整体移除

调研确认无法自动查询（微信 OAuth，详见 `smu-probe/电费接口规格.md`），
所以连「复制链接」的入口也一起删了，底部导航从 5 个变 4 个：
**今日 / 课表 / 成绩 / 设置**。

删掉的东西：`data/Ecard.kt`、`ui/power/PowerScreen.kt`、`PowerInfo`/`DailyUsage`、
电费相关的 VM 字段与容器依赖、`PowerTest.kt`（它同时含 `CourseTest`，所以单测从 51 降到 45）。

### UI 优化

| 位置 | 改动 |
|---|---|
| 课表 | **用教务自己给的 `bgcolor` 上色**（每门课一种颜色），空课格加浅底，跨节色块合并 |
| 课表 | 副标题加「本周 N 节」 |
| 今日 | 课程行左侧显示**真实上下课时间**（`qssj`/`jssj`，两行：08:00 / 16:55） |
| 今日 | 新增**本周概览**柱状图（每天几节课，今天高亮） |
| 成绩 | 统计从 3 项变 4 项（加权绩点 / 加权平均 / 总学分 / 门数） |
| 成绩 | 学期按**新到旧**排序（原来是接口返回顺序） |
| 设置 | 移除电费状态行 |

### 扫码登录已实测成功

Logcat 实录（2026-10-30）：

```
第 4 次探测：未登录
onPageStarted: https://zhjw.smu.edu.cn/new/welcome.page   ← 扫码后跳转
第 5 次探测：已登录
```

扫完码 WebView 跳到 `/new/welcome.page`，第 5 次探测（约 10 秒）就打通，自动进主页。
**至此 P2 完成。**

### 一个已知的坑：教务会话互踢

同一账号在别处登录，教务会返回
`{"code":-401,"message":"对不起，您的帐号[...]已在别处登录，您已被迫退出。"}`
App 会捕获成 `SessionExpiredException`、清 cookie、退回登录页。
也就是说：**App 里扫码登录后，就别再用网页版登教务**，否则这边会被踢下线。

---

## 更新（2026-10-30 深夜）：本地缓存 + 温和的过期提示

### 问题

教务的 JSESSIONID 是会话 cookie，闲置约半小时到两小时就失效；而且同账号在别处登录会把这边踢下线。
原来的处理是「抓到 401 → 清 cookie → 踢回登录页」，结果是一掉线整个 App 空白，
连「今天上什么课」都看不了。

### 现在的行为

1. 每次成功加载后，把「课表 + 成绩 + 学期配置 + 保存时间」写进
   `filesDir/cache_snapshot.json`（约 10 KB）
2. 启动时**先读缓存立刻渲染**（秒开、断网也能看），再去后台刷新
3. 刷新时如果遇到登录过期：
   - **有缓存** → 保留数据，顶部挂一条横幅：
     「登录已过期，当前显示的是缓存数据 / 更新于 X 小时前，可能不是最新」+「重新登录」
   - **没缓存** → 只能回登录页（否则是一屏空白）
4. 切周失败、网络错误也一样：有缓存就继续显示，只标记为旧

### 验证（已在模拟器上实测）

| 步骤 | 结果 |
|---|---|
| 正常加载后 | `cache_snapshot.json` 9836 字节，`week=9`，含学期名/第一周周一/作息 |
| 把 cookie 换成 `DEADBEEF…` 后重启 | ✅ 数据仍在，顶部出现过期横幅，**没有**跳回登录页 |
| 点横幅上的「重新登录」 | ✅ 回到登录页；缓存保留，等下次登录成功后覆盖 |
| 主动「退出登录」 | 会同时 `cache.clear()`，不留残留 |

### 注意

缓存里**只有课表、成绩、学期配置**，不含姓名/学号/cookie。
演示数据（`demoMode`）不写缓存，避免污染。

---

## 更新（2026-10-30）：成绩页按学期折叠

### 结构

`LazyColumn`，每个学期占**两个 item**：

```kotlin
item(key = "header_$term") { TermHeader(...) }      // 可点击的头
item(key = "body_$term")   { AnimatedVisibility(visible = expanded) { Column { rows } } }
```

### 一个关键细节：AnimatedVisibility 必须一直在组合里

**不能**写成这样：

```kotlin
if (expanded) item { AnimatedVisibility(visible = true) { ... } }   // ❌ 没有动画
```

那样每次展开都是**新建**一个 item，`visible` 从 true 开始，`expandVertically()` 不会播。
正确做法是 body 这个 item **始终存在**，只切换 `visible`：

```kotlin
item(key = "body_$term") { AnimatedVisibility(visible = term in expandedTerms) { ... } }
```

### 默认展开最新学期，且不能有 bug

用**可空的 Set** 表示状态：`null` = 用户还没动过（用默认值），非 null = 已手动操作。

```kotlin
var userExpanded by remember { mutableStateOf<Set<String>?>(null) }
val expandedTerms = userExpanded ?: setOf(newestTerm)
fun toggle(term: String) {
    val current = userExpanded ?: setOf(newestTerm)
    userExpanded = if (term in current) current - term else current + term
}
```

如果直接用 `Set<String>` + `if (empty) setOf(newest)`，会出现：
用户把最新学期**收起** → Set 变空 → 默认逻辑又把它展开 → 点了没反应。

### Header 上多给了点信息

每个学期头显示「N 门 · 均分 X」，右侧箭头用 `animateFloatAsState` 旋转 180°。
收起状态下 4 个学期一屏看完（原来 50 条铺满好几屏）。

---

## 修复（2026-10-30）：今日页被课表选中周带跑

### Bug

在课表翻到第 8 周，「今日」页显示的也变成第 8 周的课，而实际今天是第 9 周。

### 根因（是我引入的）

改成「按周拉取」之后，`UiState.courses` 的语义变成了**「用户选中那一周」**，
但 `todayCourses` 还是从它派生：

```kotlin
val weekCourses = courses.filter { it.occursIn(selectedWeek) }   // ← selectedWeek
val todayCourses = coursesOn(LocalDate.now().dayOfWeek.value)    // ← 间接依赖它
```

一个字段承担了两个语义（课表选中周 / 真实当前周），必然出错。

### 修法：拆成两个数据源

| 字段 | 语义 | 谁会改它 |
|---|---|---|
| `scheduleCourses` | 课表页**选中周**的课 | 切周时（`loadWeek`） |
| `currentWeekCourses` | **真实当前周**的课 | 只在 `loadAll` 时（启动/刷新） |

今日页相关的一切只读 `currentWeekCourses`：

```kotlin
private val todayIndex: Int get() = LocalDate.now().dayOfWeek.value

val todayCourses: List<Course>
    get() = currentWeekCourses
        .filter { it.occursIn(currentWeek) && it.dayOfWeek == todayIndex }
        .sortedBy { it.startSection }

val dailyCounts: List<Int>   // 本周概览柱子
    get() = (1..7).map { d -> currentWeekCourses.count { it.occursIn(currentWeek) && it.dayOfWeek == d } }

val weekTotal: Int get() = currentWeekCourses.count { it.occursIn(currentWeek) }
```

课表页则用 `scheduleWeekCourses` / `selectedWeekTotal`。

缓存也跟着拆成两份（`CachedSnapshot.currentCourses`），否则重启后仍会串。
旧缓存没有 `currentCourses` 字段时回退到 `courses`，向后兼容。

### 回归验证

1. 今日页截图（切周前）
2. 课表点「上一周」→ 显示 **第 8 周 · 该周 14 节**（真实拉取成功）
3. 切回今日页 → **仍然**是「第 9 周 · 星期五 · 机能实验学二」，本周概览 **共 13 节**

两张今日页截图 sha256 完全相同（`4ca1c5c9…`），而课表是第 8 周的 14 节 —— 数据真正解耦了。

---

## 更新（2026-11-02）：今日页新增「明日课程」

### 数据层（AppViewModel）

明日**不能**直接复用当前周的数据 —— 今天是周日时，明天属于下一周。
所以：

```kotlin
private val tomorrowDate: LocalDate get() = LocalDate.now().plusDays(1)

val tomorrowWeek: Int?               // 明天是第几周（由 firstMonday 推算 + clamp）
val todayDataReady: Boolean          // currentCoursesWeek == currentWeek
val tomorrowDataReady: Boolean       // 跨周时需要 nextWeekCourses 已拉到
val tomorrowCourses: List<Course>    // 从「明天那一周」的数据里按 dayOfWeek 过滤
```

`loadAll()` 里只在**跨周时**补拉一次下一周（`nextWeekCourses`），平时不多发请求。

### UI（TodayScreen）

卡片顺序：**今日课程 → 明日课程 → 本周概览**。
明日卡标题右侧显示「明天 · 11月3日 星期二」，内容三种状态：

| 情况 | 显示 |
|---|---|
| 数据未就绪（跨周且没拉到） | 「明天是第 N 周，那周的课表还没加载」 |
| 有课 | 复用 `CourseRow`（时间 + 课名 + 教室/老师/内容） |
| 没课 | 工作日「明天没有课，好好休息」/ 周六「明天是周六，没有课，睡个懒觉吧」/ 周日「明天是周日，没有课，好好休息」 |

### 顺带修的一个误报

模拟器重启后时间跳到 11-02（第 10 周），而缓存还是 2 天前第 9 周的。
原来的实现会显示「今天没有课」「明天没有课」——**是错的**，实际是缓存过期。
现在 `UiState.currentCoursesWeek` 记录「这份 currentWeekCourses 属于第几周」，
和 `currentWeek` 不一致时显示：

- 今日卡：「课表还没更新到本周（当前是第 10 周）」
- 明日卡：「明天是第 N 周，那周的课表还没加载」

### 验证

- 编译通过
- 截图为「缓存过期」状态下三张卡片的实际渲染（横幅 + 今日提示 + 明日提示）
- **含真实明日课程**的截图需要重新扫码（WebView 里残留会话已失效）

---

## 更新（2026-11-02）：标注「自主学习」课程

### 数据层排查（实测 118 行真实数据）

`jxhjmc`（教学环节名称）全部取值及其与教室的关系：

| jxhjmc | 行数 | 有教室 |
|---|---|---|
| 理论 | 67 | 64 有 / 3 无（缺数据） |
| **自主学习** | **19** | **0 有 / 19 无** |
| 实验 | 14 | 全部有 |
| 考试（理论） | 8 | 7 有 / 1 无 |
| 考试（实操） | 4 | 全部有 |
| 技能 | 4 | 全部有 |
| 见习 | 2 | 0 有 / 2 无 |

结论：**「自主学习」19 行全部没有教室，与其它类别完全可分**，规则可以精确判定，不会误伤。

另外 `sknrjj`（上课内容）里没有任何「自主/线上/网络」字样，`kcmc` 里也没有 —— 所以线索**只在 `jxhjmc`**。

### 实现

1. `Course` 增加 `isSelfStudy: Boolean = false`
2. `Mappers.kt`：
   ```kotlin
   private fun isSelfStudyKind(jxhjmc: String?): Boolean {
       val v = jxhjmc?.trim().orEmpty()
       if (v.isEmpty()) return false
       return listOf("自主", "线上", "网络", "慕课", "在线", "自习").any { v.contains(it) }
   }
   ```
   顺带兼容将来教务改叫法。
3. `CachedCourse` 也带上该字段，否则缓存恢复后标签会丢。
4. 新组件 `SelfStudyChip`：浅绿 `#2E7D32` + 14% 透明度底，和蓝/橙/红课块区分。

### UI

| 位置 | 表现 |
|---|---|
| 今日卡 | 课名右侧 `自主学习` 标签；教室替换为「**无需前往教室**」 |
| 课表网格 | 课名下 `自主` 紧凑标签（格子只有 ~45dp 宽，四字放不下） |

### 验证（真实数据，第 10 周）

- 今日：`14:30-15:55 综合高级英语` + `自主学习` + `无需前往教室 · （教师名略）`
- 课表：`综合高级英语`（周一 6-7）、`药理学`（周四 8-9）均显示 `自主` 标签

---

## 更新（2026-11-02）：缓存提示条降级为「提示」而非「报错」

原来用的是 `errorContainer`（红底），视觉上像严重故障。
实际上用缓存是**正常降级**，不该报警。

### 现在的样式

| 元素 | 取值 |
|---|---|
| 背景 | 浅黄 `#FFF3CD`（深色模式 `#3A3220`） |
| 正文 | 深棕 `#5C4A00`（深色 `#EDE0B8`） |
| 次要文字 | `#8A7430`（深色 `#BDAE85`） |
| 「重新登录」 | 蓝色 `#1A73E8`（深色 `#8AB4F8`） |
| 图标 | `Info` 而不是警告图标 |

### 文案

> 当前显示的是缓存课表，可能未包含最新调课信息。如有变动，请点击右侧重新登录刷新。

下面一行小字保留「更新于 X 分钟前」——它直接支撑上面那句"可能不是最新"。

### 可关闭

右侧加了 `X`：`rememberSaveable(state.cachedAt)`，
所以**关掉后要等缓存刷新（cachedAt 变化）或重启 App 才会再出现**，
不会在你每次切 tab 时反复弹。

### 验证（断网触发，不动真实会话）

1. `svc wifi disable` + `svc data disable` → 重启 App → 加载失败 → 走缓存并出现提示条
2. 点 `X` → 提示条消失，内容上移（截图 hash 变为 `6229c0b8…`）
3. 恢复网络重启 → 无提示条，正常加载

---

## 更新（2026-11-02）：去掉顶部提示条，改到「设置」里手动同步

### 主界面

`RootScreen` 里的 `CacheNoticeBar` **整个删除**，主界面不再有任何提示条。
缓存/过期状态只在「设置」里体现。

### 设置页

顶部一个整宽实心按钮「**同步最新课表与成绩**」，下面小字：

> 当前数据更新于 2 分钟前，如发现课程有变动或无法加载，请重新扫码登录。

点击 → `AppViewModel.syncNow()`：

```kotlin
fun syncNow() {
    weekJob?.cancel()
    if (!session.isLoggedIn()) {
        backToLogin(SessionExpiredException("登录已过期，请重新扫码登录"))
        return
    }
    jw = jwProvider()
    explicitSync = true
    loadAll()
}
```

- 会话有效 → 静默刷新（按钮显示「同步中…」+ 转圈，成功后时间变「刚刚」）
- 会话失效 → `backToLogin()`：清登录态、回登录页，页面上显示「登录已过期，请重新扫码登录」

### 顺带修掉一个更严重的 bug

测试时发现：**会话被清掉后点「同步」，`jwProvider()` 会返回 `FakeJw`**，
于是"同步"会静默加载**演示数据**并写进缓存，而设置页还标着「已接入」——
用户会以为看到的是真实课表。

三道防线：

1. `syncNow()` 先检查 `session.isLoggedIn()`，没有就直接回登录页
2. `loadAll()` 开头同样检查（非演示模式下无会话 → 回登录页）
3. `persistCache()` 增加 `if (!session.isLoggedIn()) return`，假数据永远进不了缓存

### 验证

| 场景 | 结果 |
|---|---|
| 会话有效点同步 | 缓存文件 mtime 从 `03:39:44` → `03:40:04`，无跳转 |
| 会话失效点同步 | 跳登录页 + 显示「登录已过期，请重新扫码登录」；`shared_prefs` 被清空 |
| 失效后的缓存 | 12437 字节，**仍是真实数据**（未被演示数据覆盖） |

---

## 更新（2026-11-02）：今日页去掉问候语，日期升为主标题

原来：

```kotlin
ScreenHeader(
    title = greeting(),                       // 夜深了 / 早上好 …
    subtitle = "2026-11-02 · 星期一 · 第 10 周",
)
```

现在直接一个标题，`titleLarge`(22sp) + `SemiBold`：

```kotlin
Text(
    text = "$today · 星期${cn(today.dayOfWeek.value)} · 第 ${state.currentWeek} 周",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
)
```

顺带删掉了 `greeting()` 函数和 `ScreenHeader`、`LocalTime` 两个不再使用的 import。
内边距沿用 `ScreenHeader` 的 20/20/20/8，卡片位置没有跳动。
实测一行放得下，不会被挤成两行。

---

## 更新（2026-11-02）：今日页新增「近期考试」

### 数据层排查（实测 118 行整学期总表）

| 假设 | 实测结果 |
|---|---|
| `khfsmc` 含「考试」 | ❌ **本接口根本不返回该字段**（0/118），只有 `jxhjmc` |
| `qsrq` 给考试日期 | ❌ 整学期总表**没有 `qsrq`**（0/118，只有按周查询才有） |
| 考试记录在哪 | ✅ 在 `jxhjmc` = `考试（理论）` / `考试（实操）`，共 **12 行** |
| 地点 | ✅ `jxcdmc`，如 `304机房`、`手术学基础实验室(1)(本部)` |

**日期怎么来**：每行考试都有 `zc`（单周，如 `"14"`）和 `xq`（星期几），
用 `第一周周一 + (周次-1)*7 + (星期-1)` 精确推出。

```kotlin
fun dateOf(firstMonday: LocalDate, week: Int, dayOfWeek: Int): LocalDate =
    firstMonday.plusWeeks((week - 1).toLong()).plusDays((dayOfWeek - 1).toLong())
```

### 零额外请求

`RealJw.bootstrap()` 为了数总周数，本来就会拉一次整学期总表（`zc=` 空）。
现在把这次响应缓存进 `termRows`，`termCourses()` 直接复用 —— **不多发一个请求**。

### 实现

| 层 | 改动 |
|---|---|
| `CourseData` | 加 `khfsmc` 字段（本接口没有，留作兼容） |
| `Course` | 加 `isExam` |
| `Mappers` | `isExam = jxhjmc 含「考试」|| khfsmc 含「考试」`；日期缺失时用 `dateOf` 推算 |
| `JwService` | 新增 `termCourses()`（默认空实现） |
| `RealJw` | 复用 `termRows`，不重复请求 |
| `FakeJw` | 演示数据加两场考试（相对今天 +6/+21 天） |
| `UiState.upcomingExams` | `isExam && date >= 今天`，按日期升序 |
| 缓存 | `CachedSnapshot.exams`，离线也能看 |

### UI

在「本周概览」下方，样式与其它卡片一致：

```
近期考试
11月30日   药理学
周一       304机房 · 08:00-09:25
```

左列日期 + 星期，右侧课名 + 地点/时间；最多显示 6 场；没有考试时显示「近期暂无考试安排」。

### 实测结果（第 10 周，2026-11-02）

正确列出：11月30日 药理学（304机房）、12月14日 综合高级英语（地点待定）、
12月21日 手术学基础、12月23日 机能实验学二 …
**已过期**的 10月8日 / 10月15日 / 10月31日 三场被正确过滤。

### 文字微调（2026-11-02）

卡片标题「近期考试」→「**考试安排**」（里面既有将来的也有本月的，叫"近期"不准确）。
空状态仍为「近期暂无考试安排」。

### 成绩页默认收起（2026-11-02）

原来默认展开最新学期（`userExpanded ?: setOf(newestTerm)`），现在改成：

```kotlin
// 默认**全部收起**，用户点了哪个就展开哪个
var expandedTerms by remember { mutableStateOf<Set<String>>(emptySet()) }

fun toggle(term: String) {
    expandedTerms = if (term in expandedTerms) expandedTerms - term else expandedTerms + term
}
```

顺带删掉了 `newestTerm` 和「null = 未操作过」那套双状态逻辑，现在只有一个 Set，语义更直白。

---

## 打包发布（2026-11-02）

### 打包前清理（已完成）

- 删除了 `MainActivity.debugCookie` —— 那是一条**真实会话 cookie**，不能随 APK 分发
- 删除了 `JwSmokeTest.kt` 及 `runSmokeTest` 接线（扫码登录已通，冒烟测试不再需要）

### 开启 R8 并实测

`app/build.gradle.kts` 的 `release` 已改为 `isMinifyEnabled = true`，并用真机验证过：

| 包 | 大小 |
|---|---|
| `app-debug.apk` | 16.66 MB |
| `app-release-unsigned.apk`（R8 开启） | **1.52 MB** |
| 签名后 | 1.54 MB |

签名后安装到模拟器，今日页 / 明日课程 / 本周概览 / 考试安排全部正常，
`logcat` 无 `SerializationException`、`ClassNotFound`、`FATAL`。
说明 **kotlinx.serialization 自带的 consumer ProGuard 规则足够**，不需要手写 keep 规则。

### 产物路径

| 生成方式 | 路径 |
|---|---|
| Android Studio 向导（Build → Generate Signed Bundle/APK） | `app\release\app-release.apk` |
| 命令行 `gradlew assembleRelease`（未配 signingConfig） | `app\build\outputs\apk\release\app-release-unsigned.apk` |

### 一个必须知道的坑

debug 包和 release 包 **applicationId 相同但签名不同**，
所以自己手机上装过 debug 版的话，要先卸载才能装签名版（否则 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。
同学手机上没有旧版，不受影响。

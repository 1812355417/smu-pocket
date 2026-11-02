# 南医大口袋（SMU Pocket）· 架构与开发流程

> 目标：仿 `Hello-Minecraft/gzucm-pocket` 的架构，做一个南方医科大学版 Android 客户端。
> 本文只谈架构与流程，**不含业务代码**。接口对齐后再进入实现。
>
> **进度**：P0（架构定稿）与 P1（骨架，假数据）已完成，代码见仓库根目录 `README.md`。
> P2 起需要 `capture-checklist.md` 里的真实接口信息。

---

## 0. 结论先行

1. **抄架构，不抄代码。** 参考项目的骨架是：单 module、**无 DI**、OkHttp + 手写 `CookieJar`、Compose、**单 ViewModel**、解析器是**纯函数**、用**真实响应脱敏后当 fixture** 跑单测。这套东西的价值在于"接口改版时能 10 分钟定位是请求变了还是解析崩了"，不在花哨。

2. **真正的架构分叉不在 UI，在登录与传输层。** 教务系统大概率能被 OkHttp 用 Cookie 直接打；电费/一卡通则经常**必须**走 WebView（参考项目的 `ecard` 就是因为签名/token 拿不到，改成在 WebView 里劫持 `XMLHttpRequest`）。所以第一天就要把 `Transport` 抽象成策略，别把 OkHttp 写进 UI。

3. **一个高价值假设，需要用第一批抓包证实或证伪：**
   你的 `smu-auto-eval-loop.user.js` 显示南医大教务在 `zhjw.smu.edu.cn`，且是 **EasyUI + jQuery Raty + 顶部搜索框 + JSP 风格菜单**。这套特征高度吻合**强智科技（qzdatasoft）系教务**，而广中医那套（`/new/welcome.page`、`menuItem`、`xxxDatas`、`page/rows/sort/order`、`{"data":"","rows":[]}`）也是强智。
   **如果同厂商** → 课表/成绩的接口形状大概率同构，P3/P4 适配成本从"周"降到"天"，参考项目那三份接口规格 md 可以直接当模板套。
   **如果不是**（比如是老版 `/jsxsd/` HTML 表格版，或者正方 `jwglxt`）→ 解析层从 JSON 换成 Jsoup 抓 HTML `<table>`，工作量翻倍。
   → 所以清单里的 B1-1「厂商指纹」是**第一优先级**。

---

## 1. 分层架构

```
smu-pocket/
├─ app/src/main/java/com/smu/pocket/
│  ├─ App.kt / MainActivity.kt
│  ├─ core/
│  │   ├─ Net.kt            OkHttp + CookieJar + 超时/重试 + 统一 UA
│  │   ├─ Session.kt        登录态：cookie 存取(EncryptedSharedPreferences)、登录状态机
│  │   ├─ Transport.kt      ① OkHttpTransport  ② WebViewTransport（策略）
│  │   ├─ Models.kt         Course / Grade / PowerInfo / Semester ...
│  │   ├─ Semester.kt       学期代码、第一周周一、周次↔日期换算
│  │   └─ Alerts.kt         上课提醒 / 电费阈值提醒
│  ├─ data/
│  │   ├─ Jw.kt             教务：请求 + 解析（课表、成绩、校历）
│  │   └─ Ecard.kt          电费/一卡通：请求 + 解析（余额、每日明细、缴费入口）
│  └─ ui/
│      ├─ AppViewModel.kt   单一状态中枢（StateFlow），所有网络调用都在这
│      ├─ RootScreen.kt     底部导航 / 页面切换
│      ├─ login/  schedule/  grades/  power/  today/  settings/  theme/
├─ app/src/test/
│  ├─ java/…                ParsingTest / WeeklyScheduleTest / EcardParseTest / SemesterTest / LoginFlowTest
│  └─ resources/            ← 真实响应脱敏后的 fixture（.json / .html）
├─ smu-probe/               ← Python 探针 + 接口规格 md（不进 apk）
│  ├─ 教务接口规格.md
│  ├─ 电费接口规格.md
│  └─ fixtures/
└─ docs/
```

### 参考项目文件 → 南医大对应文件 → 需要你什么才能写

| 参考项目 | 南医大对应 | 写它需要的输入 |
|---|---|---|
| `core/Net.kt` (12K) | `core/Net.kt` | 登录后任意一条成功请求的 cURL（拿 UA / 是否有自定义 Header / 是否要 Referer） |
| `core/Models.kt` | `core/Models.kt` | 课表 / 成绩 / 电费各一份 raw response + 字段字典 |
| `data/Jw.kt` (19K) | `data/Jw.kt` | 登录链路 + 课表 + 成绩的请求与响应（清单 B1/B4/B5） |
| `data/Ecard.kt` (12K) | `data/Ecard.kt` | 电费系统的请求与响应（清单 C） |
| `ui/AppViewModel.kt` (21K) | `ui/AppViewModel.kt` | 不需要接口信息，P1 就能起 |
| `ui/schedule/*` | 同 | 作息时间表（第 1 节几点上）+ 课表字段 |
| `ui/power/EcardSyncSheet.kt` (20K) | 同 | 电费的会话机制（决定要不要 WebView） |
| `gzucm-probe/*.py` | `smu-probe/*.py` | 你自己手动抓也行，见清单 §7 |

### 依赖选择（刻意从简）

| 用 | 不用 | 理由 |
|---|---|---|
| OkHttp | Retrofit | 教务是表单 POST + 很可能要解析 HTML，Retrofit 的注解模型帮不上忙 |
| kotlinx.serialization **或** org.json | Gson | 教务字段名是中英混排 + 有时是 `""` 字符串冒充数字，手写解析更稳 |
| Jsoup（**若**返回 HTML） | — | 老版教务的课表/成绩是 `<table>`，只能靠它 |
| SharedPreferences/DataStore + 手动 JSON 缓存 | Room | 先不上；要离线看历史电费曲线时再考虑 |
| 手写构造器传依赖 | Hilt/Koin | 参考项目没有 DI，单 module 直接 new，降低心智负担 |
| 自建导航状态 | Navigation-Compose | 页面就 6 个，`RootScreen` 里 when 一下更直观 |

---

## 2. 关键机制设计

### 2.1 登录：复用学校登录页，不在 App 内 POST 密码

这是我从参考项目抄来的最重要一条，也是**唯一能规避验证码的方案**：

- 教务系统越来越爱加**图形验证码 / 滑块**，直接 POST 登录表单要接 OCR 或打码平台；
- 密码字段常被前端 JS **加密**（RSA/AES/CryptoJS），逆向出来也会随改版失效；
- 若是**统一身份认证（CAS）**，中间还夹着多跳 302 + ticket 校验；
- 反过来，走学校自己的登录页，"微信扫码登录"是**白送的**。

做法：
1. 全屏 `WebView` 加载教务登录页，用户自己完成**扫码**（或极验滑块）；
2. **轮询**捞 cookie（1.5s 一次），不要等某一次 `onPageFinished`——扫码是页面内异步完成的；
3. `CookieHarvester.harvest()` 内部先 `flush()` 再 `getCookie(url)`，**按 host 分别存**
   （CAS 一份、教务一份，只存一个必然出错）；
4. 存 `EncryptedSharedPreferences`，OkHttp 的 `CookieJar` 复用；
5. **登录成功的唯一可靠判定 = 探针**：拿 cookie 真打一次课表接口，
   返回数据才算成功。不要用 URL 匹配来判断（扫码成功后主文档 URL 可能根本不变）。

> 南医大实测结论：密码登录有**极验滑块**，破解成本过高 →
> **只做扫码登录**。这不是妥协，反而把最脏的一块（验证码逆向）整个删掉了。
>
> ⚠️ 但扫码引入一个新的 UX 风险，必须早测：**会话能活多久**。
> 若 `Set-Cookie` 没有 `Expires`/`Max-Age`（session cookie），
> 配合服务端较短的 session 超时，就是「每次冷启动都要重新扫码」。
> 实测方法见 `capture-checklist.md` Q2。

> 参考项目 README 明确写了「账号密码只在学校的认证页面输入，App 不接触、不保存」。照抄这个态度，合规风险最低。

**已知（2026-10）**：南医大教务密码登录有极验滑块，App 只做扫码登录。

### 2.2 传输层：两个实现，一个接口

```kotlin
interface Transport {
    suspend fun request(spec: RequestSpec): Response   // url / method / headers / form|json|raw
}

class OkHttpTransport  : Transport   // 教务：带 cookie 直接打
class WebViewTransport : Transport   // 电费：页面自己算签名，我们只截响应
```

`WebViewTransport` 的成熟套路（参考项目已验证）：
1. 不可见 WebView（1dp）加载目标页；
2. 注入 JS 猴补丁 `XMLHttpRequest.prototype.open/send` 和 `window.fetch`，记录 `{url, method, headers, body}` 与 `responseText`；
3. 通过 `addJavascriptInterface` 把结果回传 Kotlin；
4. Kotlin 侧只做解析，**不用猜 token/签名/时间戳怎么生成**。

取舍：WebView 方案慢（1~3s）、吃内存，但**不会被签名规则改版打死**。教务能用 OkHttp 就别用 WebView。

### 2.3 解析层：纯函数 + fixture（这条决定项目能活多久）

```kotlin
// data/Jw.kt 里长这样，不碰网络
fun parseTimetable(raw: String): List<Course>
fun parseGrades(raw: String): List<Grade>
```

开发流程固定为：
**你给 raw response → 我放进 `test/resources/` 当 fixture → 写 parse + 单测 → UI 接真数据。**

带来的好处：
- 没有网络也能开发 UI（P1 阶段就能把课表画出来）；
- 接口改版时，把新响应丢进 fixture 跑 `./gradlew test`，一眼看出是"请求变了"还是"解析崩了"；
- 参考项目 110 个单测，主力就是 `ParsingTest` / `WeeklyScheduleTest` / `EcardParseTest`。

### 2.4 学期与周次（课表的地基）

必须搞到三样，缺一样课表就是错的：
1. **当前学期代码**（形如 `2025-2026-2` 或 `202502` / `202501`）；
2. **第一周周一日期**（校历接口能给就自动取，给不了就手填兜底）；
3. **作息时间表**（第 1 节 08:00–08:45、第 2 节 ……）——参考项目这条是**手填兜底**的，因为接口不返回。请你优先翻一下教务里有没有「作息时间 / 校历 / 教学安排」页面。

换算：`本周 = floor((今天 − 第一周周一) / 7) + 1`，注意跨年、单双周、超过总周数时要钳住。

### 2.5 「按周抓取」要不要抄？——先做实验，别先写并发

参考项目的洞察是：同一个课表接口，加周次参数后**多出** `sknrjj`（上课内容）/ `qsrq`（真实日期），所以并发 5 路逐周拉，全失败时退回学期总表。

这是**南医大必须自己验证**的一条（清单 B4-3 的 A/B 实验）：
> 同一接口，分别用「空周次」和「第 8 周」各抓一次，两份 response 都给我。
> 我 diff 字段集：如果周次参数只是**过滤**，没有新增字段 → 别抄并发，一次全量拉省事又省请求；如果确实**多了字段** → 才值得逐周拉。

不要为了抄而抄，多一个并发就多一份被限流的风险。

### 2.6 通知与后台（与接口无关，最后做）

| 功能 | 方案 | 坑 |
|---|---|---|
| 上课前提醒 | `AlarmManager` + 精确闹钟 + 本地通知 | 别用 WorkManager，周期任务不保证准点 |
| 电费每日静默同步 | WorkManager 周期任务 + "每天最多一次"去重 | 参考项目的 `PowerAlert` / `PowerSilentSync` 就是这个 |
| 电费低于阈值提醒 | 通知 + 去重 | 0 值陷阱：解析失败时别当成"电量 0"去报警（`EcardParseTest` 专门测这个） |

### 2.7 安全与合规（写进 README 和「关于」页）

- 密码不落地、不经过 App 代码；
- cookie/token 存 `EncryptedSharedPreferences`，退出登录即清空；
- **写操作一律不做**：选课、退课、缴费下单、评教提交，只做「查看入口 + 跳原网页」。
  （你的 `smu-auto-eval-loop.user.js` 是写操作自动化，属于另一个合规层级，**不要包进 App**）
- 限速：并发 ≤ 5，用户操作才触发，电费每天一次；
- 非官方声明、免责声明、请求频率说明、不开源/开源（MIT）二选一；
- 分发方式：自用则直接 APK / GitHub Release；上应用商店需要软著 + 备案，不建议。

---

## 3. 开发流程（6 阶段）

| 阶段 | 内容 | 需要你给的 | 我的产出 |
|---|---|---|---|
| **P0 侦察** | 确认厂商与登录机制，把"能不能做"钉死 | 清单 §1 + B1（半小时） | 厂商判定结论 + 3 份接口规格 md 的骨架 |
| ~~**P1 骨架**~~ ✅ | Gradle 工程、Compose 主题、底部导航、`Net`/`Transport`/`Session`、假数据课表跑通、15 项单测 | 不依赖接口 | 已完成：能跑的空壳 App |
| **P2 登录真通** | WebView 登录 → cookie 落盘 → 打一条真实接口验证 | B1 的完整登录链路 | 登录态可用 |
| **P3 课表** | 解析 + 周次 + 按周翻页 UI + 今日页 | B2（校历/作息）+ B4（课表） | 课表功能完成 |
| **P4 成绩** | 课程/等级/体测 + 绩点统计 | B5 | 成绩功能完成 |
| **P5 电费** | 余额 + 每日明细 + 趋势图 + 提醒 | C（电费系统全量） | 电费功能完成 |
| **P6 打磨分发** | 提醒、深色、关于/更新日志、APK 签名与发布 | 无 | 可安装的 release |

**关键路径**：`P0 → P2 → P3`。P4/P5 可以和 P3 并行，因为它们是独立文件（`Jw.kt` 里加函数 / `Ecard.kt` 另起）。

**P1 可以现在就动工**，因为它完全不含接口逻辑——你确认一下要不要先搭（见对话末尾的提问）。

---

## 4. 功能范围建议

**必做（MVP）**：登录、周课表、成绩、电费
**选做（确认后加）**：今日、上课提醒、电费提醒、考试安排、空教室查询、校历
**明确不做**：选课/退课、缴费下单、评教提交、任何"替用户点提交"的功能

---

## 5. 数据模型草案（先对齐字段，再写代码）

```kotlin
data class Course(
    val name: String, val teacher: String?, val room: String?,
    val dayOfWeek: Int,        // 1..7
    val startSection: Int, val endSection: Int,
    val weeks: Set<Int>,       // 单双周/跳周都塞这里
    val content: String?,      // 上课内容（按周才有，待验证）
    val date: String?          // 真实日期 yyyy-MM-dd
)

data class Grade(
    val course: String, val term: String,
    val score: Double?, val gpa: Double?,
    val credit: Double, val type: String?   // 必修/选修/实践
)

data class PowerInfo(
    val room: String, val balanceKwh: Double,
    val dailyUsage: List<Pair<String, Double>>   // 日期 → 用电量
)
```

真实字段名出来后再定 `@SerialName` 或手写映射——**字段字典（清单 §7-4）比 response 样例更重要**，因为 `zcj` / `zcjfs` / `zf` 这种"多份冗余字段"光看名字猜一定会错。

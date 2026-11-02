# 南医大口袋（SMU Pocket）

南方医科大学学生自用的 Android 客户端：课表、成绩、宿舍电费。

> **非官方项目。** 与南方医科大学无任何隶属关系，未获学校授权或认可。
> 数据直连学校系统，不经过任何第三方服务器。

---

## 当前状态：P1 骨架

**能跑起来，但数据全是假的。** 这是刻意的——先把架构、UI、测试钉住，等接口信息到位再换数据层。

| 模块 | 状态 | 说明 |
|---|---|---|
| 工程 / Gradle / Compose 主题 | ✅ | JDK 17、compileSdk 35、minSdk 26 |
| 底部导航 5 页（今日/课表/成绩/电费/设置） | ✅ | 交互都通，数据来自 `FakeJw` / `FakeEcard` |
| 周课表网格 + 周次切换 | ✅ | 跨节课程会渲染成连续色块 |
| 成绩聚合（加权绩点/加权平均） | ✅ | 等级制课程正确排除在加权平均分之外 |
| 电费趋势图 + 可用天数 | ✅ | 含「解析失败返回 0 不能报警」的保护 |
| 周次换算 / 周次串解析 | ✅ | 15 项单测（`SemesterTest`） |
| 教务数据模型（课表 / 成绩） | ✅ | 按 40 + 42 个真实字段定；`zc` 数字/字符串都能吃 |
| JSON 解析 + 脱敏 fixture 单测 | ✅ | 3 份真实 fixture；30 行课表全部映射成功 |
| 学期起点反推（无校历接口） | ✅ | 从 `qsrq` 反推，两条真实记录都指向 2026-08-31 |
| 登录（WebView 开学校登录页 + 轮询捞 cookie + 落盘） | 🟡 | 扫码流程与 cookie 按 host 落盘已通；登录成功探针与真实接口待 P2 |
| 真实教务接口接线（`RealJw`） | 🟡 | 接口与模型已确认，剩下把 `JwApi` 接到 `RealJw` 并跑通 |
| 真实电费接口 | ❌ | 等 §6 |

切换点只有一处：`app/src/main/java/com/smu/pocket/App.kt` 里的 `AppContainer`，
把 `FakeJw()` 换成 `RealJw(transport)` 即可。

---

## 目录

```
smu-pocket/
├─ app/src/main/java/com/smu/pocket/
│  ├─ core/        Models / Semester / Transport / Net(OkHttp) / Session(cookie)
│  ├─ data/        Jw.kt（教务）· Ecard.kt（电费）—— 每个系统一个文件：请求 + 解析
│  └─ ui/          AppViewModel（单状态中枢）+ 6 个页面
├─ app/src/test/   SemesterTest / PowerTest —— 纯逻辑，无需网络
├─ docs/
│  ├─ architecture.md       架构、关键机制、6 阶段开发流程
│  └─ capture-checklist.md  ⭐ 需要你抓的接口清单（填空式）
└─ smu-probe/      抓包脚本与接口规格 md（不进 apk）
```

设计取舍（为什么不引框架）见 `docs/architecture.md` 第 0 节：
**无 DI、无 Room、无 Retrofit、无 Navigation 库**，单 module 直接 new。
和参考项目 `gzucm-pocket` 保持一致，接口改版时改一个文件就能定位问题。

---

## 构建

需要 **JDK 17** + **Android SDK 35**。仓库里没有 `gradle-wrapper.jar`（二进制不好放进对话），
所以有两条路：

**A. Android Studio（推荐）**
1. `File → Open` 选 `smu-pocket` 目录
2. 首次打开会自动补 Gradle wrapper 并下载 Gradle 8.9
3. 若提示 SDK 路径缺失，在根目录建 `local.properties`：
   ```properties
   sdk.dir=C\:\\Users\\<你>\\AppData\\Local\\Android\\Sdk
   ```
4. 直接 Run

**B. 命令行**
```bash
cd smu-pocket
gradle wrapper --gradle-version 8.9   # 本机已装 gradle 时，一次性生成 wrapper
./gradlew testDebugUnitTest          # 跑单测（不需要真机）
./gradlew assembleDebug              # 出 APK
```

> ⚠️ 说明：这套源码是在**没有 JDK 17 / Android SDK 的环境里写的，没做过编译验证**。
> 首次构建若报错，大概率是版本号或依赖解析问题，把报错原文贴出来即可，别自己硬改版本。

---

## 为什么登录要在 WebView 里做

`LoginScreen` 只做一件事：打开学校自己的登录页，用户自己输账号密码，我们只接登录成功后的 cookie。

换来四件事：**验证码、密码前端加密、统一身份认证多跳、微信扫码**全部免适配；
代价是登录时多一层 WebView（1~3 秒），以及要自己判定「什么时候算登录成功」。

反面做法是在 App 里 POST 登录表单——一旦学校加验证码或改加密方式就得重写。

---

## 下一步（按顺序）

1. 抓包 → 填 `docs/capture-checklist.md`（**先抓 §8 的 20 分钟最小集**）
2. 拿到厂商指纹与登录链路 → `RealJw` 落 cookie（P2）
3. 拿到课表响应 → 存 `test/resources/` 当 fixture → 实现 `parseTimetable` + 单测（P3）
4. 成绩（P4）、电费（P5）、打磨与分发（P6）

---

## 免责

- 本项目仅供学习交流与个人使用
- 请遵守学校的信息系统使用规定，不要高频请求
- 不做任何写操作（选课、退课、缴费下单、评教提交），只提供跳转
- 因使用本软件产生的任何后果由使用者自行承担

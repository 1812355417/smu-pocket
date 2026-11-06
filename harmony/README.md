# 南医大口袋 · HarmonyOS 客户端

用 **ArkTS + ArkUI** 从零重建的鸿蒙原生客户端，与 Android（根目录）/ Apple（`apple/`）客户端按同一教务协议实现。

**课表 · 成绩 · 考试网考号 · 离线可用 · 深色模式跟随系统**

> **非官方项目。** 与南方医科大学无任何隶属关系。数据直连学校系统，不经过任何第三方服务器；**只读不写**。

## 构建

需要 **DevEco Studio 5.0+**（HarmonyOS NEXT，API 12 / 5.0.0），官方支持 Windows / macOS：

```bash
git clone https://github.com/1812355417/smu-pocket.git
# 用 DevEco Studio「Open」打开 harmony/ 目录，等待 ohpm 依赖同步完成
```

- **模拟器 / 真机运行**：点 Run 'entry'，自动生成调试签名；
- **发布 HAP**：Build → Build App(s)/Hap(s) → Build Hap(s)；正式发布需在 Project Structure → Signing Configs 配置自己的签名；
- 也可以用命令行：`hvigorw assembleHap`（需本机已装 HarmonyOS SDK 与 ohpm）。

> Linux 没有官方 DevEco Studio / 命令行工具，请用 Windows 或 macOS 构建。

## 功能对齐 Android 端

| 页面 | 能力 |
|---|---|
| **今日** | 日期标题 · 今日课程 · 明日课程（周日自动拉下一周）· 本周概览柱状图 · 考试安排（含**网考号**，与列表同帧渲染不闪烁） |
| **课表** | **固定 14 节网格**（含 11~14 节晚课）· 按周切换 · 教务原生配色 · 跨节课程合并成整块 · 自主学习标注 |
| **成绩** | 按学期折叠 · 加权绩点 / 加权平均 / 总学分 / 门数 |
| **设置** | 手动同步 · 接入状态 · QQ 群一键复制 · ☕ 赞赏与支持 · 版本与开源信息 |

关键设计（与 Android 端逐条对应）：

- **扫码登录**：ArkWeb `Web` 组件打开学校登录页，用户自己扫码；App 不接触密码，每 2 秒用 `WebCookieManager.fetchCookie` 捞 cookie，再拿 `zc=1` 真打一次接口探活；
- **课表按周拉**：`zc` 参数就是第几周，切周真的发一次请求；用请求序号防止「旧响应覆盖新响应」；
- **第一周周一反推**：按周课表带 `qsrq`，`qsrq - (xq-1)天 - (zc-1)周`，全班记录投票取众数；
- **考试日期推算**：整学期总表没有 `qsrq`，用「第一周周一 + zc + xq」还原；
- **网考号字段名会骗人**：`xkh` 优先、回退 `xid`；只在内存缓存；
- **会话过期静默降级**：教务 `code=-401`（HTTP 200）翻译成 `SessionExpiredException`；有缓存继续显示缓存，手动同步失败才回登录页；
- **分页必须翻完**：`*Datas` 接口默认只给 20 行，拒绝重复页 / 提前空页 / 半份结果。

## 结构

```text
harmony/
├─ AppScope/                      应用级配置与图标
├─ entry/src/main/
│  ├─ module.json5                声明 INTERNET 权限、EntryAbility
│  ├─ ets/
│  │  ├─ entryability/            UIAbility 入口（初始化 ArkWeb 内核）
│  │  ├─ pages/Index.ets          手写依赖容器（对应 Android App.kt）
│  │  ├─ core/                    领域模型与平台能力
│  │  │  ├─ LocalDate.ets         本地日期（epochDay 整数运算，避开 JS Date 时区坑）
│  │  │  ├─ Models.ets            Course / Grade / TimetableConfig / TOTAL_SECTIONS=14
│  │  │  ├─ Periods.ets           作息表（1~14 节，真实抓包校准）
│  │  │  ├─ Semester.ets          周次换算、周次串解析
│  │  │  ├─ TermCode.ets          学期代码按日期推算（教务没有查学期接口）
│  │  │  ├─ TimeUtils.ets         第一周周一反推、节次解析
│  │  │  ├─ EncryptedStore.ets    HUKS AES-256-GCM 加密落盘（≈ Android Keystore）
│  │  │  ├─ Session.ets           cookie 按域加密持久化（Domain/Path/Expires/Secure 全语义）
│  │  │  ├─ CacheStore.ets        课表/成绩/考试快照（加密）
│  │  │  ├─ TermStore.ets         学期代码（preferences）
│  │  │  ├─ Transport.ets         Req/Resp 传输抽象 + @kit.NetworkKit 实现（cookie/重定向/重试）
│  │  │  └─ Http.ets              教务门面：补浏览器头 + 登录态失效检测（≈ JwClient 拦截器）
│  │  ├─ data/                    教务接口
│  │  │  ├─ JwModels.ets          原始行模型 + 柔性字段转换（类型漂移全兜住）
│  │  │  ├─ JwJson.ets            三种响应形态（对象/裸数组/失败空串）容错解码
│  │  │  ├─ Mappers.ets           接口模型 → 领域模型（隐私字段进不了 UI）
│  │  │  ├─ Pagination.ets        分页收集（拒绝半份数据）
│  │  │  ├─ TermResolver.ets      学期代码：缓存 → 日期推算 → 兜底常量
│  │  │  ├─ RealJw.ets            真实实现：自举 + 按周拉取 + 分页成绩 + 网考号缓存
│  │  │  └─ JwService.ets         契约 + FakeJw 演示数据
│  │  ├─ viewmodel/
│  │  │  └─ AppViewModel.ets      @Observed 单一状态中枢（对应 AppViewModel.kt）
│  │  └─ ui/                      RootView(Tabs) / Login / Today / Schedule / Grades / Settings
│  └─ resources/                  base + dark 两套颜色（深浅色跟随系统）
└─ oh-package.json5 / build-profile.json5 / hvigorfile.ts
```

## 数据与隐私

与 Android 端同一套承诺：

- 账号密码**只在学校的登录页输入**，App 不接触、不保存、不上传；
- Cookie 与课表/成绩/考试缓存经 **HUKS 托管的 AES-256-GCM** 加密后落盘（密钥不出安全区，每次加密随机 nonce，store 名作为 AAD 隔离两类数据）；密文损坏或密钥丢失即丢弃，不回退明文；
- 本地缓存只含课表、成绩、考试，不含姓名和学号；网考号只存在内存；
- 所有请求直连 `zhjw.smu.edu.cn`，没有任何第三方服务器；
- 退出登录清空凭据与缓存，并清理 ArkWeb 的 cookie。

## 验证

### 离线逻辑测试（无需 DevEco Studio）

把 core / data / viewmodel 编译成 JS，用与 Android 端**同一份脱敏真实抓包 fixture** 跑断言（镜像 Android 的 JwJsonTest / TimeUtilsTest / PaginationTest 等）：

```bash
bash harmony/test/run.sh    # 依赖 node ≥ 18，首次自动 npx 拉取 typescript
```

当前 **35 项全部通过**：日期/周次/学期代码推算、课表三种响应形态、整学期总表与第 11 周回归、成绩分页与字段容错、网考号 `xkh`/`xid` 兼容、`code=-401` 信封容错、分页拒绝半份数据、cookie 域/路径/Secure/过期语义与合并 Set-Cookie 拆分、重定向 Location 解析，以及 AppViewModel 演示数据全链路。

### 真机 / 模拟器验证清单（DevEco Studio）

UI 与平台能力必须在 DevEco 模拟器或真机上过一遍：

1. 编译运行：entry 正常安装启动，四 Tab 切换，深浅色跟随系统；
2. 登录：Web 组件打开教务页 → 扫码 → 2 秒内探活成功自动进入主页（重点验证 `WebCookieManager.fetchCookie` + `saveCookieAsync`）；
3. 课表：切周请求、14 节网格、晚课色块、教务配色；
4. 加密存储：退出重进 App 缓存可解密（重点验证 HUKS `HUKS_TAG_AE_TAG` 解密路径）；
5. 断网/会话过期：显示缓存数据 + 设置页可手动同步。

## 已知差异 / 待办

- Hypium（ohosTest）正式单测工程待补；目前用上面的 Node 离线测试覆盖逻辑层；
- 应用图标为占位图（蓝底白圆），正式发布前替换；
- 深色模式跟随系统（base/dark 两套 color 资源），未做「动态取色」类定制。

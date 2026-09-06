# RandomNamePicker — 多功能随机抽取器

> 面向 AI 助手的项目速览文档。改代码前请通读本文件，尤其"改代码前必读"章节与文首"开发进度"。

纯 Java Swing 桌面程序（Windows，中文 UI），用于课堂/活动场景的**随机点名、随机数字、随机座位抽取**。
玩法为"滚停式"：点击开始后结果每 50ms 随机轮换，再点一次停止定格。所有数据本地加密落盘并自带多副本自愈，可常驻系统托盘与透明悬浮球。

- 语言/界面：Java 8+ 语法（Swing，无第三方依赖），源码 UTF-8
- 包结构：`com.randomnamepicker.*`（2026-09-03 由默认包迁移完成，Stage1 已验收通过）
- 主窗口标题 / 托盘提示：`多功能随机抽取器`
- 打包名：`RandomNamePicker`（jar → exe 由仓库外工具生成，见 tools/）

---

## 0. 开发进度（Stage 化改造，勿跳过）

本仓库按"任务书 → 执行 → 验收"方式分阶段改造；**插件化（外部 jar 动态注册新模式/菜单，Stage3）已完成**，jlink 免安装打包为后续规划。当前状态：

| 阶段 | 内容 | 状态 | 依据 |
|---|---|---|---|
| Stage 0（历史） | 默认包 23 文件/26 类原始版 | 完成 | `.stage1_originals/` 为迁移前完整备份（23 个 .java） |
| **Stage 1** | 默认包 → `com.randomnamepicker.{core,model,ui,mode,main,floating}`；模型类从 SchemeManager.java 拆出 | ✅ **完成并验收通过**（见下"Stage1 验收摘要"） | `src/com/randomnamepicker/**`（26 个 .java）；manifest / build.bat 已同步 |
| **Stage 2** | 模式策略重构：ModeHandler 新契约 + ModeHost + RollingPicker + 模式注册表，为插件注册新模式铺路 | ✅ **完成并验收通过**（见下"Stage2 验收摘要"） | `Stage2.txt` 全文 + `Stage2_交付报告.md`（29 个 .java） |
| **Stage 3** | 插件化：plugin 包（Plugin/PluginContext/PluginManager）+ 主窗"插件"菜单/下拉框并入/托盘/悬浮球接入 + 示例与 QA 插件 | ✅ **完成并验收通过**（见下"Stage3 验收摘要"） | `Stage3.txt` 全文 + `Stage3_交付报告.md`（32 个 .java） |
| Stage 4+ | jlink 免安装打包 / 插件运行中热插拔等 | 未开始 | 规划中 |

**Stage1 验收摘要（2026-09-03）**：全新编译零错误（36 个 class）；与原始文件逐行等价比对 22/23 完全一致（唯一差异：LogManager 首行注释在迁移中被删，已记录为豁免，行为零影响）；jar 可启动、日志行为与迁移前一致；无新增依赖、26 类无增减。`RandomNamePicker.jar` 为迁移版（Main-Class=`com.randomnamepicker.main.Main`）。

**Stage2 验收摘要（2026-09-04）**：模式策略重构完成——`ModeHandler` 契约收敛为 8 个抽象方法（删除死方法 getModeButton1/2），新增 `ModeHost` / `RollingPicker` / `ModeRegistry`，三个内置 Handler 与主窗/悬浮球共用 `canPick()+nextCandidate()`；主窗统一抽取入口、悬浮球双击滚动收敛到同一引擎与取样。全量 javac（Stage1 方式 A）零错误（40 个 class / 29 个 .java）；模拟插件经注册表注册后验证"出现于下拉框并可抽取"，验证后已删除。期间修复两处问题：① `NamePickerApp` 为满足 `ModeHost.getOwner()` 以 Frame 协变重写 `Window.getOwner()` 返回自身 → 所有模态子窗口白屏死锁（`getOwner()` 改返回 `java.awt.Window`，宿主不再重写，见 §6-13）；② `ConfigWindow` 把两行都 `add` 到 `BorderLayout.SOUTH`，后行覆盖前行致"姓名 + 添加/删除"不可见（改为纵向叠放复原，见 §6-14）。改动前副本在 `.stage2_originals/`，改动/验收/测试清单见 `Stage2_交付报告.md`。

**Stage3 验收摘要（2026-09-04）**：新增 `com.randomnamepicker.plugin` 包（`Plugin`/`PluginContext`/`PluginManager`，决策见 `Stage3.txt` §0 D0.1–D0.14）。加载机制 = 后台线程扫描运行目录 `plugins/`（不阻塞 UI）→ 每 jar 一个 parent-first URLClassLoader、作为**原子事务单元**：类条目加载失败 / 实例化失败 / onLoad 异常 / 提交冲突（撞内置或更早插件或同 jar 互撞）→ **整 jar 拒载并回滚**；注册动作先入 per-plugin 暂存区，onLoad 全部成功才统一提交并冲突预检，提交成功后 EDT 一次性刷新 UI——无半状态残留。主窗新增"插件"菜单（无插件也为空菜单）并含只读插件"名称 版本"，模式下拉框并入插件模式（无附加按钮时隐藏第 4/6 格按钮），托盘菜单整体重建追加插件项（当时为原生 AWT 菜单；2026 起改为 Swing 中文弹出菜单，见 §4.6），悬浮球右键菜单追加插件项（每次新建实例读取），退出时 `cleanupAndExit` 先 `PluginManager.shutdown()`（逐 onUnload + 关闭 ClassLoader）。全量 javac 零错误（32 个 .java）；示例插件 `examples/plugin-example/`（`示例插件模式`→固定串 `Hello from Plugin`）与三个坏样例（构造抛异常 / onLoad 抛异常 / 与内置冲突）均已编译产出 jar；自动化 UI 驱动两组场景 PASS：无插件（3 内置模式 + 空插件菜单 + 0 插件）与混合场景（仅好插件提交、三个坏 jar 整包拒载且无菜单/模式残留、插件模式可正常抽取、日志 PLUGIN_LOADED×1 / PLUGIN_LOAD_ERROR×3 / PLUGIN_UNLOADED）。`RandomNamePicker.jar` 已重建为 Stage3 版；`plugins/ExamplePlugin.jar` 已放置（删除该 jar 即回到无插件形态）。改动前副本在 `.stage3_originals/`，改动/验收/测试清单见 `Stage3_交付报告.md`。

**执行任务书文件**：`Stage1.txt` / `Stage2.txt` / `Stage3.txt` 是交给 AI 的分阶段改造 prompt（已修订为可行版本），执行结果需按其中的验收标准核对后再进入下一阶段。

**插件 UI 槽迭代（2026-09-04/05，承接 Stage3）**：统一 Zone 架构（`PluginContext.addUiAction(UiZone,…)` + PluginManager Zone 注册表 + `PluginUiSupport` 多渲染端）。一期在设置窗试点并验收；二期推广至全部业务窗口——主窗（3×2 按钮阵下方两列按钮区，其上有"插件"标识 + 分隔线）、配置名单、方案管理、数字设置、座位设置，外加一期设置窗，共六个窗口 Zone；另有 `MAIN_MENU/TRAY_MENU/FLOATING_BALL_MENU` 三个既有菜单 Zone（旧菜单 API 为 default 委托）。空 Zone 零渲染、严格 null-zone 拒载、无运行中热插拔。自动化 none/all 场景 PASS；`RandomNamePicker.jar` 已重建；示例 `plugins/ExamplePlugin.jar`（2.0）已并入 UI 槽演示功能："注意"按钮出现在六个窗口，点击弹窗"无运行中热插拔：增删插件/改按钮都要重启"（原 SettingsZoneDemoPlugin 已并入并移出 plugins）。详见《插件UI槽一期/二期任务书与交付报告》。

**宿主插件生态一期（插件体系二次开发一期，2026 实施）**：在不动加载内核的前提下补齐——① 打包禁令强制（jar 内 `com/randomnamepicker/` 前缀 .class 条目 → 整 jar 拒载 `BUNDLED_HOST_CLASS`）；② API 级别门控（`HostApi.PLUGIN_API_LEVEL`，基线 1 → 本期 **2**；插件可经 Manifest `Api-Level-Min` 声明，不符 → `VERSION_MISMATCH` 拒载）；③ 宿主事件订阅（九类：PLUGINS_CHANGED/SCHEME_CHANGED/MODE_CHANGED/PICK_STARTED/PICK_FINISHED/LOCK_CHANGED/BALL_SHOWN/BALL_HIDDEN/BALL_MOVED，EDT 防御派发、按 jar+实现类自动清理，含 Ctrl+L×10 后门锁事件，PasswordManager 零改动）；④ 加载结果可观测（`LoadOutcome`/`RejectReason` + 主窗"插件"菜单尾"插件状态…"入口）；⑤ 悬浮球窗口钩子（G1 `ModeHost` 几何只读查询、G2 BALL_* 事件、G4 `ModeHandler.getContextMenuItems()` 模式专属右键项）。新增 `plugin/HostApi.java`、`plugin/HostEvent.java`、`plugin/HostEventListener.java`；`ModeRegistry.register/unregister` 收紧为宿主内部；`ModeHost`/`ModeHandler` 仅新增只读/default 方法。源码 45 个 .java（主 jar 76 个 class）。`plugins/` 现含 ExamplePlugin.jar、CountUpTimerBall.jar 与新增 EventsDemoPlugin.jar；QA 新增 BadBundlePlugin（打包违规）与 BadVersionPlugin（级别不符）。另：**托盘菜单已由原生 AWT 菜单改为 Swing 中文弹出菜单**（托盘图标单击/双击唤出，规避系统菜单字体显示中文为方框的平台限制，详见《插件开发文档.md》§15.7）。详见《插件体系二次开发一期任务书/交付报告》。

`.sp-review/` 是某执行方下载的评审工具包缓存，与项目无关，可忽略或删除。

---

## 1. 目录结构

```
RdNmPk/
├── src/
│   └── com/randomnamepicker/      # 全部 Java 源码（34 个 .java，每文件一个顶层 public 类）
│       ├── core/                  # NameManager, SchemeManager, DataManager, EncryptionUtil,
│       │                          #   ConfigManager, PasswordManager, LogManager
│       ├── model/                 # Scheme, NumberRange, SeatConfig（Stage1 从 SchemeManager.java 拆出）
│       ├── ui/                    # NamePickerApp, ConfigWindow, SchemeManagerDialog, NumberPicker,
│       │                          #   SeatPicker, SettingsWindow, PasswordDialog, OldPasswordDialog,
│       │                          #   NewPasswordDialog, ChangePasswordDialog(死代码),
│       │                          #   RollingPicker(Stage2 新增), PluginUiSupport(UI 槽一期新增)
│       ├── mode/                  # ModeHandler(抽象), ModeHost(接口), ModeRegistry,
│       │                          #   NameListModeHandler, NumberModeHandler, SeatModeHandler
│       ├── main/                  # Main
│       ├── floating/              # FloatingBall
│       └── plugin/                # (Stage3 新增) Plugin, PluginContext, PluginManager, UiZone(UI 槽一期新增)
├── examples/
│   ├── plugin-example/            # 示例插件：ExamplePlugin.java + BUILD.txt + ExamplePlugin.jar（纯菜单/模式型）
│   ├── plugin-ui-zone-demo/       # UI 槽演示留档：SettingsZoneDemoPlugin.java（已并入 ExamplePlugin 2.0，仅留档）+ SingleZoneDemoPlugin（仅主窗，隔离样例）+ BUILD.txt
│   └── plugin-qa/                 # 坏样例源码与 jar：BadCtor / BadOnLoad / Conflict / BadNullZone（验收用）
├── plugins/                       # 插件运行目录（启动时自动创建；现放置 ExamplePlugin.jar——2.0 已并入六窗口“注意”演示；删除即回到无插件形态）
├── tools/
│   ├── build.bat                  # 递归收集 src\*.java → out\，jar cfm 打包（Stage1 已适配 argfile 编译）
│   ├── Creator.bat                # 手工把 RandomNamePicker.exe 写入注册表 Run 键（开机自启）
│   └── LogClearer.bat             # 实际功能是"删除开机自启注册表项"，与文件名不符
├── .stage1_originals/             # Stage1 迁移前的原始 23 个 .java（默认包），勿删，作回归对照
├── .stage2_originals/             # Stage2 改动前 7 个文件（mode×4 + NamePickerApp + FloatingBall + ConfigWindow）的副本，勿删
├── .stage3_originals/             # Stage3 改动前 3 个文件（NamePickerApp / Main / FloatingBall）的副本，勿删
├── .pluginui_originals/           # 插件 UI 槽一期改动前 3 个文件（PluginContext / PluginManager / SettingsWindow）副本，勿删
├── .pluginui2_originals/          # 插件 UI 槽二期改动前 6 个文件（UiZone / NamePickerApp / ConfigWindow / SchemeManagerDialog / NumberPicker / SeatPicker）副本，勿删
├── Stage1.txt / Stage2.txt / Stage3.txt   # 分阶段改造任务书（给 AI 的 prompt）
├── Stage2_交付报告.md             # Stage2 交付报告：改动清单 / 决策记录 / 验收对照 / 手动测试清单 / 修复记录
├── Stage3_交付报告.md             # Stage3 交付报告：D0.x 决策 / 插件机制说明 / 改动清单 / 验证与人工验收清单
├── 插件UI槽一期任务书.md          # 插件 UI 槽一期任务书（设置窗试点）＋ 一期交付报告（同目录）
├── 插件UI槽一期交付报告.md        # 插件 UI 槽一期交付报告（含 2026-09-05 变更记录）
├── 插件UI槽二期任务书.md          # 插件 UI 槽二期任务书（六窗口推广，已定稿）
├── 插件UI槽二期交付报告.md        # 插件 UI 槽二期交付报告（六窗口推广：文件/机制/验证/人工验收清单）
├── V4.1.2代码备份.zip             # 更早版本备份，与运行无关
├── manifest.txt                   # jar 清单：Main-Class: com.randomnamepicker.main.Main（被 gitignore）
├── RandomNamePicker.jar           # 构建产物（含 Stage3 插件化与 UI 槽一期/二期，被 gitignore）；Stage1 旧包备份为 RandomNamePicker.jar.stage1bak
├── .sp-review/                    # 执行方下载的评审工具缓存（与项目无关）
└── .gitignore                     # 忽略 data/ log/ out/ *.jar *.exe jre 等运行产物
```

> Stage1 迁移规则（后续维护须知）：跨包使用的类一律 `public`（现有 34 个顶层类型全部 public）；新增类也须按上述包归属放置并显式 public。

## 2. 运行时目录与数据文件（程序启动后自动创建于工作目录，与包迁移无关）

```
data/
├── config.properties            # 全局配置 + 密码哈希（键见 §4.7）
├── schemes/
│   ├── index.txt                # 方案索引，每行 "方案名,类型"，类型 ∈ name_list|number|seat
│   ├── <方案>_names.txt         # 名单数据（加密）
│   ├── <方案>_number.txt        # 数字范围，明文内容 "min,max"（加密）
│   └── <方案>_seat.txt          # 座位布局：首行 "rows,cols"，其后每行 "x,y" 一个已选座位（加密）
└── backup/                      # 与 schemes/ 一一对应的加密备份副本
log/
├── Modifylog.txt                # 操作留痕日志（见 §4.8）
└── sys_cache_*.dat / usr_config_*.tmp / app_data_*.log   # 伪装扩展名的第三份加密备份（见 §4.4）
```

> 数据加密文件格式（DataManager 统一读写）：
> 第1行 `#ENCRYPTED_DATA`，随后 base64(AES-GCM 密文)，`#HASH` 行，随后 base64(SHA-256(明文))。

---

## 3. 类职责速查（按包分组，均为 `com.randomnamepicker.*`）

### main 包 — 入口与宿主
| 类 | 职责 |
|---|---|
| `Main` | main 入口。系统外观初始化、密码初始化、开机自启检查；创建系统托盘（16px 程序绘制"抽"字图标）；托盘菜单 = **Swing 中文弹出菜单**（`buildSwingTrayMenu`：显示/隐藏主窗、开关悬浮球、退出，含插件项时为"内置项＋插件项＋退出"；托盘图标**单击/双击**在屏幕右下角唤出，双击 350ms 去抖；插件加载后 EDT 整体重建 `refreshTrayMenu`）——原生 AWT 托盘菜单因系统字体不含中文字形会显示方框，已弃用；`registerAutoStart/unregisterAutoStart/checkAutoStartStatus` 用 `reg` 命令操作 `HKCU\...\CurrentVersion\Run` 键值 `RandomNamePicker`（指向 `<user.dir>\RandomNamePicker.exe`，无 exe 时报错）；`cleanupAndExit` 全局退出（Stage3：先 `PluginManager.shutdown()` 卸载插件并关闭 ClassLoader）。启动时序（Stage3，EDT）：`new NamePickerApp()` → `PluginManager.init(app)` → 显示主窗 → **后台线程** `PluginManager.loadAllAsync()`（不阻塞 UI）→ 提交成功后 EDT 刷新主窗菜单/下拉框并重建托盘菜单。 |

### ui 包 — 主窗体与全部对话框
| 类 | 职责 |
|---|---|
| `NamePickerApp` | 主窗体 `JFrame`，**implements `ModeHost`**。顶部：方案下拉框 + 模式下拉框（内置=ModeRegistry 显示名，插件提交后并入 PluginManager 的插件模式）+ **"插件"菜单（JMenuBar，Stage3）** + 方案管理 + 设置；中央大字号结果 Label；下方 3×2 按钮阵（开始抽取 / 配置名单 / 悬浮球 / 模式按钮1 / 空 / 模式按钮2）。负责：加载方案列表（内置不可删的"默认方案"type=name_list）、恢复上次方案(lastScheme)、关闭行为（minimizeToTray=true 隐藏，否则退出）、切换方案/模式时解析当前 `ModeHandler`（内置走 ModeRegistry、插件走 PluginManager）并重绑两个模式按钮（第 4/6 格；**插件按钮文本为 null/空则隐藏对应按钮**）、统一抽取入口 `startPicking`（`canPick()` 非 null → 按模式弹现文案并复位、数据缺失点击仍按旧语义补记"抽取结果"日志；否则 `RollingPicker` 50ms 无限滚动；**插件 canPick/nextCandidate 调用点防御包裹**）、`stopPicking`（定格后按 `方案-模式显示名=结果` 记日志，操作码"抽取结果"）、打开各窗口、悬浮球开关（每次重建实例）。插件集合变化后（EDT）执行 `refreshPluginMenu()`（功能项＋分隔线＋只读"名称 版本"）与 `refreshModeCombo()`（重建并尽量保持当前选中）。 |
| `RollingPicker` | （Stage2 新增，ui 包）通用滚停引擎：构造 `Supplier<String>` 候选提供器 + `Consumer<String>` 显示回调 + 滚动间隔（默认 50ms）+ `Integer maxTicks`（null=无限滚动直到 stop()，主窗用；20=滚满自停，悬浮球用）；`start()/stop()/getLastValue()`（stop 后取定格值供主窗记日志）。引擎不含截断与空数据提示——截断/格式化由调用方在显示回调里做（悬浮球 5 字截断、主窗不截断）。 |
| `ConfigWindow` | 名单配置对话框：方案下拉 + JTable 名单 + 姓名输入框，支持**添加/删除（多选）**/txt 导入/导出/保存；底部两行：上行"姓名+添加+删除"，下行"导入/导出/保存"（2026-09-04 修复：两行曾都挂 SOUTH 相互覆盖致"添加/删除"不可见，见 §6-14）。所有变更操作受锁保护。 |
| `SchemeManagerDialog` | 方案管理：创建方案（名称 + 原始类型码下拉 name_list/number/seat）、删除选中方案（连带删数据文件）。受锁保护。 |
| `NumberPicker` | 数字抽取设置：min/max 输入、载入当前范围、内部滚停抽取预览、保存设置（写方案）、应用到方案。 |
| `SeatPicker` | 座位布局设置：行/列输入 →"更新布局"生成网格 JButton，点击座位黄底选中/取消（**点击即自动保存**），滚停抽取预览、保存、应用。 |
| `SettingsWindow` | 设置对话框：开机自启（勾选即调 `Main.register/unregisterAutoStart`）、关闭最小化到托盘、悬浮球半径(30–100)与透明度(50–255)滑杆实时保存、导出日志文件、**锁定/解锁配置**（解锁=PasswordDialog 验密成功→unlock）、**修改密码**（OldPasswordDialog 验原密 → NewPasswordDialog 输两次新密 → `changePasswordDirectly`）。 |
| `PasswordDialog` | 解锁用密码验证框（Ctrl+L×10 后门在此复刻）。 |
| `OldPasswordDialog` | 改密第一步：验证原密码。 |
| `NewPasswordDialog` | 改密第二步：新密码两遍一致（≥4 位）→ changePasswordDirectly。 |
| `ChangePasswordDialog` | **死代码**：单框式改密（原密+新密+确认），全工程无引用；其中调用的 `PasswordManager.changePassword` 也随之无实际调用者。 |
| `PluginUiSupport`（UI 槽一期新增） | 宿主渲染帮助：`createZonePanel(UiZone)` 生成带"插件"标题的按钮行（点击防御执行）；空 Zone 返回 null（各窗口"空则不渲染"）。 |

### mode 包 — 模式策略层（Stage2 重构后：宿主接口 + 注册表 + 抽象契约 + 内置实现）
| 类 | 职责 |
|---|---|
| `ModeHost`（接口，新增） | 模式宿主接口，替代子类对 `NamePickerApp` 的直接依赖：`getCurrentScheme() / getNameManager() / getSchemeManager() / getDataManager() / getLogManager() / getOwner()`。`NamePickerApp` 实现之；`getOwner()` 返回 `java.awt.Window`——**任何 JFrame/Window 宿主都不得以 Frame 协变重写 `Window.getOwner()` 返回自身**（会破坏 AWT 模态 owner 链，见 §6-13）。 |
| `ModeHandler`（抽象） | 模式策略契约，构造参数为 `ModeHost`。8 个抽象方法：`getModeId()`（稳定标识，内置=方案类型 name_list/number/seat，插件可自定义）、`getDisplayName()`（下拉框文本，内置与 UI/日志共用）、`canPick()`（null=可抽取；非 null=不可抽取原因：主窗用返回值作弹窗文案并复位，悬浮球按 modeId 映射短文本"损坏/无范围/无座位"）、`nextCandidate()`（返回 `Supplier<String>` 随机候选串，主窗与悬浮球共用取样）、`getButton1Text/getButton2Text/handleButton1Click/handleButton2Click`（附加按钮能力）。Stage2 已删除死方法 `getModeButton1/2`。 |
| `ModeRegistry`（新增） | 最小模式注册表：有序 `modeId → (displayName, schemeType, 工厂)`（LinkedHashMap）。下拉框内置项数据源 = 注册表显示名列表；内置 Handler 解析、方案类型→内置模式联动均走注册表，代码内不再 switch 中文字符串。**插件模式不进本注册表**，由 `plugin.PluginManager` 单独维护（Stage3），下拉框在提交后并入插件模式。 |
| `NameListModeHandler` | 内置名字列表模式（displayName=名字列表模式）。`canPick`：名单空→"名单已损坏，请重新导入"；`nextCandidate` 从每次抽取开始时快照的名单随机取一行。附加按钮：导入名单 / 导出名单（JFileChooser + UTF-8 txt，导入为**追加合并**），受锁保护。 |
| `NumberModeHandler` | 内置数字模式（displayName=数字模式）。`canPick`：未设范围→"请先设置数字范围！"；取样公式原样 `nextInt(max-min+1)+min`。附加按钮：设置数字范围（弹 `NumberPicker`）/ 保存数字范围（**只读已存范围并提示，真正保存发生在 NumberPicker 内**）。 |
| `SeatModeHandler` | 内置座位模式（displayName=座位模式）。`canPick`：无配置或未选座位→"请先设置座位并选择座位！"；座位候选主窗格式 `(x, y)`（悬浮球显示回调还原为无空格 `(x,y)`）。附加按钮：设置座位布局（弹 `SeatPicker`）/ 保存座位设置（同上，仅提示已存内容）。 |

### core 包 — 业务 / 数据层
| 类 | 职责 |
|---|---|
| `NameManager` | 名单逻辑：按方案加载（损坏则删损坏文件并弹窗）、整存、增/删/清空单条、txt 导入（UTF-8、跳过空行、追加合并、**不去重**）、导出。 |
| `SchemeManager` | 方案增删 + `data/schemes/index.txt` 维护；把数字范围/座位配置转成字符串走 DataManager。模型类已拆至 model 包（Stage1）。 |
| `DataManager` | 持久化核心：名单/数字/座位三类数据的 加密写、校验读、三副本备份与自愈（详见 §4.4）。文件名中的非法字符 `[\\/:*?"<>|]` 一律替换为 `_`。内部私有类 `FileData`(encryptedContent,hash)。 |
| `EncryptionUtil` | 静态密码学工具：RSA2048 密钥对；`hashPassword`=SHA-256→Base64；`encryptData/decryptData`=AES-256-GCM（密钥由 `PBKDF2WithHmacSHA256`：口令=方案盐、盐=`"R@nd0mN@m3P!ck3r_S@lt_2026"`、10 万次迭代、128bit；12 字节随机 IV 前置，整体 Base64）；`calculateHash`=SHA-256→Base64。 |
| `ConfigManager` | 读写 `data/config.properties`（合并式写回，保留未知键）；提供 autoStart / 悬浮球半径 / 透明度 / 最小化到托盘 / lastScheme 的 get/set；首次运行建默认配置并写入默认密码哈希。 |
| `PasswordManager` | 锁状态机（`isUnlocked`，初始锁定）。静态读取 config 中混淆键 `x7f9a2b1c4e8d3f6` 下的密码哈希：`verifyPassword`（SHA-256 比对，成功置 unlock）、`changePassword`（验旧→换新，需 ≥4 字符，**仅被死代码调用**）、`changePasswordDirectly`（免验旧直接换新，被 NewPasswordDialog 使用）、`handleCtrlL`（**免密后门**：2 秒窗口内累计 10 次 Ctrl+L 直接解锁）、lock/unlock。 |
| `LogManager` | 追加写 `log/Modifylog.txt`：`[yyyy-MM-dd HH:mm:ss]【详情】【操作码】`；readLog/clearLog。（Stage1 迁移时首行注释丢失，已记录豁免，功能不受影响） |

### model 包 — 数据模型（Stage1 从 SchemeManager.java 独立拆出）
| 类 | 字段/说明 |
|---|---|
| `Scheme` | name、type（name_list/number/seat）；toString=name。UI 常把它放 JComboBox 显示。 |
| `NumberRange` | min、max。 |
| `SeatConfig` | rows、cols、selectedSeats:List<Point>。 |

### floating 包 — 悬浮球
| 类 | 职责 |
|---|---|
| `FloatingBall` | 无边框置顶透明圆形 `JWindow`（继承），半径/透明度取自 ConfigManager；左键拖拽；**双击 = 快速抽取**（Stage2 后与主窗共用当前模式的 `ModeHandler`：`canPick()` 非 null 时按 modeId 在球上显示现状短文本"损坏/无范围/无座位"（未知插件回退显示 canPick 返回值），否则 `RollingPicker(maxTicks=20)` 滚 20 次自动定格；显示回调内做悬浮球历史口径格式化：名字 >5 字截断为前 5 字+"..."、座位还原为无空格 `(x,y)`；**插件模式的 canPick/nextCandidate 防御包裹**）；右键菜单在"悬浮球设置"与"关闭"之间**追加插件项**（每次新建悬浮球实例读取 PluginManager 当前已提交插件，Stage3）；100ms 保顶 Timer 强制 toFront。**抽取逻辑已不再与主窗平行重复**（Stage2 收敛）。 |

### plugin 包 — 插件机制（Stage3 新增）
| 类 | 职责 |
|---|---|
| `Plugin`（接口） | 插件入口：`getName()/getVersion()/onLoad(PluginContext)/onUnload()`。 |
| `UiZone`（枚举，UI 槽一期新增） | UI 动作区域：菜单类 MAIN_MENU / TRAY_MENU / FLOATING_BALL_MENU；窗口按钮区 SETTINGS_WINDOW（一期）与 MAIN_WINDOW / CONFIG_WINDOW / SCHEME_MANAGER_WINDOW / NUMBER_PICKER / SEAT_PICKER（二期）。插件经 `addUiAction(UiZone,…)` 把按钮挂到对应窗口。 |
| `PluginContext`（接口） | 注册能力（**暂存语义**：onLoad 期间只写本插件暂存区，提交后才可见；菜单标题不判重）：`registerModeHandler(ModeHandler)`、`addMainMenuAction/addTrayMenuAction/addFloatingBallMenuAction(title, ActionListener)`；服务访问：`getNameManager/getSchemeManager/getDataManager/getLogManager/getModeHost`（ModeHost 接口形态，D0.1）与 `log`。不暴露 PasswordManager/ConfigManager/FloatingBall/NamePickerApp；`getDataManager()` 返回主窗自持实例（D0.8）。 |
| `PluginManager` | 进程内单例加载管理器：后台扫描运行目录 `plugins/*.jar`（文件名排序）→ 每 jar 一个 parent-first URLClassLoader、为**原子事务单元**（D0.9/D0.10：类条目加载失败/实例化失败/onLoad 异常/提交冲突 → 整 jar 拒载并回滚、已 onLoad 候选补 onUnload）；注册进 per-plugin 暂存区（D0.11），全部成功后在**提交阶段**做冲突预检（撞内置/撞更早提交插件/同 jar 互撞），通过才整体生效；提交成功后 EDT 一次性刷新 UI；查询（插件信息/模式/三类菜单项）均只读快照；`shutdown()` 逐个 onUnload + 关闭 ClassLoader；`runMenuActionSafely` 防御执行菜单动作。 |

---

## 4. 核心机制细节

### 4.1 抽取流程（Stage2 重构后：统一入口 + RollingPicker 引擎）
开始 → 校验方案/模式（方案空→"请选择一个方案！"）→ 取当前 `ModeHandler` → `canPick()`：
- 非 null（数据缺失/损坏）→ 主窗**弹窗并复位**：名字模式为"错误/ERROR"样式、数字/座位为"提示/WARNING"样式，文案即 canPick() 返回值（"名单已损坏，请重新导入"/"请先设置数字范围！"/"请先设置座位并选择座位！"）。**严格保真（D1）**：与迁移前一致，此类点击仍补记一行"抽取结果"日志（值=当时标签文本）。
- null（可抽取）→ `isPicking=true`、按钮变"停止" → 建 `RollingPicker(候选=handler.nextCandidate(), 回调=写标签, 50ms, maxTicks=null)` 无限滚动（`javax.swing.Timer`，每 50ms 一次，随机源在各自 Handler 内）→ 再点按钮即 `stopPicking`：停引擎、复位按钮、把定格值按 `方案-模式显示名=结果` 记日志（操作码"抽取结果"）。

取样公式/规则与迁移前逐字一致：数字 `nextInt(max-min+1)+min`；名字/座位从当前方案数据随机取。悬浮球用同一 Handler 取样（同源同公式），固定 20 次自停。

### 4.2 三种模式
- 名字列表模式：从当前方案加密名单中随机选一行显示。
- 数字模式：在当前方案数字范围内随机整数。
- 座位模式：从当前方案"已选座位"集合中随机取一个 `(行,列)` 显示。

### 4.3 方案（Scheme）机制
- 每方案有类型 name_list / number / seat；新建方案时由用户在原始类型码下拉中选择。
- UI 中始终额外硬编码一个 **"默认方案"**（name_list），不来自 index.txt、不可删除。
- 主窗切换方案时按方案类型**自动联动**模式下拉框，并记录 lastScheme（下次启动恢复）。
- `SchemeManager.removeScheme` 会删 `data/schemes/` 下对应三个数据文件（用**未清洗**的原始方案名拼路径，与 DataManager 内部清洗后的落盘名在含非法字符时可能对不上——见 §6）。

### 4.4 加密存储与三副本自愈（DataManager）
- 写：`saveNamesFile/saveNumberRange/saveSeatConfig` 各自加密 + SHA-256 → 写 `data/schemes/<clean>_*.txt`；同时写 `data/backup/<clean>_<clean>_*.txt`；再写第三份到 `log/` 下**伪装名**文件：names→`sys_cache_<hash>.dat`、number→`usr_config_<hash>.tmp`、seat→`app_data_<hash>.log`（hash=`schemeName.hashCode() & 0x7FFFFFFF`）。
- 读：解出明文后重算 SHA-256 与文件内 hash 比对；不一致或解密失败 → `recoverAndSyncData`：按 **log > backup > data** 优先级取可用（校验通过）的一份为 master，把三份文件全部同步成 master 内容；全不可用返回 null（名单场景 NameManager 会删三份损坏文件并弹"名单已损坏，请重新导入"）。
- 方案盐：`"R@nd0mN@m3P!ck3r_" + 方案名 + "_2026"`。

### 4.5 锁定 / 密码 / 后门
- 全局锁概念：`PasswordManager.isLocked()`。**处于锁定态时**，以下操作全部被拦（弹"当前为锁定模式，请先解锁"）：名单导入/导出（主窗按钮与 ConfigWindow 内按钮）、ConfigWindow 增删改存、方案创建/删除。
- 密码：默认密码为硬编码字符串 `#include<bits/stdc++.h>usingnamespacestd;intmain(){return 0;}`，其 SHA-256 Base64 存于 config 键 `x7f9a2b1c4e8d3f6`。首次运行由 ConfigManager 写入；若该键缺失，verifyPassword 会调用 initializeDefaultPassword 回填。
- 后门：任一密码对话框中 2 秒内连按 **Ctrl+L 十次** → 直接解锁（PasswordManager 与各对话框各有一份计数逻辑）。
- 解锁入口：设置 →"锁定/解锁配置" → 验密成功后 `PasswordManager.unlock()`。

### 4.6 系统托盘与开机自启
- 托盘图标 = 16px 程序绘制"抽"字（见 `Main`）；**单击/双击图标**在屏幕右下角唤出 **Swing 中文弹出菜单**：显示/隐藏主窗、显示/隐藏悬浮球、插件项、退出（二次确认后 `cleanupAndExit`）；双击去抖（350ms）只弹一次；菜单显示中再点图标即收起。托盘菜单用 Swing 渲染（原生 AWT 托盘菜单用系统 Segoe UI 字体不含中文，会显示方框，故弃用）。
- 主窗关闭行为：`minimizeToTray=true`（默认）→ 仅隐藏窗口；false → 直接退出整个程序。
- 开机自启 = 写/删/查注册表 `HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Run` 下值 `RandomNamePicker`（Main 内通过 `reg add/delete/query` 子进程实现；依赖工作目录存在 `RandomNamePicker.exe`）。设置窗勾选即调用，tools 脚本为手工等价物。注意：插件化/jlink 化后 exe 路径布局会变，届时需同步适配此处定位逻辑。

### 4.7 配置键（data/config.properties）
`autoStart`(bool) / `floatingBallRadius`(30–100,默认50) / `floatingBallOpacity`(50–255,默认200) / `minimizeToTray`(默认 true) / `lastScheme` / `x7f9a2b1c4e8d3f6`(密码SHA-256)。`ConfigManager.saveConfig` 采用"读旧文件→覆写已知键→整存"策略，未知键会保留。

### 4.8 日志
所有用户可见操作都经 `LogManager.log(详情, 操作码)` 落盘 `log/Modifylog.txt`（追加、UTF-8），含大量 DEBUG 级恢复过程日志。设置窗可"导出日志文件"复制该文件。插件相关操作码：`PLUGIN_LOADED` / `PLUGIN_UNLOADED` / `PLUGIN_LOAD_ERROR`（拒载与各类插件异常）、`PLUGIN_MODE_ERROR` 类（校验/候选异常日志）。

### 4.9 插件机制（Stage3）
- 运行目录：`plugins/`（`user.dir` 下，与 jar/exe 同级；不存在则启动自动创建），只扫描 `*.jar`（文件名排序）。
- 加载：后台线程（不阻塞 UI）→ 每 jar 新建 **parent-first URLClassLoader** → 遍历 .class 条目（跳过 `module-info.class` 与 `META-INF/versions/`）→ `Class.forName` 收集 `Plugin` 实现类（非抽象、public、无参构造）→ 实例化并 `onLoad(ctx)`。`ctx.getModeHost()` 让插件以 ModeHost 构造自己的 ModeHandler（D0.1）。
- 事务与失败隔离（以 jar 为单元）：类条目加载失败 / 实例化失败 / onLoad 异常 / 提交冲突（modeId 或 displayName 撞内置或更早提交插件或同 jar 候选互撞）→ **整 jar 原子拒载**并记 `PLUGIN_LOAD_ERROR`；已 onLoad 的候选补 `onUnload`；不中断其它 jar。坏插件不影响主程序与好插件。
- 暂存-提交：onLoad 期间的 `registerModeHandler/addXxxMenuAction` 只写本插件 pending，不触 UI/注册表；jar 内全部候选 onLoad 成功后才统一提交并冲突预检；失败路径丢弃 pending，**无半状态残留**。菜单标题不判重。
- 展示：主窗"插件"菜单 = 插件功能项 + 分隔线 + 只读"名称 版本"；模式下拉框 = 内置（前）+ 插件（提交序，后）；无附加按钮（getButton*Text 为 null/空）时主窗第 4/6 格按钮隐藏；插件模式无 schemeType，切换方案不会自动拨动它。托盘菜单（Swing，单击/双击图标唤出，见 §4.6）= 内置项 + 插件项 + 退出（提交后整体重建）；悬浮球每次新建实例读取当前插件项追加到右键菜单。
- 防御（D0.5）：onLoad/onUnload/菜单动作/`canPick()`/`nextCandidate()` 调用点均 try/catch（含 LinkageError 边界），失败只记日志并回退占位显示。
- 生命周期：不做运行中热插拔——增删 `plugins/` 下 jar 需重启；退出（托盘"退出"或主窗关闭且非最小化到托盘）经 `Main.cleanupAndExit` → `PluginManager.shutdown()`（逐个 onUnload → 关闭全部 URLClassLoader → 清空注册）。
- 插件开发：示例与坏样例见 `examples/plugin-example/`（含 `BUILD.txt`）与 `examples/plugin-qa/`；插件 jar 只允许包含自身类与资源，**禁止打包 `com/randomnamepicker/**`**（API 由宿主 parent-first 提供，不做强制检查，文档约束）。

---

## 5. 构建与运行

```bat
:: 编译全部源码并打包（tools\build.bat 已适配包结构：递归收集 src\*.java → out\ → jar cfm）
:: 注意：Windows 下 javac 不支持 ** 递归通配，脚本用的是 argfile（dir /s /b + @sources.txt）
tools\build.bat

:: 运行（迁移版主类）
java -jar RandomNamePicker.jar
:: 或：java -cp out com.randomnamepicker.main.Main
```

- 需 JDK（javac/jar）可用；本机 PATH 无 java 时可用 `C:\Program Files\Java\jdk-22\bin\`（构建脚本需在 PATH 或先 set）。
- 仓库根 `manifest.txt` 必须为 `Main-Class: com.randomnamepicker.main.Main`（Stage1 已更新，被 gitignore，勿误删）。
- 首次运行会在**当前工作目录**创建 data/ log/（程序以工作目录为数据根）；默认密码即 §4.5 的硬编码串。
- 插件：运行目录 `plugins/`（见 §4.9）；示例插件的编译打包与安装/卸载见 `examples/plugin-example/BUILD.txt`。
- 之后再经外部打包工具（launch4j 等）转 RandomNamePicker.exe；jlink 免安装打包与插件运行中热插拔属 Stage 4+ 规划。

---

## 6. 改代码前必读（已确认的死代码 / 不一致 / 坑）

1. **死代码类**：`ui.ChangePasswordDialog` 无任何引用（真实改密链路是 SettingsWindow → OldPasswordDialog → NewPasswordDialog）；连带 `PasswordManager.changePassword(旧,新)` 无有效调用者。
2. ~~**冗余抽象（Stage2 已按任务书删除）**~~：旧 `mode.ModeHandler.getModeButton1/2` 及三个子类各自 `new` 的 `button1/button2` 死对象从未被主窗使用（NamePickerApp 只调 `getButton*Text()` 和 `handleButton*Click()` 驱动自己的按钮）。Stage2 已删除该死方法/死按钮字段；如需回归对照 `.stage2_originals/`。
3. **文件命名不一致**：DataManager 保存时对方案名清洗非法字符为 `_`，而 `SchemeManager.deleteSchemeFiles` 用原始方案名拼删除路径——方案名含 `\/:*?"<>|` 时删不掉对应文件。
4. **改密两步链的"免验旧"特性**：NewPasswordDialog 直接调 `changePasswordDirectly`，只要旧密码对话框放行（含 Ctrl+L 后门）即换密成功。
5. **伪备份文件**：`log/` 下的 `.dat/.tmp/.log` 伪装名文件是**加密数据备份**，不是日志；误删会削弱自愈能力（恢复优先级 log > backup > data）。
6. **工具脚本名不符**：`LogClearer.bat` 实为删除开机自启注册表项；`build.bat` 成功提示写的是 `RandomNamePicker_new.jar`，实际产物为 `RandomNamePicker.jar`。
7. **模式按钮2语义弱**：Number/Seat 模式的"保存…"按钮只读当前已存配置并弹提示，真正保存须在 NumberPicker/SeatPicker 对话框内；SeatPicker 点击座位会**立即静默保存**。
8. 名单导入 = 追加合并不去重；抽取结果不归档（原"抽取日志"功能已被注释废除）。
9. 安全强度刻意偏低（默认密码硬编码、无盐 SHA-256、存在免密后门、config 混淆键），符合"防学生乱改"定位，勿擅自"加固"改坏行为——除非明确要求。
10. 所有窗口/对话框直接 new，无依赖注入；状态靠静态类（ConfigManager/PasswordManager/LogManager）共享，多实例测试时注意静态残留（如解锁态、Ctrl+L 计数）。
11. **迁移期须知（Stage3 后 32 个顶层类型）**：跨包使用的类一律 `public`（现有 32 个顶层类型全部 public，含 Stage2 新增 `ModeHost`/`ModeRegistry`/`RollingPicker` 与 Stage3 新增 `Plugin`/`PluginContext`/`PluginManager`）；改动后新增类务必带 package 与 import；`tools\build.bat` 编译依赖目录递归收集，不要在 src 根放回默认包 .java。
12. **已知记录在案的豁免**：Stage1 迁移删除了 `core.LogManager` 首行注释（`//To recording Motify NOT the programme running log`），行为零影响；如需严格恢复可自行补回。
13. **Window 宿主不得改 `getOwner()` 语义（Stage2 修复记录，2026-09-04）**：`NamePickerApp` 曾为实现 `ModeHost.getOwner()`（声明 Frame）而协变重写 `java.awt.Window#getOwner()` 并返回自身，AWT 弹模态对话框时沿 owner 链建立模态关系遇到自引用 → 所有模态子窗口（配置名单/数字设置/座位设置/设置等）白屏死锁、无法关闭，只能杀进程。修复：`ModeHost.getOwner()` 返回 `java.awt.Window`，宿主直接继承 AWT 实现、不再重写；构造 NumberPicker/SeatPicker 处显式 `(Frame) host.getOwner()`（当前唯一宿主即主窗 JFrame）。**教训：任何 JFrame/Window 的 getOwner 都被 AWT 模态机制使用，勿重写或改变其返回语义。**
14. **BorderLayout 同区域重复 `add` = 后者覆盖前者（历史坑，2026-09-04 已修）**：`ConfigWindow` 曾把"姓名+添加+删除"输入行与"导入/导出/保存"按钮行都 `add` 到 `SOUTH`，导致"添加名字/删除名字"整行不可见（组件仍在组件树中、代码可调用，但用户看不到点不到）。修复：两行纵向叠放（BoxLayout.Y_AXIS）后整体入 SOUTH。新增对话框布局时注意勿把多个面板压到同一 BorderLayout 区域。
15. **插件事务与隔离（Stage3）**：加载单元＝单个 jar——类加载失败/实例化失败/onLoad 异常/提交冲突任一发生即**整 jar 拒载并回滚**（onLoad 成功者补 onUnload），拒载只记日志不影响其它 jar；onLoad 中 `registerModeHandler/addXxxMenuAction` 只进暂存区，提交通过才可见（无半状态）；菜单标题不判重；不做运行中热插拔（增删 `plugins/` 下 jar 需重启）；悬浮球每次新建实例读取当前插件。**插件 jar 禁止打包 `com/randomnamepicker/**`**（API 由宿主 parent-first 提供；不做强制检查，属文档约束）。
16. **插件模式与内置的边界（Stage3）**：插件模式不经 `ModeRegistry`（仅内置三项），由 `PluginManager` 提交后并入下拉框；冲突预检同时比对内置（ModeRegistry）与已提交插件；插件模式无 schemeType，`onSchemeChanged` 不会自动拨动它；卸载插件无运行中路径，仅在退出时 onUnload。

---

## 7. 一句话技术画像（供 prompt 复用）

> Java Swing 单机工具；`com.randomnamepicker` 包 **34 类**无框架（Stage1 默认包迁移 ✅ / Stage2 模式策略重构 ✅ / Stage3 插件化 ✅，均已验收）；抽取走"模式策略"架构：`ModeHandler` 抽象契约（modeId/displayName/canPick/nextCandidate/附加按钮）× `ModeHost` 宿主接口 × `ModeRegistry` 有序注册表（内置三项）× `RollingPicker` 通用滚停引擎，主窗（无限滚动）与悬浮球（20 次自停）共用同一 Handler 取样；插件化（Stage3）：`plugin.Plugin/PluginContext/PluginManager` 以"后台加载 + jar 原子事务 + 暂存-提交 + 冲突预检 + EDT 刷新"把第三方 jar 的新模式与主窗/托盘/悬浮球菜单接入运行目录 `plugins/`（示例 `examples/plugin-example/`）；插件 UI 槽（一/二期，2026-09-05 ✅）：`addUiAction(UiZone,…)` 把"注意"类按钮挂到主窗/设置/配置名单/方案管理/数字/座位等六窗口（`PluginUiSupport` 渲染、空 Zone 零界面），示例 `examples/plugin-ui-zone-demo/`；方案化数据（名单/数字范围/座位）经 AES-GCM(PBKDF2 派生、每方案盐)+SHA-256 三副本加密持久化并自愈；全局"锁"由硬编码默认密码 SHA-256 校验，另有 Ctrl+L×10 免密后门；系统托盘 + 透明悬浮球双形态常驻；注册表 Run 键控制开机自启；全操作写 log/Modifylog.txt。分阶段改造中：**Stage1 ✅ / Stage2 ✅ / Stage3 ✅ / 插件 UI 槽 一二期 ✅（2026-09-05）**，后续规划 jlink 免安装打包与插件运行中热插拔（Stage 4+）。

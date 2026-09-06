# 插件 UI 槽一期任务书 —— 设置窗插件按钮区（统一 Zone 架构试点）

> 面向 AI 助手的插件体系迭代任务书（在 Stage3 插件化之上的一次能力扩展）。
> 目标宿主：RandomNamePicker（`com.randomnamepicker`，Stage1–3 已完成并验收）。
> **本任务书阶段只出任务与验收口径，不实施**；实施前须经技术评审通过。
> 本版已吸收 2026-09-04 技术评审结论（P1–P7 全部定稿）。

---

## §0 已确认决策（用户拍板 + 技术评审定稿，实施时不得更改）

- D1 **统一 Zone 注册表 + 多渲染端**：插件面向"区域(Zone)"注册动作，宿主各窗口各自渲染；后续支持新窗口 = 新增枚举值与一个渲染调用点，**不再为每个窗口新增一套按钮 API**。
- D2 **一期试点仅开放 `SETTINGS_WINDOW`（设置窗按钮区）**；验证无误后二期再推广（`MAIN_WINDOW`/`CONFIG_WINDOW`/…），一期**不得**新增未开放的 Zone 与渲染。
- D3 **计时子球不列入本期**（仅作未来目标）；本期唯一目标 = 提供"插件在设置窗口增加按钮"的通用方法。
- D4 **向后兼容**：`PluginContext` 现有 `addMainMenuAction / addTrayMenuAction / addFloatingBallMenuAction` 语义、签名、行为不变（改为内部委托同一 Zone 注册表，用 Java 8 `default` 方法承接；属二进制兼容，既有插件源码与 .class 零改动）。
- D5 按钮与菜单同规则：标题**不判重**、动作**防御执行**、注册走现有**暂存-提交**语义（onLoad 失败/整 jar 拒载时按钮随 pending 一并丢弃，无残留）。
- D6 空 Zone（无插件注册）渲染为零界面元素 → 无插件/旧插件时窗口布局与 Stage2/3 逐字一致。
- D7 不改动任何现有插件（`examples/plugin-example`、`examples/plugin-qa`）与任何已验收的宿主行为（抽取/子窗口/锁定/托盘/悬浮球等）。
- D8（评审 P1）**严格失败语义**：`addUiAction` 收到 `zone == null` 视为插件缺陷 → 抛 `IllegalArgumentException`，由 PluginManager 包装为加载失败 → **整 jar 拒载**并记 `PLUGIN_LOAD_ERROR`（与 Stage3 拒载语义一致，缺陷可见）；`title` 为 null 或 trim 后为空、或 `action` 为 null → **忽略**（与既有菜单注册一致）。
- D9（评审 P2）**内部数据统一为 `EnumMap<UiZone, List<MenuAction>>`**（PerPluginContext 内同步保护）；顺序语义显式定义：**查询结果 = 插件提交序 × 插件内注册序**（现有三菜单的顺序与内容必须逐字不变，用 Stage3 自动化 none/all 场景回归验证）。
- D10（评审 P3）**动作防御执行直接复用现有 `runMenuActionSafely`，不改名、不新增别名**；其 javadoc 注明"菜单与 UI 按钮动作共用此防御执行"。
- D11（评审 P4）设置窗插件块置于滚动面板**列表最末**（安全设置面板之后）；不改既有分区相邻关系；验收文档注明"需滚动到底可见"。
- D12（评审 P5）演示插件按钮动作 = **纯日志**（`ctx.log("…", "DEMO_BUTTON_CLICKED")`，无弹窗），自动化以日志断言；人工验收以日志为反馈。
- D13（评审 P6）设置窗插件块面板标题 = **"插件"**（`TitledBorder("插件")`）。
- D14 插件块按钮仅由 title 生成 JButton，不承载插件自绘组件；一期不做按钮分组/图标/排序。

## §1 目标与范围

1. 目标：为"插件往设置窗口加按钮"提供**统一、通用、可推广**的机制（一期以设置窗为试点验证链路）。
2. 范围（仅以下五类交付）：
   - 新增 `plugin/UiZone.java`（Zone 枚举）；
   - 扩展 `plugin/PluginContext.java`（通用 `addUiAction(Zone,…)` + 三个旧方法 default 委托）；
   - 重构 `plugin/PluginManager.java`（内部按 Zone 组织注册/查询；对外查询与既有方法行为不变）；
   - 新增 `ui/PluginUiSupport.java`（宿主渲染帮助：按 Zone 生成插件动作面板，空则 null）；
   - 修改 `ui/SettingsWindow.java`（构造时渲染 `SETTINGS_WINDOW` Zone 面板）。
   - 另附：验证用演示插件 `examples/plugin-ui-zone-demo/`，并产出本任务书要求的说明文档。

## §2 现状核对与技术假定（动手前先读源码，勿按名臆测）

- `src/com/randomnamepicker/plugin/PluginContext.java`：现有 3 个菜单注册方法 + 服务访问 + `registerModeHandler`；约束"不暴露内部类、标题不判重、实现线程安全、暂存语义"。
- `plugin/PluginManager.java`：进程内单例；`PerPluginContext` 维护 per-plugin pending；`LoadedPlugin` 存已提交列表与只读快照查询；加载=后台线程、jar 原子事务、提交时冲突预检（仅针对 ModeHandler 的 modeId/displayName），菜单/动作不判重；`runMenuActionSafely` 防御执行；UI 刷新经 `addUIListener` 在 EDT 触发；`shutdown()` 全量 onUnload+关 loader。
- `ui/SettingsWindow.java`：模态 JDialog；布局 = BorderLayout；CENTER 为 `JScrollPane(mainPanel)`，`mainPanel` 为 `BoxLayout(Y_AXIS)`，自顶向下：启动与关闭设置 / 悬浮球外观 / 日志管理 / 安全设置（各带 `TitledBorder`），面板间以 `Box.createRigidArea(0,10)` 分隔；无"插件"相关 UI。
- **技术假定（写明，不必额外实现）**：
  - `SettingsWindow` 构造必在 EDT（入口均为 ActionListener 触发）；`PluginUiSupport.createZonePanel` 不自行做线程切换，调用方保证 EDT。
  - `PluginContext` 唯一实现方是宿主的 `PerPluginContext`（插件只调用、从不实现该接口），故新增抽象方法 `addUiAction` 无第三方实现兼容问题。
  - 设置窗每次打开都是新实例 → 按钮天然取"最新已提交"列表，**无需**给 settings 区挂 `addUIListener` 实时刷新（与常驻主窗不同）。
  - 锁边界：宿主设置窗既有功能各自做 `PasswordManager.isLocked` 判断；插件按钮动作宿主不代做锁检查，插件也无法访问 PasswordManager（"受控 API"边界如此，文档说明即可）。

## §3 实施任务项

**T1 新增 `plugin/UiZone.java`**
- `public enum UiZone { MAIN_MENU, TRAY_MENU, FLOATING_BALL_MENU, SETTINGS_WINDOW }`
- 一期只用 `SETTINGS_WINDOW`；前三个对应既有能力归位。**不得**加入 MAIN_WINDOW / CONFIG_WINDOW 等未开放 Zone。

**T2 扩展 `plugin/PluginContext.java`**
- 新增：`void addUiAction(UiZone zone, String title, ActionListener action);`
- 既有三方法改为 `default` 委托：
  `default void addMainMenuAction(...) { addUiAction(UiZone.MAIN_MENU, title, action); }`（其余同理）。
- 语义（D8）：`zone == null` → 抛 `IllegalArgumentException`（由 PluginManager 包装为加载失败、整 jar 拒载）；`title` 为 null/trim 后为空或 `action` 为 null → 忽略；标题不判重；暂存语义。
- Javadoc 注明插件用法示例：`ctx.addUiAction(UiZone.SETTINGS_WINDOW, "标题", e -> {...});`

**T3 重构 `plugin/PluginManager.java`（内部 Zone 化，行为不变）**
- `PerPluginContext`：pending 统一为 `EnumMap<UiZone, List<MenuAction>>`（同步保护），实现 `addUiAction` 注册与 D8 校验；提交时随既有菜单动作一并提交（动作不参与冲突预检、不判重）。
- `LoadedPlugin`：对应 Zone 化后的动作列表（含 settings 区）。
- 查询：新增 `List<MenuAction> getUiActions(UiZone zone)`（只读快照，顺序 = 插件提交序 × 插件内注册序）；`getMainMenuActions/getTrayMenuActions/getFloatingBallMenuActions` 改为对 zone 的委托（内容与顺序逐字不变，现有调用方/测试零影响）。
- 动作防御执行：**复用现有 `runMenuActionSafely`**（D10），按钮点击与菜单动作走同一入口；可在其 javadoc 补充"UI 按钮动作共用"。
- 拒载/回滚/清理：settings 区 pending 随 EnumMap 一并清理；已 onLoad 候选补 onUnload 路径不变；不得遗漏新列表。

**T4 新增 `ui/PluginUiSupport.java`（宿主帮助类）**
- `public static JPanel createZonePanel(UiZone zone)`：
  - 从 `PluginManager.getInstance().getUiActions(zone)` 取动作；空 → 返回 `null`；
  - 非空 → 返回 `TitledBorder("插件")`（D13）的面板：`FlowLayout(FlowLayout.LEFT)` 放 JButton（逐按钮文字=title、点击经 `runMenuActionSafely` 防御执行）；
  - 纯数据驱动、无状态；仅宿主 EDT 调用（调用方负责 EDT，本方法不做线程切换）。

**T5 修改 `ui/SettingsWindow.java`（唯一渲染点）**
- `setupLayout()` 构建 `mainPanel` 时在**列表最末**（安全设置面板之后，D11）追加：
  `JPanel zone = PluginUiSupport.createZonePanel(UiZone.SETTINGS_WINDOW);`
  非 null → `mainPanel.add(Box.createRigidArea(0,10)); mainPanel.add(zone);`
- 空 → 不添加任何组件（窗口与现在逐字一致）。
- 不改任何既有控件/行为；插件块宽度随 Box Y 自然撑满滚动区、内容左对齐。

**T6 验证演示插件 `examples/plugin-ui-zone-demo/`**
- `SettingsZoneDemoPlugin`：实现 `Plugin`，`onLoad` 里 `ctx.addUiAction(UiZone.SETTINGS_WINDOW, "演示按钮", e -> ctx.log("演示按钮被点击", "DEMO_BUTTON_CLICKED"))`（D12，无弹窗）；按 `examples/plugin-example/BUILD.txt` 方式编译打包（只含自身类，不打包 `com/randomnamepicker/**`），附 BUILD 说明。
- 评审通过后可在 `plugins/` 放置演示 jar 供人工查看（文档注明删除即恢复）。

**T7 编译与回归**
- 沿用方式 A：`dir /s /b src\*.java > sources.txt` + `javac -encoding UTF-8 -d out @sources.txt`，全量零错误；`jar cfm RandomNamePicker.jar manifest.txt -C out .`。
- 演示插件以 out 为 API 类路径编译打包。

## §4 不改动（保真红线）

`examples/plugin-example`、`examples/plugin-qa` 既有插件与 jar 零改动；主窗/托盘/悬浮球既有渲染与行为不改（除 PluginManager 内部 Zone 化后行为等价的委托）；抽取/数据/加密/锁/托盘/悬浮球等宿主行为不改；`PluginContext` 既有三方法签名不改（default 化不改调用方）；本期不实现计时子球、不开其它窗口 Zone、不做按钮分组/图标/排序/插件自绘面板、不为设置窗挂实时刷新监听（新实例天然取最新）。

## §5 输出要求

1. 新增/修改文件清单与各文件说明（对应 T1–T5）。
2. `UiZone`、`PluginContext`（完整）、`PluginManager` 改动片段（或全文件）、`PluginUiSupport`、`SettingsWindow` 改动片段。
3. `examples/plugin-ui-zone-demo/` 插件源码与 BUILD 说明。
4. 设计说明一段：为何不改每窗口一套 API、为何一期仅设置窗、二期如何推广（对应 D1/D2/§7）。
5. 手动测试清单（对应 §6 逐条可勾选）。

## §6 验收标准

- 编译零错误；`RandomNamePicker.jar` 重建成功；无第三方依赖新增。
- **无插件/旧插件回归**：plugins/ 为空（或仅放既有示例插件）时打开设置窗 → 无"插件"块、界面与 Stage2/3 一致；主窗/托盘/悬浮球/抽取等行为回归通过（复用 Stage3 清单与自动化 none/all 场景，验证 Zone 化后三菜单顺序与内容逐字不变）。
- **演示插件生效**：放入 `SettingsZoneDemoPlugin.jar` 重启 → 打开设置窗，滚动到底部："安全设置"下方出现"插件"块与"演示按钮"；点击按钮 → `log/Modifylog.txt` 出现 `DEMO_BUTTON_CLICKED`；无弹窗。
- **Zone 隔离**：演示插件未注册主窗/托盘/悬浮球动作 → 那些界面无新增元素。
- **严格失败（D8）**：QA 样例——插件 `addUiAction(null, "x", e->{})` → 整 jar 拒载并记 `PLUGIN_LOAD_ERROR`，其它插件不受影响、设置窗无残留。
- **暂存/拒载兼容**：插件 onLoad 抛异常 → 整 jar 拒载、设置窗无残留（复用 plugin-qa 手法）。
- 退出：程序退出无异常、无残留；插件 onUnload 正常（`PLUGIN_UNLOADED`）。

## §7 二期展望（不在本期实施，仅记录）
- 推广到 `MAIN_WINDOW`（主窗按钮区）与其它按需窗口：各加一个 `UiZone` 枚举值与一处 `PluginUiSupport.createZonePanel(...)` 渲染调用即可，插件 API 无需再改；
- 计时子球等"自绘 UI + 窗口级交互"需求待独立方案（生命周期/几何订阅类钩子）另行评审。

## §8 交付物文件建议命名
- 任务书：`插件UI槽一期任务书.md`（本文件）
- 实施后交付报告：`插件UI槽一期交付报告.md`（待评审通过后实施时产出）

# 插件 UI 槽二期任务书 —— 向全部宿主窗口推广（已定稿，未实施）

> 面向 AI 助手的插件体系迭代任务书（一期已验收：设置窗插件按钮区 / 统一 Zone 架构试点）。
> 目标宿主：RandomNamePicker（`com.randomnamepicker`，Stage1–3 + 插件 UI 槽一期均已完成并验收）。
> 本版已吸收 2026-09-05 技术评审与用户拍板结论；**只出任务与验收口径，不动代码**；待批准后实施。

---

## §0 已确认决策（2026-09-05 拍板，实施时不得更改）

- D1 **一期机制全量沿用**：`addUiAction(UiZone,…)`、EnumMap Zone 注册表、只读快照、防御执行、空 Zone 零渲染、严格 null-zone 整 jar 拒载、标题不判重、暂存-提交、旧菜单 default 委托（二进制兼容）、无运行中热插拔。
- D2 **二期接入全部六个窗口按钮区（Zone）**：
  - `MAIN_WINDOW`（主窗 NamePickerApp，A 类·常驻，提交后刷新重建）
  - `SETTINGS_WINDOW`（设置窗，一期已实现，纳入统一验收）
  - `CONFIG_WINDOW`（配置名单 ConfigWindow，B 类·每次新建渲染）
  - `SCHEME_MANAGER_WINDOW`（方案管理 SchemeManagerDialog，B 类）
  - `NUMBER_PICKER`（数字设置 NumberPicker，B 类）
  - `SEAT_PICKER`（座位设置 SeatPicker，B 类）
- D3 **主窗插件按钮区位置（自定义口径）**：放在主窗口**下方**——紧邻现有 3×2 按钮阵之下新增按钮区；按钮**排成两列、向下依次扩展**（与宿主 3×2 同列宽风格）；仅在存在已注册按钮时可见，空则完全无痕。
- D4 主窗按钮区**不展示**插件"名称 版本"（按钮区保持简洁；信息仍见于主窗"插件"菜单）。
- D5 **示例插件改造**：`SettingsZoneDemoPlugin` 单个插件向**全部六个窗口**各注册一个"注意"按钮，点击弹出提示框并记日志；弹窗文本改为 **"无运行中热插拔：增删插件/改按钮都要重启"**（不再含"注意！"前缀）。
- D6 另加 `SingleZoneDemoPlugin`（仅注册 `MAIN_WINDOW` 一个按钮）作为"插件只需其中某个窗口"的隔离样例；沿用 `ExamplePlugin`（纯菜单/模式型，零窗口按钮）作"完全不需要窗口按钮"样例；QA 沿用 `BadNullZonePlugin` 等坏样例。
- D7 **密码类对话框不接入**（Password/OldPassword/NewPassword/ChangePassword：安全边界 + 临时录入性质）；托盘/悬浮球已有各自菜单 Zone，不开按钮区。
- D8 交付配套：编译/自动化验证后重建主 jar、更新 `plugins/` 演示 jar、产出《插件UI槽二期交付报告.md》并同步 `README.txt`（类计数与 plugin 区窗口 Zone 说明）。

---

## §1 目标与设计不变式

1. 目标：把一期验证过的"Zone 注册 + 各窗口独立渲染"推广到全部宿主业务窗口；插件按 Zone 粒度自选需要哪些窗口按钮（全部/部分/零均可），宿主不做裁剪判断。
2. 渲染点两类：
   - **A 类（常驻）**：主窗 → 提交后经 `addUIListener`/`onPluginSetChanged` 统一重建（菜单、模式下拉框、主窗按钮区一并刷新，EDT）；
   - **B 类（每次新建模态窗）**：设置/配置名单/方案管理/数字设置/座位设置 → 构造时调 `PluginUiSupport.createZonePanel(zone)` 渲染一次（取最新已提交），无需刷新监听。
3. 新增窗口 = 新增 `UiZone` 枚举值 + 该窗口一处渲染调用；插件 API 不新增方法。
4. "插件只需其中几个或不需要"由插件自选注册实现：注册几个 Zone 就出现在几个窗口；一个不注册则只按既有能力（菜单/模式）呈现。宿主零裁剪逻辑。

---

## §2 接入清单与渲染落位（按源码现状，实施前先读源码核对）

| 窗口 | Zone | 渲染类 | 落位（建议） |
|---|---|---|---|
| 主窗 `NamePickerApp` | `MAIN_WINDOW` | A | SOUTH 现为 3×2 按钮阵：改外包一个垂直容器，按钮阵下新增"插件按钮区"——两列、向下扩展（GridLayout(0,2) 或等效），仅非空时可见 |
| 设置窗 `SettingsWindow` | `SETTINGS_WINDOW` | B | 一期已实现（滚动面板列表最末），保持 |
| 配置名单 `ConfigWindow` | `CONFIG_WINDOW` | B | SOUTH 纵向面板追加一行"插件"面板（该行现已有两行，作第三行），不改窗口尺寸 |
| 方案管理 `SchemeManagerDialog` | `SCHEME_MANAGER_WINDOW` | B | 对话框底部合适容器追加 |
| 数字设置 `NumberPicker` | `NUMBER_PICKER` | B | 400×250 偏小：在有插件面板时追加并微调（Flow 换行或仅面板时 pack），实施时按源码布局定夺 |
| 座位设置 `SeatPicker` | `SEAT_PICKER` | B | 主网格下方追加一行，必要时仅面板时微调 |

> 各 B 类窗口均"面板为空 → 完全不添加任何组件"，保证无插件零差异。

---

## §3 实施任务分解（批准后按 T1–T9 执行）

- **T1** `plugin/UiZone.java`：新增 `MAIN_WINDOW / CONFIG_WINDOW / SCHEME_MANAGER_WINDOW / NUMBER_PICKER / SEAT_PICKER`（只增不删，javadoc 注明窗口）。
- **T2** 主窗 `NamePickerApp`（A 类）：
  - SOUTH 外包垂直容器：按钮阵在上、"插件按钮区"在下（两列、向下扩展；空则隐藏）；
  - 提交后 `onPluginSetChanged()` 中追加重建主窗按钮区（读 `PluginUiSupport`/直接渲染均可，逐按钮挂防御执行）；
  - 无插件/未注册时该区不存在可见元素。
- **T3** `ConfigWindow`（B 类）：`createZonePanel(UiZone.CONFIG_WINDOW)` 非空 → 追加为 SOUTH 纵向面板第三行。
- **T4** `SchemeManagerDialog`（B 类）：同法渲染 `SCHEME_MANAGER_WINDOW`。
- **T5** `NumberPicker`（B 类）：渲染 `NUMBER_PICKER`（含小窗尺寸处理，见 §2）。
- **T6** `SeatPicker`（B 类）：渲染 `SEAT_PICKER`。
- **T7** 演示与 QA：
  - 改造 `examples/plugin-ui-zone-demo/SettingsZoneDemoPlugin.java`：向全部六个窗口注册"注意"按钮，点击 = 弹窗（文本 D5）+ 记日志 `DEMO_BUTTON_CLICKED`；重建并更新 `plugins/SettingsZoneDemoPlugin.jar`；
  - 新增 `examples/plugin-ui-zone-demo/SingleZoneDemoPlugin.java`（仅 `MAIN_WINDOW` 一个按钮，动作=日志/简单弹窗）+ BUILD 说明 + jar；
  - 沿用 `ExamplePlugin`（零窗口按钮）、`BadNullZonePlugin`（null-zone 拒载）等 QA。
- **T8** 编译与自动化验证（扩展一期 driver 模式，独立工作目录）：
  - 场景 none：六个窗口均无"插件"面板/按钮区；既有行为与现版一致；
  - 场景 all（放 ExamplePlugin + 改造后 SettingsZoneDemoPlugin + SingleZoneDemoPlugin + BadNullZone）：
    主窗按钮区出现"注意"（SettingsZoneDemo）与单窗样例按钮（SingleZoneDemo，仅主窗）；
    打开设置/配置名单/方案管理/数字设置/座位设置：SettingsZoneDemo 的"注意"出现在每处（SingleZoneDemo 不出现在任何窗口）；
    点击各窗口"注意"按钮 → 弹窗文案 = D5 文本、日志记 `DEMO_BUTTON_CLICKED`；
    下拉框/插件菜单回归（ExamplePlugin 菜单/模式正常）；BadNullZone 整 jar 拒载、全部窗口无残留；shutdown `PLUGIN_UNLOADED` 正常。
- **T9** 交付：产出《插件UI槽二期交付报告.md》；同步 `README.txt`（34→39 个 .java 等、plugin 区窗口 Zone 说明）；重建 `RandomNamePicker.jar`。

---

## §4 不改动（保真红线）

`PluginContext` 不再新增方法（只增 UiZone 枚举）；既有方法与行为（菜单/模式/托盘/悬浮球/设置窗一期行为/抽取/锁/加密等）不改；既有插件（ExamplePlugin、plugin-qa、旧版 SettingsZoneDemoPlugin 的用户 jar）零改动可继续运行（二进制兼容）；密码类窗口不加；托盘/悬浮球按钮区不加；多槽位/分组/图标/排序/插件自绘面板不做；计时子球不列入；无插件时所有窗口零差异。

---

## §5 风险与对策

| 风险 | 对策 |
|---|---|
| 主窗常驻刷新遗漏（提交后按钮区不出现） | 复用 `onPluginSetChanged` 统一重建；自动化 all 场景覆盖主窗按钮区断言 |
| B 类小窗（Number 400×250 / Seat）空间不足 | 有面板时按源码布局放最合适处并 pack() 微调；Flow 可换行；逐窗自动化+人工目检 |
| 多窗口同名按钮 | 各 Zone 独立渲染无跨窗冲突；标题不判重沿用 |
| 枚举增长影响旧包 | 只增不删；旧包不引用新常量即安全 |
| “插件只要部分窗口”验收口径 | SingleZoneDemoPlugin（仅主窗）+ ExamplePlugin（零窗口）双样例 + all 场景断言其余窗口无其按钮 |

---

## §6 验收标准

1. 编译零错误；主 jar 重建；无第三方依赖新增。
2. 无插件回归：六个窗口均无"插件"面板/按钮区，各窗口行为与现版一致。
3. 旧插件回归：仅 ExamplePlugin → 菜单/模式如常；仅旧版 SettingsZoneDemoPlugin 行为按一期保持（无其它窗口按钮）。
4. 改造后 SettingsZoneDemoPlugin：主窗下方两列按钮区出现"注意"；设置/配置名单/方案管理/数字/座位窗口各出现"注意"；点击任一处 → 弹窗文本 = **"无运行中热插拔：增删插件/改按钮都要重启"**，日志 `DEMO_BUTTON_CLICKED`。
5. SingleZoneDemoPlugin：仅主窗按钮区出现其按钮，其余五个窗口无。
6. BadNullZonePlugin（null-zone）：整 jar 拒载；全部窗口无残留；好插件不受影响。
7. 退出正常，`PLUGIN_UNLOADED` 完整；README/交付报告已同步。

---

## §7 明确不做与后续展望

- 不做：宿主按插件裁剪窗口清单；密码类窗口接入；托盘/悬浮球按钮区；主窗按钮区名称/版本展示；多槽位/分组/图标/排序/自绘面板；计时子球。
- 后续展望：计时子球等"自绘 UI + 窗口级交互"（生命周期/几何订阅钩子）另行评审。

---

## §8 交付物命名建议
- 本任务书：`插件UI槽二期任务书.md`
- 实施后交付报告：`插件UI槽二期交付报告.md`

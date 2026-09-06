# 插件 UI 槽一期交付报告 —— 设置窗插件按钮区（统一 Zone 架构试点）

> 依据《插件UI槽一期任务书.md》（D1–D14）与 2026-09-04 技术评审定稿口径实施。
> 改动前副本：`.pluginui_originals/`（PluginContext / PluginManager / SettingsWindow）。

## 1. 决策与范围（本版落实）
- D1 统一 Zone 注册表 + 多渲染端；D2 一期仅开放 `SETTINGS_WINDOW`；D3 计时子球不列入；
- D4 旧三菜单方法改 `default` 委托（二进制兼容）；D5 按钮与菜单同规则（不判重/防御执行/暂存-提交）；
- D6 空 Zone 零渲染；D7 不改既有插件与宿主行为；
- D8 严格失败（`addUiAction(null,…)` → 整 jar 拒载）；D9 内部 EnumMap、顺序=提交序×注册序；
- D10 复用 `runMenuActionSafely`；D11 面板置于设置窗列表最末；D12 演示动作为纯日志；D13 面板标题“插件”；D14 不做分组/图标/排序/自绘面板。

## 2. 改动/新增文件清单
| 文件 | 动作 | 说明 |
|---|---|---|
| `plugin/UiZone.java` | 新增 | 枚举：MAIN_MENU / TRAY_MENU / FLOATING_BALL_MENU / SETTINGS_WINDOW |
| `plugin/PluginContext.java` | 修改 | 新增 `addUiAction(UiZone,String,ActionListener)`；旧三菜单方法改 `default` 委托 addUiAction；javadoc 注明严格失败语义与用法示例 |
| `plugin/PluginManager.java` | 修改 | `PerPluginContext` pending 改 `EnumMap<UiZone,List<MenuAction>>` 并实现 addUiAction（D8 校验：zone=null 抛 IllegalArgumentException → 整 jar 拒载；空 title/action 忽略）；`LoadedPlugin` 动作改按 Zone 的只读 Map；提交快照同步；新增 `getUiActions(UiZone)`；三个旧 getter 改为委托（顺序=插件提交序×插件内注册序）；`runMenuActionSafely` 注明“菜单与 UI 按钮动作共用” |
| `ui/PluginUiSupport.java` | 新增 | 宿主帮助类：`createZonePanel(UiZone)` 空返回 null、非空返回 `TitledBorder("插件")` 按钮行（点击经 runMenuActionSafely 防御执行）；仅供宿主 EDT 调用 |
| `ui/SettingsWindow.java` | 修改 | `setupLayout()` 在滚动 mainPanel 列表最末（安全设置之后）追加 `SETTINGS_WINDOW` 插件面板；空则不渲染 |
| `examples/plugin-ui-zone-demo/` | 新增 | `SettingsZoneDemoPlugin.java`（向 SETTINGS_WINDOW 注册“演示按钮”，点击记 `DEMO_BUTTON_CLICKED` 纯日志）+ `BUILD.txt` + `SettingsZoneDemoPlugin.jar` |
| `examples/plugin-qa/BadNullZonePlugin.java` | 新增 | QA 坏样例：`addUiAction(null,…)` → 整 jar 拒载（含 jar） |

类计数：主工程 32 → **34** 个 .java（新增 UiZone、PluginUiSupport）。

## 3. 兼容性结论（已验证）
- **二进制向后兼容**：验证场景使用的 `ExamplePlugin.jar` 是 Zone 化**之前**编译的旧包（仅含旧三菜单方法调用），运行时经 default 委托正常提交（主窗/托盘/悬浮球菜单各 1 项，内容顺序不变）——证明既有插件无需重编译。
- 无插件/旧插件场景：设置窗无“插件”面板，界面与之前逐字一致（自动化断言通过）。
- `PluginContext` 唯一实现方为宿主 `PerPluginContext`，无第三方实现兼容问题。

## 4. 验证记录（自动化，2026-09-04/05）
全量 `javac`（方式 A）零错误 → `jar cfm RandomNamePicker.jar …`（jar 内含 UiZone/PluginUiSupport）。自动化 UI 驱动两组工作目录场景：

1. **无插件（none）**：所有 Zone 查询为空、主窗模式下拉框 3 项、设置窗内无“插件”标题面板、无“演示按钮” → PASS。
2. **混合（all）**：plugins/ 同时放 `BadNullZonePlugin.jar`（旧）`ExamplePlugin.jar`（旧二进制）`SettingsZoneDemoPlugin.jar` →
   - 已提交仅 [ExamplePlugin, SettingsZoneDemo]（BadNullZone 因 `addUiAction(null,…)` 整 jar 拒载并记 `PLUGIN_LOAD_ERROR`，日志含 jar 名）；
   - 主窗/托盘/悬浮球三菜单各 1 项（委托后内容不变）；模式下拉框 4 项；主窗插件菜单含“关于插件”；
   - `getUiActions(SETTINGS_WINDOW)` 仅 1 项：演示按钮(SettingsZoneDemo)；
   - 打开设置窗：出现标题“插件”面板与“演示按钮”；点击 → 日志出现 `DEMO_BUTTON_CLICKED`；
   - shutdown → `PLUGIN_UNLOADED` 记录正常（≥2）→ PASS。

## 5. 人工验收清单（GUI）
- [ ] 无插件（plugins/ 为空）打开 设置 → 无“插件”面板，界面与原版一致。
- [ ] 放入 `SettingsZoneDemoPlugin.jar` 重启 → 打开 设置，滚动到底部：安全设置下方出现“插件”面板与“演示按钮”。
- [ ] 点击“演示按钮” → `log/Modifylog.txt` 新增 `DEMO_BUTTON_CLICKED`（无弹窗）。
- [ ] 主窗/托盘/悬浮球菜单与之前一致（无新增元素，Zone 隔离正确）；放入既有 `ExamplePlugin.jar` 时其菜单/模式正常（默认委托兼容）。
- [ ] QA：放入 `BadNullZonePlugin.jar` 重启 → 整 jar 拒载、日志含其 jar 名与 `PLUGIN_LOAD_ERROR`、其它插件不受影响、设置窗无残留（验收后移除该 jar）。
- [ ] 退出程序正常，`PLUGIN_UNLOADED` 记录。

## 6. 明确不做（本期）与二期
- 不做：其它窗口 Zone（MAIN_WINDOW/CONFIG_WINDOW 等）、按钮分组/图标/排序、插件自绘面板、计时子球、设置窗实时刷新监听（新实例天然取最新）。
- 二期：新增 Zone = 加枚举值 + 对应窗口一处 `PluginUiSupport.createZonePanel(...)` 渲染调用即可，插件 API 无需再改。

## 7. 产物与备注
- `RandomNamePicker.jar` 已重建（含一期类）；`plugins/` 现有 `ExamplePlugin.jar`（Stage3 示例）与新增 `SettingsZoneDemoPlugin.jar`（一期演示，删除即恢复无插件形态）；另见仓库内 `CountUpTimerBall.jar`（非本任务产物，未触碰）。
- 演示/QA 源码与打包说明见 `examples/plugin-ui-zone-demo/`、`examples/plugin-qa/`。

## 8. 变更记录（2026-09-05）
- `examples/plugin-ui-zone-demo/SettingsZoneDemoPlugin` 按钮点击行为按需求改为：**弹出提示框**
  `注意！程序无运行中热插拔：增删插件/改按钮都要重启`，同时写日志 `DEMO_BUTTON_CLICKED`；
  演示 jar 已重建并更新 `plugins/SettingsZoneDemoPlugin.jar`，`BUILD.txt` 已同步。
- 验证：自动化驱动触发按钮动作 → 自动检测到含上述文案的提示框并关闭，日志含 `DEMO_BUTTON_CLICKED` → PASS。
- 按钮文本按需求由“演示按钮”改为 **“注意”**（历史条目中的“演示按钮”即指该按钮，点击动作同上）。
- （注：§4 自动化验证记录为本变更前的“仅日志”行为；变更后的动作语义以本节省为准。）

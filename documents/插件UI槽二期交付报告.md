# 插件 UI 槽二期交付报告 —— 推广至全部宿主窗口

> 依据《插件UI槽二期任务书.md》（D1–D8，2026-09-05 定稿）实施；代码按批准后 T1–T10 完成。
> 改动前副本：`.pluginui2_originals/`（UiZone / NamePickerApp / ConfigWindow / SchemeManagerDialog / NumberPicker / SeatPicker）。

## 1. 决策落实（D1–D8）
- D2 六窗口按钮区：主窗 `MAIN_WINDOW`、设置窗 `SETTINGS_WINDOW`（一期保留）、配置名单 `CONFIG_WINDOW`、方案管理 `SCHEME_MANAGER_WINDOW`、数字设置 `NUMBER_PICKER`、座位设置 `SEAT_PICKER`；
- D3 主窗按钮区位于 3×2 按钮阵**下方**、按钮**两列向下扩展**、空则隐藏（A 类，提交后 `onPluginSetChanged` 刷新重建）；
- D4 主窗按钮区不展示名称/版本；D5 示例插件“注意”按钮出现在全部六窗口，点击弹窗文本=“无运行中热插拔：增删插件/改按钮都要重启”+记 `DEMO_BUTTON_CLICKED`；
- D6 隔离样例 `SingleZoneDemoPlugin`（仅主窗）与 `ExamplePlugin`（零窗口按钮）双基线；QA `BadNullZonePlugin`（null-zone 拒载）复验全窗口无残留；
- D7 密码类对话框/托盘/悬浮球按钮区不接入；D8 编译/自动化/重建 jar/更新 plugins/同步 README。

## 2. 改动/新增文件清单
| 文件 | 动作 | 说明 |
|---|---|---|
| `plugin/UiZone.java` | 修改 | 新增 MAIN_WINDOW / CONFIG_WINDOW / SCHEME_MANAGER_WINDOW / NUMBER_PICKER / SEAT_PICKER（javadoc 注明窗口；只增不删） |
| `ui/NamePickerApp.java` | 修改 | SOUTH 外包垂直容器：3×2 按钮阵上、插件按钮区下（GridLayout(0,2) 两列向下、空隐藏）；`onPluginSetChanged` 增 `refreshPluginButtons()`（A 类刷新） |
| `ui/ConfigWindow.java` | 修改 | SOUTH 纵向面板追加第三行 `CONFIG_WINDOW` 面板（空不渲染） |
| `ui/SchemeManagerDialog.java` | 修改 | SOUTH 改为“插件面板(若有) + 关闭按钮”纵向叠放（SCHEME_MANAGER_WINDOW） |
| `ui/NumberPicker.java` | 修改 | SOUTH 按钮行下追加 `NUMBER_PICKER` 面板（空不渲染） |
| `ui/SeatPicker.java` | 修改 | 保留既有 resultLabel 语义不变，SOUTH 改为按钮行+`SEAT_PICKER` 面板纵向叠放 |
| `examples/plugin-ui-zone-demo/SettingsZoneDemoPlugin.java` | 修改 | 单个插件向六窗口各注册“注意”按钮（同一动作；弹窗文本二期定稿、版本 2.0） |
| `examples/plugin-ui-zone-demo/SingleZoneDemoPlugin.java` | 新增 | 仅主窗注册“仅主窗按钮”（日志 SINGLEZONE_CLICKED），隔离样例 + jar |
| `README.txt` | 修改 | 34 个 .java；UiZone/PluginUiSupport 类表行；示例/QA/备份目录；插件 UI 槽一二期进度段落；画像更新 |

## 3. 机制说明
- 渲染类型：A 类仅主窗（常驻 → 提交后 EDT 刷新重建）；B 类五个对话框（每次新建实例 → 构造时渲染一次取最新已提交）。
- 空 Zone → `PluginUiSupport.createZonePanel` 返回 null → 窗口零新增元素（无插件零差异）。
- 插件按 Zone 自选（要几个注册几个）；宿主零裁剪逻辑；跨窗同名按钮各窗口独立无冲突。
- 全部沿用一期严格语义：null-zone 拒载、标题不判重、防御执行、暂存-提交、无热插拔。

## 4. 验证记录（自动化，2026-09-05）
- 编译：全量 `javac`（方式 A）零错误；主 jar 重建；演示/QA jar 以 out 编译打包。
- **none**：全部 UiZone 为空；主窗及设置/配置名单/方案管理/数字/座位六窗口无任何插件面板/按钮 → PASS。
- **all**（plugins/：BadNullZonePlugin + ExamplePlugin + SettingsZoneDemoPlugin + SingleZoneDemoPlugin）：
  - 已提交 [ExamplePlugin, SettingsZoneDemo, SingleZoneDemo]（BadNullZone 整 jar 拒载、日志含 jar 名与 PLUGIN_LOAD_ERROR）；
  - MAIN_MENU 1（ExamplePlugin 关于插件）；MAIN_WINDOW 2（注意 + 仅主窗按钮）；其余五窗口区各 1（仅注意）；
  - 主窗下方出现“注意”与“仅主窗按钮”；插件菜单含 关于插件 与三插件“名称 版本”只读项；下拉框 4 项（内置 3 + 示例插件模式）；
  - 五个对话框各出现“插件”标题面板与“注意”按钮，且均**不含**“仅主窗按钮”（隔离正确）；
  - 点击主窗“注意”→ 弹出“无运行中热插拔：增删插件/改按钮都要重启”，日志含 `DEMO_BUTTON_CLICKED`；
  - shutdown → `PLUGIN_UNLOADED` ≥3 → PASS。

## 5. 人工验收清单
- [ ] plugins/ 为空（或仅既有插件）重启 → 六窗口均无“插件”面板/按钮区，界面与现版一致。
- [ ] `plugins/SettingsZoneDemoPlugin.jar`（2.0）重启：
  - 主窗 3×2 按钮阵下方出现两列按钮区，含“注意”；
  - 打开 设置 / 配置名单 / 方案管理 / 数字设置 / 座位设置：各出现“插件”面板与“注意”；
  - 点击任一“注意”→ 弹窗“无运行中热插拔：增删插件/改按钮都要重启”，日志 `DEMO_BUTTON_CLICKED`。
- [ ] 放入 `SingleZoneDemoPlugin.jar` → 仅主窗出现“仅主窗按钮”，其余五窗口无（隔离）；删除后重启即消失。
- [ ] QA：`BadNullZonePlugin.jar` → 整 jar 拒载、日志含 jar 名与 `PLUGIN_LOAD_ERROR`、六窗口无残留（验收后移除）。
- [ ] 退出程序正常，`PLUGIN_UNLOADED` 完整。

## 6. 明确不做与后续
- 不做：密码类窗口接入；托盘/悬浮球按钮区；主窗按钮区名称/版本展示；多槽位/分组/图标/排序/自绘面板；宿主按插件裁剪窗口清单。
- 后续展望（另行评审）：计时子球等“自绘 UI + 窗口级交互”（生命周期/几何订阅钩子）。

## 7. 产物与备注
- `RandomNamePicker.jar` 已重建（含全部二期改动）；`plugins/`：ExamplePlugin.jar、SettingsZoneDemoPlugin.jar(2.0)；SingleZoneDemoPlugin.jar 位于 `examples/plugin-ui-zone-demo/`（按需放入 plugins/）。
- 备份：`.pluginui2_originals/`；任务书/报告：《插件UI槽二期任务书.md》《插件UI槽二期交付报告.md》。
- 附注：仓库 `plugins/CountUpTimerBall.jar` 非本任务产物，未触碰。

## 8. 变更记录（2026-09-05 主窗隔断）
- 主窗在"原有 3×2 功能按钮"与"插件按钮区"之间加入**水平隔断线**（JSeparator）：
  仅当存在 `MAIN_WINDOW` 插件按钮时可见；无插件/未注册时连同按钮区一起隐藏（无插件零差异）。
- 验证：自动化 none/all —— 无插件时隔断线隐藏、有插件（SettingsZoneDemo 的“注意”）时隔断线可见 → PASS；`RandomNamePicker.jar` 已重建。

## 9. 变更记录（2026-09-05 插件标识与示例合并）
- 主窗隔断升级为**“插件”标识行**：左侧“插件”文字（加粗小标题）+ 右侧水平分隔线，位于 3×2 功能按钮与插件按钮区之间；空 Zone 时标识行与按钮区一并隐藏（`NamePickerApp`：pluginCaptionLabel / pluginHeaderPanel / refreshPluginButtons）。
- **SettingsZoneDemoPlugin 并入 ExamplePlugin（2.0）**：`examples/plugin-example/ExamplePlugin` 现包含模式 + 三类“关于插件”菜单 + **六窗口“注意”按钮**（点击弹窗“无运行中热插拔：增删插件/改按钮都要重启”并记 `DEMO_BUTTON_CLICKED`）；`plugins/` 仅保留 `ExamplePlugin.jar`（2.0），原 `SettingsZoneDemoPlugin.jar` 已从 plugins/ 与示例产物中移除；`examples/plugin-ui-zone-demo/SettingsZoneDemoPlugin.java` 仅留档并标注已并入；`SingleZoneDemoPlugin` 保留作隔离验收样例。
- 验证：自动化 none/all——无插件时 标识/分隔线/按钮 全部隐藏；放 `ExamplePlugin.jar`（2.0）→ 主窗出现“插件”标识、分隔线与“注意”，设置/配置名单/方案管理/数字/座位窗口各有“插件”面板与“注意”，`注意`点击弹窗文案正确 → PASS；`RandomNamePicker.jar` 已重建；README/示例 BUILD 说明已同步。

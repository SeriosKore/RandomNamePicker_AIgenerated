# TODO 清单 — documents 文档中已规划但尚未实现的功能

> 核对日期：2026 交接期（工作区 = RandomNamePicker V1.1.0）
> 核对范围：`documents/` 全部 15 份文档（另交叉核对仓库根《插件体系改进建议.md》、`README.txt`）对照 `src/` 现状。
> 结论先行：**V1.1.0 已交付范围（Stage1–3、插件 UI 槽一/二期、正计时子球插件、宿主内建背景主题）在源码中均有实现**；下文所列均为"已立项未实施 / 文档规划后续 / 明确不做"三类，按优先级排列。

---

## 一、已立项、待批准实施（最高优先级）

### A. 插件体系二次开发一期（宿主插件生态一期）—— 全量未实施

来源：`documents/插件体系二次开发一期任务书.md`（文件本身声明"只出任务与验收口径，不动代码；待批准后实施"）。
证据：`src/` 全量检索无 `HostApi / HostEvent / HostEventListener / LoadOutcome / RejectReason / addHostEventListener / getLoadHistory / VERSION_MISMATCH / BUNDLED_HOST_CLASS / 插件状态` 等任何符号；`ModeRegistry.register/unregister` 仍为 `public static`；`PluginContext` 无事件订阅方法。→ **实施进度 0%，需批准后按 T0–T13 执行。**

- [ ] **T0**：按仓库惯例将待改源码快照至 `.plugin3_originals/`（D14）
- [ ] **T1（D2/D8）**：新增 `plugin/HostApi.java` —— 常量 `HOST_VERSION` + `API_LEVEL=1` + 公开 API 面 javadoc
- [ ] **T2（D8）**：Manifest 版本契约 —— 读取 jar `MANIFEST.MF` 的 `Host-Version-Min/Max`（可选、未声明不校验）；不满足 → 整 jar 拒载 `VERSION_MISMATCH`（日志含期望/实际）
- [ ] **T3（D3）**：打包禁令强制 —— jar 内出现任何 `com/randomnamepicker/` 前缀 class 条目 → 整 jar 拒载 `BUNDLED_HOST_CLASS`（记首个违规类名，原仅文档约束升为宿主强制）
- [ ] **T4（D4）**：`ModeRegistry.register/unregister` 由 `public static` 收紧为 **package-private**，修正 javadoc（已核全仓库无外部调用）
- [ ] **T5（D6/D7）**：事件类型与骨架 —— 新增 `HostEvent.java`（六类枚举 + 不可变载荷）、`HostEventListener.java`；`PluginManager` 按插件登记/注销监听、EDT 防御派发；onUnload / 整 jar 拒载回滚自动清理监听
- [ ] **T6（D7）**：`PluginContext` 新增 `addHostEventListener / removeHostEventListener / default getHostVersion()`
- [ ] **T7（D6/D12）**：抽取事件接线 —— 主窗 `startPicking` 发 `PICK_STARTED`、`stopPicking/logResult` 出口发 `PICK_FINISHED`（含 canPick 拦截/空结果路径）；`RollingPicker` 增加可选自动停完成回调（仅 maxTicks 自停触发，默认 null 零变化）；`FloatingBall` 双击路径接 `PICK_STARTED/PICK_FINISHED`（source=BALL）
- [ ] **T8（D6）**：状态事件接线 —— `NamePickerApp` 方案/模式切换发 `SCHEME_CHANGED/MODE_CHANGED`、插件提交处补发 `PLUGINS_CHANGED`；`SettingsWindow.toggleLock` 与 `PasswordDialog/OldPasswordDialog` 验证成功分支发 `LOCK_CHANGED`（**Ctrl+L×10 后门不派发**，已知边界）
- [ ] **T9（D9）**：`LoadOutcome` + `RejectReason` 枚举 + 只读查询 `getLoadHistory() / getLoadOutcome(jar)`；各拒载出口统一记原因码与建议文案
- [ ] **T10（D10）**：主窗"插件"菜单尾新增可点项"插件状态…"（仅 outcomes 非空时出现）→ 只读模态对话框：成功/拒载清单 + 原因与建议
- [ ] **T11（示例/QA）**：新增 `examples/plugin-qa/BadBundlePlugin`、`BadVersionPlugin`（源码+jar）；新增 `examples/plugin-events-demo/`（事件订阅演示 + Manifest 版本声明样例 + BUILD.txt + jar）；既有 QA 回归保留
- [ ] **T12（D13）**：《插件开发文档.md》按 9 条清单同批修订（信任模型如实化、打包禁令强制语义、事件订阅、各 Zone 生效时机表、ModeRegistry 内部化、宿主窗口类依赖边界、加载结果/事件获取、API 稳定性与 Host-Version 声明、插件数据与卸载边界）
- [ ] **T13（D14）**：全量 javac 零错误、重跑验收、重建 `RandomNamePicker.jar`、更新 `plugins/` 演示 jar、产出《插件体系二次开发一期交付报告.md》、同步 `README.txt` 与《交接说明.md》

---

## 二、文档标注的"后续展望 / 规划"backlog（未立项，按需评审再实施）

| # | 待办 | 来源 |
|---|---|---|
| B1 | **Stage 4+：jlink 免安装打包**、**插件运行中热插拔/重扫/动态卸载** | `README.txt` §0 进度表 / §4.9；`documents/Stage3_交付报告.md` §7 |
| B2 | **窗口级交互官方钩子**（悬浮球/主窗几何订阅、生命周期订阅）——呼应"自绘 UI + 窗口级交互"展望 | `documents/插件UI槽一期任务书.md` §7；`插件UI槽二期任务书.md` §7；`插件UI槽二期交付报告.md` §6；任务书 A 之 §7（=《建议》P0-2.4，一期明确不做） |
| B3 | **P1 系列**：ModeHandler 生命周期钩子（`canPick`/结果语义扩展）；ActionSpec 图标/提示/顺序/分组/动态显隐；官方插件"自持数据/配置"设施（替代裸 Properties+手工路径）；TestHost / headless 加载验证；插件模板工程 + 一键校验脚本；加载顺序与显示秩序显性化 | `插件体系改进建议.md` P1-5~P1-10；`documents/插件体系二次开发一期任务书.md` §7 |
| B4 | **P2 系列**：信任/安全模型（"未知插件首启确认"弹窗）；主题/窗口引擎与插件窗口互操作显性化；命名卫生 modeId 优先 API（displayName 仅展示） | `插件体系改进建议.md` P2-11~P2-13；任务书 A 之 §7 |
| B5 | **`LOCK_CHANGED` 补 Ctrl+L×10 后门路径**（需 `PasswordManager` 侧回调，涉 core→plugin 依赖方向评估） | `documents/插件体系二次开发一期任务书.md` §7 |
| B6 | **JTable 表头**与背景融合/半透明（当前"表头保持原生"，标注"可后续灰度"） | `documents/3_主题T2风险评估.md`；`documents/交接说明.md` §2/§6 |
| B7 | **整窗"皮肤级"沉浸 / 自绘 Look&Feel（T3）**，含系统原生标题栏/边框贴图能力评估 —— 明确需**另立项** | `documents/2_宿主代码主题实现.md` §0/§9 遗留提醒；`documents/背景主题插件开发方案.md` §0-D/§4；`documents/交接说明.md` §6 |

---

## 三、明确不做（设计边界，不建议列入实施，仅留档）

| 项 | 出处 |
|---|---|
| 正计时子球：多计时器、倒计时、声音/通知；悬浮球"隐藏/关闭"仅暂停保留读数、不做自动重置 | `documents/正计时子球开发方案.md` §5 |
| 插件 UI 槽：密码类窗口接入、托盘/悬浮球按钮区、主窗按钮区名称/版本展示、多槽位/分组/图标/排序/自绘面板、宿主按插件裁剪窗口清单 | `documents/插件UI槽一期任务书.md` §7 / 交付报告 §6；`插件UI槽二期任务书.md` §7 / 交付报告 §6 |
| 插件体系：沙箱/SecurityManager、签名校验、插件市场、插件间依赖解析、JPMS 模块化收紧 API 面、全量运行中热插拔 | `documents/插件体系二次开发一期任务书.md` §7；`Stage3_交付报告.md` §7 |
| 主题：输入框/密码窗/悬浮球/JOptionPane/JFileChooser 一律豁免不贴图（保可读铁律） | `documents/交接说明.md` §2；`2_宿主代码主题实现.md` §8 |
| 背景主题插件形态（已废弃，被宿主内建取代，不再发布） | `documents/背景主题插件开发方案.md` 首部声明 |

---

## 四、一致性提示（非功能遗漏，供维护参考）

- 《插件体系改进建议.md》（`documents/插件体系二次开发一期任务书.md` 的溯源"档A"）当前位于**仓库根**而非 `documents/`；如保持文档集中管理可考虑一并移入（`推送清单.md` 亦将其列为根目录建议推送项，需同步改）。
- `documents/` 现 15 份文件（`推送清单.md` 中"共 14 个"表述已过时，因新任务书已加入）。
- `README.txt` 目录树/进度表仍是 V1.1.0 口径；实施 A 项（T13/D13）时需同步更新。
- 已交付物复核结果：主窗/托盘/悬浮球三菜单、六窗口 Zone（`UiZone` 9 值 + `PluginUiSupport` 渲染端）、`plugins/` 两示例 jar、`theme` 包 8 类 + 接线均与交付报告一致，未发现"报告称已交付但代码缺失"的功能项。

---

## 附：核对依据文件清单

- `documents/插件体系二次开发一期任务书.md`（D1–D14 / T0–T13 / §7）＋ 仓库根《插件体系改进建议.md》（P0-1~4 / P1-5~10 / P2-11~13）
- `README.txt` §0、§4.9；`documents/Stage3_交付报告.md` §7
- `documents/插件UI槽一期任务书.md` §7、`插件UI槽一期交付报告.md` §6、`插件UI槽二期任务书.md` §7、`插件UI槽二期交付报告.md` §6
- `documents/正计时子球开发方案.md` §5；`documents/2_宿主代码主题实现.md` §0/§8/§9；`documents/3_主题T2风险评估.md`；`documents/交接说明.md` §2/§6

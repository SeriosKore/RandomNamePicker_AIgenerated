# Stage3 交付报告 —— 插件化（Plugin / PluginContext / PluginManager）

> 依据修订版 `Stage3.txt`（§0 D0.1–D0.14，2026-09-04 用户拍板 + 组长质询评审）执行。
> 交付内容：新 plugin 包、三处宿主集成、示例/QA 插件工程、重建 jar；自动化验证两组场景均通过。
> 改动前副本：`.stage3_originals/`（NamePickerApp / Main / FloatingBall）。

## 1. 决策记录（2026-09-04 锁定）

组长质询 → 决策（Q1–Q9 全部按推荐项）：

| 质询 | 决策 |
|---|---|
| Q1 `getDataManager()` 返回哪个实例 | 返回 NamePickerApp 自持实例（D0.8）；文档声明各 DataManager 实例文件级等价、均操作同一 data/ 根，插件不得自行 new；不做共享单例重构 |
| Q2 一个 jar 允许多个 Plugin 实现类？ | 允许（D0.9）：同 jar 多候选=多逻辑插件，共享同一 loader，整 jar 为一个事务单元 |
| Q3/Q4 冲突预检范围与失败粒度 | handler 在 onLoad 内才创建 → 预检只能在 onLoad 后的**提交阶段**做；以 **jar 为原子单元**（D0.10）：类加载失败/实例化失败/onLoad 异常/提交冲突（撞内置、撞更早提交插件、同 jar 候选互撞）→ 整 jar 拒载并回滚 |
| Q5 onLoad 半状态回滚 | **暂存-提交事务**（D0.11）：register/addXxx 只写 per-plugin pending；全通过才统一 commit+冲突预检，UI 仅提交后刷新一次；失败=丢 pending+补 onUnload，无残留 |
| Q6 §3 与 D0.3 去重矛盾 | 菜单标题一律不判重、无条件追加（D0.3），删除"去重可选"表述 |
| Q7 无附加按钮的 UI | 契约不动（8 抽象方法保持）：插件返回 null/空文本 → 主窗隐藏对应按钮、格子留空（D0.12） |
| Q8 EDT 阻塞 | 扫描/加载/实例化/onLoad 在**后台线程**（ctx 线程安全），完成后 EDT 一次性提交+刷新（D0.13） |
| Q9 坏插件 jar 构造法 | 三类样例全覆盖（D0.14）：无参构造抛异常 / onLoad 抛异常 / 注册冲突（modeId=name_list、displayName=名字列表模式），另含类加载失败按整 jar 拒载 |

另有：D0.1 宿主=ModeHost 暴露（方案 A）；D0.2 无插件也建空"插件"菜单（过 UI 测试）；
D0.4 不做运行中热插拔（悬浮球每次新建实例读取当前插件）；D0.5 防御含 canPick/nextCandidate；
D0.6 插件菜单含只读"名称 版本"；D0.7 示例插件放独立 examples/plugin-example。

## 2. 改动/新增文件清单

| 文件 | 动作 | 说明 |
|---|---|---|
| `plugin/Plugin.java` | 新增 | 插件接口：getName/getVersion/onLoad(PluginContext)/onUnload |
| `plugin/PluginContext.java` | 新增 | 注册（registerModeHandler + 三类菜单动作，暂存语义、标题不判重）+ 服务访问（含 getModeHost D0.1；getDataManager 返回宿主实例 D0.8；不暴露内部类；线程安全） |
| `plugin/PluginManager.java` | 新增 | 进程内单例：init/loadAllAsync(后台)/loadAllSync(测试)/shutdown/addUIListener；加载核心（扫描→URLClassLoader(parent-first)→条目过滤→候选收集→实例化→onLoad→提交/冲突预检→原子生效或整 jar 拒载）；查询只读快照；runMenuActionSafely |
| `ui/NamePickerApp.java` | 修改 | 新增 JMenuBar+"插件"菜单（D0.2 空菜单可见；功能项+分隔+只读"名称 版本" D0.6）；模式下拉框=内置+插件（refreshModeCombo 保持选中）；resolveModeHandler 内置/插件双源；按钮文本 null/空→隐藏（D0.12）；canPick/nextCandidate/按钮动作防御（D0.5）；注册 UI 刷新监听 |
| `main/Main.java` | 修改 | 启动：init(app) → 显示主窗 → 后台 loadAllAsync → EDT 刷新（托盘整体重建 rebuildTrayMenu 内置+插件项）；cleanupAndExit 先 PluginManager.shutdown() |
| `floating/FloatingBall.java` | 修改 | 右键菜单在"悬浮球设置/关闭"间追加插件项（每次新建实例读取，D0.4）；双击抽取对插件模式防御包裹（D0.5） |
| `examples/plugin-example/` | 新增 | ExamplePlugin.java（模式 示例插件模式→`Hello from Plugin`，无附加按钮返回 null）+ BUILD.txt + ExamplePlugin.jar |
| `examples/plugin-qa/` | 新增 | BadCtorPlugin / BadOnLoadPlugin / ConflictPlugin（含内嵌冲突 Handler）源码与 jar |

类计数：主工程 32 个 .java（原 29 + plugin 包 3）。

## 3. 插件机制实现要点

- **目录**：运行目录 `plugins/`（user.dir，与 jar/exe 同级），不存在自动创建，扫描 `*.jar`（文件名排序）。
- **加载（后台线程）**：每 jar 一个 parent-first `URLClassLoader`；遍历 .class（跳过 module-info.class、META-INF/versions/）→ `Class.forName(名,false,loader)` 任一条目失败即整 jar 失败 → 收集 `Plugin` 实现类（非抽象、public、无参构造）→ 实例化 → `onLoad(ctx)`。
- **事务（jar 原子）**：任一失败（类加载/实例化/onLoad/提交冲突）→ 整 jar 拒载，记 `PLUGIN_LOAD_ERROR`，对已成功 onLoad 的候选补 `onUnload`，丢弃全部 pending，关闭 loader，不中断其它 jar。
- **暂存-提交**：`PerPluginContext` 持有该插件 pending 区（registerModeHandler/addXxx 只写 pending）；jar 内全部候选 onLoad 成功后统一提交：冲突预检（ModeRegistry 内置 + 已提交插件 + 同 jar 候选互撞）→ 通过才追加到已提交列表并记 `PLUGIN_LOADED`；随后 EDT 一次性触发 UI 刷新监听器（主窗菜单/下拉框、托盘重建）。
- **防御**：onLoad/onUnload/菜单动作与宿主对插件的 canPick()/nextCandidate() 调用点均 try/catch（含 LinkageError 边界），失败回退占位并记日志（操作码 PLUGIN_LOAD_ERROR / 校验与候选异常日志）。
- **退出**：`Main.cleanupAndExit()` → `PluginManager.shutdown()`：逐 onUnload（记 `PLUGIN_UNLOADED`）+ 关闭全部 URLClassLoader + 清空注册。
- **线程/只读**：查询方法返回不可变快照；UI 刷新在 EDT；加载线程只写受保护状态。

## 4. 宿主集成行为（已实现的验收口径）

- 主窗：无插件时空"插件"菜单可见；有插件时含功能项（提交序）与只读"名称 版本"；下拉框＝内置 3 项 + 插件模式（提交序），切换方案不拨动插件模式；插件无按钮 → 第 4/6 格隐藏；抽取对插件模式可正常开始→滚动→停止定格并记"抽取结果"日志。
- 托盘：提交后整体重建（内置项 + 插件项 + 退出），插件菜单动作防御执行。
- 悬浮球：每次新建实例读取当前已提交插件，追加到右键菜单（悬浮球设置 与 关闭 之间）。
- 无插件时除新增空"插件"菜单外，其余行为与 Stage2 一致。

## 5. 验证记录（自动化，2026-09-04）

命令（等价）：全量 `javac -encoding UTF-8 -d out @sources.txt`（方式 A）零错误 → `jar cfm RandomNamePicker.jar manifest.txt -C out .`；示例/QA 以 out 为类路径编译并 `jar cf` 产出；自动化 UI 驱动两组独立工作目录场景：

1. **无插件回归（none）**：`plugins` 缺失 → 模式下拉框 3 项（内置）、插件菜单为空、0 插件提交 → PASS。
2. **混合隔离（all）**：`plugins/` 同时放 ExamplePlugin + BadCtor + BadOnLoad + Conflict 四个 jar →
   - 仅 ExamplePlugin 提交；菜单 main/tray/ball 各 1 项（坏插件的暂存项全部丢弃）；
   - 下拉框 4 项（3 内置 + 示例插件模式），插件菜单含"关于插件"与只读"ExamplePlugin 1.0"；
   - 选择"示例插件模式" → 开始/停止 → 定格 `Hello from Plugin`；
   - 日志：管理器 `PLUGIN_LOADED ×1`、`PLUGIN_LOAD_ERROR ×3`（拒载日志含三个坏 jar 名）、退出后 `PLUGIN_UNLOADED ≥1` → PASS。

## 6. 人工验收清单（对照 Stage3 §7，GUI 勾选）

- [ ] 无插件：删除 plugins/ 下所有 jar（或移走目录）重启 → 主窗出现空"插件"菜单、布局正常、其余行为与 Stage2 一致。
- [ ] 放入 `plugins/ExamplePlugin.jar` 重启 → 插件菜单含"关于插件"与"ExamplePlugin 1.0"；点击弹窗正常。
- [ ] 托盘菜单出现插件项；悬浮球重新打开后右键菜单出现插件项。
- [ ] 模式下拉框出现"示例插件模式"：主窗抽取定格 `Hello from Plugin`；悬浮球双击同样定格；切换方案不自动拨走该模式；第 4/6 格按钮隐藏。
- [ ] 日志出现 `PLUGIN_LOADED`；退出程序后出现 `PLUGIN_UNLOADED`。
- [ ] 坏样例（验收后移除）：逐个放入 BadCtorPlugin / BadOnLoadPlugin / ConflictPlugin 的 jar 重启 → 整 jar 拒载（菜单/下拉框无残留、内置完好），日志 `PLUGIN_LOAD_ERROR` 且好插件不受影响。
- [ ] 退出程序正常（无卡死），`cleanupAndExit` 触发 onUnload。

## 7. 明确不做（边界，对应 Stage3 §8）
运行中热插拔/重扫、插件 UI 选项面板、插件间依赖解析、签名/权限校验（"受控 API + 可信插件"，仅文档约束禁打包 API 类）、DataManager 共享单例重构；不做运行中卸载（仅退出全量 onUnload）。jlink 免安装打包与热插拔属 Stage 4+。

## 8. 产物与备注
- `RandomNamePicker.jar` 为 Stage3 版（含 plugin 包）；`plugins/ExamplePlugin.jar` 已放置（删除即回到无插件形态）；旧 Stage1 包备份 `RandomNamePicker.jar.stage1bak`。
- 插件打包命令与安装/卸载说明见 `examples/plugin-example/BUILD.txt`。
- 备份：`.stage3_originals/`（3 个被改文件）。任务书：`Stage3.txt`（含全部 D0.x 决策与验收）。

# Stage2 交付报告 —— 模式策略重构（ModeHandler / ModeHost / RollingPicker / 模式注册表）

> 依据 `Stage2.txt` 执行；重构前源码已逐文件核对；本报告含改动清单、决策记录、
> 验收对照、手动测试清单。备份：`.stage2_originals\`（6 个被改文件的迁移前副本）。

## 1. 决策记录（用户已确认）

- **D1 严格保真**：主窗数据缺失点击（名单空 / 未设数字范围 / 座位未配置未选）弹窗后，
  仍按旧格式补写一行 `方案-模式=当时标签文本` 的"抽取结果"日志（与迁移前 stopPicking 语义一致）。
- **D2 按规范建齐**：`ModeHost` 含 `getCurrentScheme / getNameManager / getSchemeManager /
  getDataManager / getLogManager / getOwner`；`NamePickerApp` 自持 `DataManager`、`LogManager`
  实例供访问器返回（内置模式当前不调用这两个访问器，纯为接口完备 / Stage3 插件预留）。
- **D3 行为保真**：悬浮球仅"名字列表模式"做 >5 字符截断（`xxx...`）；数字恒等显示；
  座位候选还原为无空格 `(x,y)`（主窗仍为 `(x, y)`，两处各自保持原显示）。截断/格式化全部在
  悬浮球自己的显示回调内完成，不写入引擎。
- **D4 交付范围**：不重建 `RandomNamePicker.jar`、不改 README（用户自行维护）；交付 = 源码 +
  编译验证 + 本报告。

## 2. 改动文件清单

| 文件 | 动作 | 说明 |
|---|---|---|
| `mode/ModeHost.java` | 新增 | 宿主接口，替代子类对 `NamePickerApp` 的直接依赖 |
| `mode/ModeHandler.java` | 重写 | 构造参数改 `ModeHost`；抽象方法收敛为 8 个（getModeId/getDisplayName/canPick/nextCandidate/getButton1Text/getButton2Text/handleButton1Click/handleButton2Click）；删除死方法 getModeButton1/2；mode 包不再依赖 ui |
| `ui/RollingPicker.java` | 新增 | 通用滚停引擎：Supplier 候选 + Consumer 显示回调 + 间隔(默认 50ms) + maxTicks(null=无限 / 20=悬浮球)；start/stop/getLastValue；引擎不含截断与空数据提示 |
| `mode/ModeRegistry.java` | 新增 | 最小有序注册表（LinkedHashMap）：modeId→(displayName, schemeType, 工厂)；下拉框数据源与 Handler 解析统一走注册表；register/unregister 为插件预留 |
| `mode/NameListModeHandler.java` | 重写 | 新契约实现；canPick 空名单→"名单已损坏，请重新导入"；nextCandidate 从开始时的名单快照随机取；导入/导出（含锁定拦截、JFileChooser、弹窗文案）原样，宿主改 ModeHost |
| `mode/NumberModeHandler.java` | 重写 | 新契约实现；canPick 无范围→"请先设置数字范围！"；取样公式原样 `nextInt(max-min+1)+min`；设置/保存数字范围行为与日志操作码不变 |
| `mode/SeatModeHandler.java` | 重写 | 新契约实现；canPick 无配置/未选座位→"请先设置座位并选择座位！"；候选为主窗格式 `(x, y)`；设置/保存座位行为与日志操作码不变 |
| `ui/NamePickerApp.java` | 重写 | `implements ModeHost`；下拉框数据源=注册表；统一 startPicking（canPick→按模式弹窗+复位并补日志(D1)，否则 RollingPicker 无限滚动）；stopPicking 记"抽取结果"日志并复位；onSchemeChanged 经注册表联动；附加按钮绑定逻辑与 3×2 网格不变；新增 getCurrentModeHandler() 供悬浮球；updateDisplayText 保留（NumberPicker 在用） |
| `floating/FloatingBall.java` | 重写 | 双击抽取：取当前 Handler→canPick 非 null 按 modeId 显示"损坏/无范围/无座位"（未知插件回退 canPick 文本），否则 RollingPicker(maxTicks=20) 滚动；名字 5 字截断与座位 (x,y) 还原在显示回调内(D3)；右键菜单内容与"构造时生成一次"不变 |

## 3. 验收标准对照（Stage2 第 56-64 行）

1. **编译零错误**：✅ `javac -encoding UTF-8 -d out @sources.txt`（Stage1 方式 A）全量通过，
   共 40 个 class。最终源码 29 个 .java（原 26 + 新增 ModeHost/ModeRegistry/RollingPicker）。
2. **行为等价**（代码层面逐项核对，GUI 逐条请按第 4 节手动清单复验）：
   - 1) 主窗三模式：开始→无限滚动→手动停止定格；日志行 `方案-模式显示名=定格结果`（操作码"抽取结果"），
     与迁移前格式一致（座位为 `(x, y)` 带空格）。
   - 2) 数据缺失：主窗弹窗文案与触发一致（名字=错误/ERROR；数字与座位=提示/WARNING），并按 D1 补记日志行；
     悬浮球短文本 损坏/无范围/无座位 不弹窗。
   - 3) 悬浮球双击：固定 20 次后自动定格；名字 >5 截断为 `xxx...`；候选与主窗同源同公式
     （共用 handler.nextCandidate()）。
   - 4) 方案切换自动联动内置模式、lastScheme 恢复、附加按钮文本与点击行为不变（网格第 4/6 格）。
   - 5) 锁定模式下导入/导出等仍被拦截（PasswordManager.isLocked 逻辑原样）。
3. **扩展点验证**：✅ 已用临时"模拟插件"`DemoPluginModeHandler`（modeId=demo_plugin，
   显示名=插件演示模式）注册到 ModeRegistry 验证：注册表出现第 4 项且顺序正确、按显示名可解析、
   经 `definition.create(host)` 实例化后 canPick()=null 且 nextCandidate() 可产出候选
   （无 GUI 桩宿主验证，退出码 0）。验证后已删除演示类与注册行，最终源码仅含内置三项。

## 4. 手动测试清单（GUI，勾选复验）

启动方式：命令行 `tools\build.bat`（会重建 jar）或 IDE 运行
`com.randomnamepicker.main.Main`。建议先备份 `data/`、`log/`。

- [ ] 1.1 名字列表：名单非空 → 开始抽取 → 界面无限滚动 → 停止 → 定格；`log/Modifylog.txt`
      新增 `方案-名字列表模式=定格结果`（操作码"抽取结果"）。
- [ ] 1.2 数字：设置范围 [min,max] 并应用 → 开始/停止 → 定格数字在范围内；日志同 1.1 格式。
- [ ] 1.3 座位：设置布局并勾选座位 → 开始/停止 → 定格 `(x, y)`（逗号后有空格）；日志同格式。
- [ ] 2.1 名单为空时点开始 → 弹窗标题"错误"红叉"名单已损坏，请重新导入"；按钮复位为"开始抽取"；
      且该次点击仍补记一行"抽取结果"日志（值=弹窗前标签文本）——D1。
- [ ] 2.2 数字未设范围 → 弹窗"提示"三角叹号"请先设置数字范围！"+ 复位 + D1 日志行。
- [ ] 2.3 座位未配置或未选座位 → 弹窗"请先设置座位并选择座位！"+ 复位 + D1 日志行。
- [ ] 2.4 悬浮球在三种缺数据状态双击 → 分别显示"损坏 / 无范围 / 无座位"，不弹窗。
- [ ] 3.1 悬浮球双击 → 恰好 20 次滚动后自动定格。
- [ ] 3.2 名单含 >5 字名字时悬浮球定格/滚动显示为前 5 字 + "..."；主窗不截断。
- [ ] 3.3 悬浮球座位定格显示 `(x,y)`（无空格）——与主窗 `(x, y)` 各自保持。
- [ ] 4.1 切换方案类型 name_list/number/seat → 模式下拉框自动拨到对应内置模式；重启后恢复上次方案。
- [ ] 4.2 各模式附加按钮文本：导入名单/导出名单、设置数字范围/保存数字范围、设置座位布局/保存座位设置；
      位于按钮网格第 4、6 格；点击行为（含弹窗、JFileChooser）与迁移前一致。
- [ ] 5.1 锁定模式下点导入/导出 → 弹"当前为锁定模式，请先解锁"。
- [ ] 6.1 悬浮球右键菜单内容与迁移前一致（随机抽取/配置名单或数字设置或座位设置/悬浮球设置/关闭）。
- [ ] 6.2 数字/座位设置对话框内部滚显与"抽取结果: ..."文案与迁移前一致（未改动）。

## 5. 保留与未动（逐字未改）

`NumberPicker/SeatPicker` 内部 Timer 滚显与文案、导入导出/数字范围/座位布局原行为、加密与三副本
备份、已知死代码与原坑（Stage1 清单）、悬浮球右键菜单内容与生成时机、`updateDisplayText()`
public 签名（NumberPicker 依赖）、托盘/锁机制等。

## 6. 修复记录（2026-09-04：模态子窗口白屏死锁）

**现象**：新版 jar 主窗口正常，但所有模态子窗口（配置名单/数字设置/座位设置/设置等）全白、
无法操作、无法关闭，只能结束 java 进程；悬浮球右键菜单打开的界面外观正常但同样不可操作。

**根因**：`NamePickerApp` 为实现 `ModeHost.getOwner()`（声明返回 `java.awt.Frame`）而协变重写了
`java.awt.Window#getOwner()` 并返回 `this`——窗口把自己的 owner 指向自己。AWT 弹出模态对话框时
沿 owner 链遍历/建立模态关系，遇到自引用后死锁，EDT 卡死 → 白屏、无法关闭；非模态的主窗与悬浮球
不受影响，与现象完全吻合。

**修复**（对应 §2 中 `ModeHost` / `NamePickerApp` / `NumberModeHandler` / `SeatModeHandler`）：
- `ModeHost.getOwner()` 返回类型由 `Frame` 改为 `java.awt.Window`——与 AWT 同名方法签名一致，
  `NamePickerApp` 直接继承实现，**不再重写** `getOwner()`（已删除原重写方法并注释原因）。
- `NumberModeHandler` / `SeatModeHandler` 构造对话框处显式转型 `(Frame) host.getOwner()`
  （当前唯一宿主 NamePickerApp 即 JFrame，运行时安全；对话框内部本就强转 NamePickerApp）。
- `JOptionPane` / `JFileChooser` 父窗口直接使用 `host.getOwner()`（Window 即 Component），无需转型。

**验证**：自动化驱动逐个以真实模态方式打开 配置名单（执行 添加 0+2 / 删除 -1）→ 数字设置 →
座位设置 → 设置，全部"可见且可操作、可正常关闭"，`MODAL_DRIVER_PASS`；全量 javac 零错误；
`RandomNamePicker.jar` 已按修复后源码重建（旧 Stage1 包备份为 `RandomNamePicker.jar.stage1bak`）。

### 配置名单"添加/删除"不可见（2026-09-04 第二次修复）

**现象**：窗口可用，但"姓名输入 + 添加/删除"那一行看不到（早期版本即存在，非 Stage2 引入）。

**根因**：`ConfigWindow.setupLayout()` 把 `inputPanel`（姓名/添加/删除）与 `bottomPanel`
（导入/导出/保存）**都 add 到 BorderLayout.SOUTH**——后加入的 bottomPanel 覆盖了先加入的
inputPanel，导致"添加名字/删除名字"整行从不显示（组件仍在树中，程序上可调用，但用户不可见
不可点）。

**修复**：两行纵向叠放（`BoxLayout.Y_AXIS` 的 southPanel）后整体放入 SOUTH，
复原"姓名输入框 + 添加 + 删除"的可见可用。备份于 `.stage2_originals\ConfigWindow.java`。

**验证**：模态打开配置名单后断言 姓名输入框/添加/删除 与 导入/导出/保存 均 `isShowing()`，
并实际执行 添加 +2 / 删除 -1，`VIS_DRIVER_PASS`；全量 javac 零错误；jar 已重建。

## 7. 备注与 Stage3 提示

- `RollingPicker` 按 Stage2 允许范围放入 `ui` 包（唯一 Swing 依赖 javax.swing.Timer）。
- 注册表工厂使用 `Function<ModeHost,ModeHandler>` 而非无参 Supplier：Handler 构造需宿主实例
  （主窗/未来宿主不可预知），取用时注入，注册表静态初始化只注册工厂引用。
- 弹窗外观（错误 vs 提示、ERROR vs WARNING）由主窗按 modeId 区分（与悬浮球按 modeId 映射短文本
  同一种宿主侧映射哲学）；未知插件模式走默认"提示/WARNING"，Stage3 可再定规则。
- `ModeHost.getDataManager()/getLogManager()` 当前无内置调用方（DataManager 在
  NameManager/SchemeManager 内各自私有持有、LogManager 全静态），已按 D2 建齐并返回
  NamePickerApp 自持实例，供 Stage3 插件能力使用。
- FloatingBall 右键菜单仍含按中文字符串 switch 的菜单项组装——Stage2 明确"菜单内容不变、
  本阶段不动态化"，留待 Stage3 动态菜单时一并改为 modeId/插件驱动。
- 编译环境提示：本会话沙箱 PATH 不含 cmd/javac，实际以 `JAVA_HOME`（jdk-22）全路径调用 javac
  完成；交互环境直接跑 `tools\build.bat` 即可重建 jar。

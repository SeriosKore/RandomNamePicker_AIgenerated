# 更新日志（Changelog）

## v1.1.0 — 名单修改日志 · 开机自启增强 · 多人点名 · 插件生态

### 新增功能

1. **名单修改日志**
   - 名单发生新增 / 删除 / 修改时自动记录到程序运行目录下的 `modification_log.txt`；
   - 日志内容包含：操作时间（精确到秒）、操作类型（新增/删除/修改）、涉及条目、修改前后内容对比；
   - 采用追加写入，绝不覆盖历史记录；文件不存在时自动创建；
   - 设置界面新增“查看名单修改日志”入口。

2. **开机自启设置增强**
   - 开启时自动写入注册表 `HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run`（值名 `RandomNamePicker`）；
   - 自动定位当前主程序完整路径（优先代码源定位的 jar/exe，其次工作目录下的 `RandomNamePicker.exe`），路径含空格时自动加英文双引号；
   - 关闭时自动删除注册表项；非 Windows 系统给出提示并跳过；访问失败时给出明确提示；
   - 设置界面打开时与注册表实际状态自动同步。

3. **多人点名**
   - 设置界面新增“单次抽取数量”设置项（最小值 1，最大值 = 当前名单总人数）；
   - 非法输入（非数字、小于 1、大于名单总人数）拒绝并提示；名单人数变化导致设置值超限时自动修正并提示；
   - 抽取数量为 1 时悬浮球保持原有单球效果不变；
   - 多人抽取结果自动去重且不超过名单总人数；主窗口同时展示全部中奖者；
   - **悬浮球多球动画独立为“多悬浮球插件”**：主球 + 中奖者小球弹出/旋转/浮动、渐隐尾迹、多环防重叠、透明度可调（插件设置面板）。

4. **插件生态（不修改主程序即可扩展功能）**
   - 新增 `Plugin` / `PluginContext` / `PluginManager` / `FloatingBallPickHandler`：扫描 `extensions/*.jar`，按清单 `Plugin-Class` 属性自动加载；
   - 标准扩展点：主窗口“插件”菜单、插件按钮区、系统托盘菜单、悬浮球右键菜单、设置窗口插件面板、**悬浮球抽取拦截**；
   - **UI 篡改接口**：`setMainDisplayText` / `registerMainComponent` / `addMainMenu` / `getMainApp()`；
   - 插件异常隔离，退出自动 `onUnload`；模型类 `Scheme`/`NumberRange`/`SeatConfig` 提升为 public；
   - 随仓库发布两个插件：
     - **多悬浮球插件(示例插件)**：接管多人点名悬浮球动画；
     - **屏幕画笔插件**：参考 Seewo 白板悬浮球画笔——JNA 原生 WM_POINTER 输入、Catmull-Rom 平滑 + 变宽轮廓笔锋（速度/压感、起收笔渐细）、增量渲染局部重绘保证流畅；**触屏大面积接触自动识别为橡皮**，鼠标右键为橡皮；含工具条（选色/清屏/退出）与 Esc 退出；
   - 文档：`docs/插件接口文档.md`、`docs/插件开发文档.md`；示例源码 `examples/`；插件加载/注册/卸载已通过自动化检查。

### 打包与交付

- 新增 `plugins/package.bat`：一键编译打包并生成 release 目录（主程序 JAR + 示例名单 + 说明文档）；
- 新增 `plugins/selfcheck.bat`：在临时目录自动执行核心逻辑自检（修改日志、去重抽取、配置项读写）；
- `build.bat` 移至项目根目录，双击即可运行；
- **免安装版（无需预装 Java）**：`jlink` 生成 44.5MB 精简运行时（java.desktop）→ `jpackage --type app-image` 生成免安装目录 `RandomNamePicker/`（含 `RandomNamePicker.exe`），压缩为 `RandomNamePicker-v1.1.0-windows-x64.zip`（约 30MB）随 Release 发布；自启动功能自动适配 jpackage 目录结构；
- release 目录仅需保留主程序与名单文件即可运行，配置、日志、数据文件均首次运行时自动生成。

### 其他

- README 更新架构图、目录结构、配置项、使用说明与插件开发指南。

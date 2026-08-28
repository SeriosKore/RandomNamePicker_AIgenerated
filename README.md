# 多功能随机抽取器（RandomNamePicker）

一个基于 **Java Swing** 的 Windows 桌面端随机抽取工具，支持**名字列表、数字范围、座位**三种抽取模式，并提供预设方案管理、桌面悬浮球、系统托盘、开机自启动、数据加密存储与操作日志等完整功能，适用于课堂点名、抽奖、随机排座等场景。

---

## 目录

- [1. 项目简介](#1-项目简介)
- [2. 功能特性](#2-功能特性)
- [3. 运行环境](#3-运行环境)
- [4. 系统架构](#4-系统架构)
- [5. 目录结构](#5-目录结构)
- [6. 核心流程](#6-核心流程)
- [7. 数据存储与安全设计](#7-数据存储与安全设计)
- [8. 编译与运行](#8-编译与运行)
- [9. 使用说明](#9-使用说明)
- [10. 配置项说明](#10-配置项说明)
- [11. 已知问题与改进方向](#11-已知问题与改进方向)
- [12. 插件开发指南](#12-插件开发指南)

---

## 1. 项目简介

本项目是一个纯 Java 实现的桌面应用程序，无第三方依赖，仅使用 JDK 标准库（Swing/AWT、`javax.crypto`、`java.security` 等）。程序围绕“预设方案”组织数据：每个方案绑定一种抽取模式，用户可创建多套方案（如“一班学生名单”“1~100 抽号”“教室座位图”），随时切换抽取。

项目特色：

- **多模式抽取**：同一界面下支持名单、数字、座位三种抽取模式；
- **悬浮球**：置顶半透明悬浮球，可拖拽、双击抽取、右键快捷菜单，不遮挡正常使用；
- **系统托盘集成**：可最小化到托盘后台运行；
- **数据安全**：所有名单/数字/座位数据使用 AES-GCM 加密存储，SHA-256 完整性校验，并自动维护三份冗余副本，损坏时自动恢复；
- **权限保护**：锁定模式下修改任何配置都需要密码验证。

### 项目地址与下载

- GitHub 仓库：<https://github.com/SeriosKore/RandomNamePicker_AIgenerated>
- 发布页面（Releases）：<https://github.com/SeriosKore/RandomNamePicker_AIgenerated/releases>
- 最新版本 **v1.1.0** 新增：名单修改日志、开机自启增强、多人点名（详见 `CHANGELOG.md` 与发布说明）；
- 历史版本：V0.4.1.2（exe 版 / jar 版）见 Releases 页面；
- 下载后只需将主程序（JAR/EXE）与名单文件放在**同一目录**运行即可，`data/`、`log/`、`modification_log.txt` 等文件首次运行时自动生成，无需其他辅助文件。

---

## 2. 功能特性

### 2.1 三种抽取模式

| 模式 | 说明 |
| --- | --- |
| 名字列表模式 | 从名单中随机抽取姓名，支持名单导入/导出（TXT） |
| 数字模式 | 在自定义 `[最小值, 最大值]` 区间内随机抽取整数 |
| 座位模式 | 可视化座位图（网格），勾选候选座位后在已选座位中随机抽取坐标 |

所有模式均采用 `javax.swing.Timer`（50ms 间隔）+ `SecureRandom` 实现“滚动抽取”动画，点击“停止”定格结果，并将结果写入日志。

### 2.2 预设方案管理

- 创建/删除方案，方案类型与抽取模式绑定（`name_list` / `number` / `seat`）；
- 主界面下拉框切换方案，自动记忆上次使用的方案（`lastScheme`）；
- 内置“默认方案”始终可用。

### 2.3 名单管理

- 表格化增、删名单；
- 从 TXT 文件批量导入（UTF-8，每行一个姓名）；
- 导出为 TXT 文件。

### 2.4 数字范围设置

- 设置最小值/最大值，校验 `min < max`；
- 可在设置对话框内直接试抽，也可保存并应用到当前方案。

### 2.5 座位设置

- 输入行列数生成座位网格（`GridLayout`）；
- 鼠标点击切换座位选中状态（黄色高亮），自动保存；
- 支持任意行列规模，网格区域可滚动。

### 2.6 桌面悬浮球

- `JWindow` 实现的置顶半透明圆球，可拖拽移动；
- 双击悬浮球：滚动抽取并定格结果；
- 右键菜单：随机抽取、当前模式的快捷设置入口、悬浮球设置、关闭；
- 半径（30~100 像素）与透明度（50~255）可在设置中调整；
- 内置置顶保持定时器，避免被其他窗口遮挡。

### 2.7 系统托盘

- 托盘图标与菜单：显示/隐藏主窗口、显示/隐藏悬浮球、退出程序；
- 支持“关闭窗口时最小化到托盘”选项。

### 2.8 系统设置

- 开机自启动（写入注册表 `HKCU\...\CurrentVersion\Run`，可注册/注销/查询状态）；
- 悬浮球外观（半径、透明度）；
- 日志导出；
- 配置锁定/解锁与密码修改。

### 2.9 安全保护

- **配置锁定**：锁定状态下，名单增删/导入导出/方案增删等修改操作均被拒绝，需密码解锁；
- **密码**：SHA-256 哈希存储，支持修改密码（原密码验证 + 两次新密码一致性校验，长度≥4）；
- **数据加密**：详见[第 7 节](#7-数据存储与安全设计)。

### 2.10 操作日志

所有关键操作（保存/导入/导出/创建/删除方案、抽取结果、异常等）以 `[时间] 【内容】【操作类型】` 格式追加到 `log/Modifylog.txt`，可在设置中导出。

### 2.11 名单修改日志

- 名单发生**新增 / 删除 / 修改**时自动记录到程序运行目录下的 `modification_log.txt`（文件不存在时自动创建）；
- 日志包含：操作时间（精确到秒）、操作类型（新增/删除/修改）、涉及的条目、修改前后内容对比；
- 采用**追加写入**，绝不覆盖历史记录；保存时自动与磁盘上旧名单对比归类；
- 设置界面提供“查看名单修改日志”入口。

### 2.12 多人点名

- 设置界面新增“单次抽取数量”设置项（最小值 1，最大值 = 当前名单总人数）；
- 非法值（非数字、小于 1、大于名单总人数）被拒绝并提示；名单人数变化导致设置值超限时，自动修正为当前最大值并提示；
- 抽取数量为 1 时悬浮球保持原有单球效果；
- 抽取数量大于 1 时，悬浮球变为主球体 + 中奖者小球从主球周围**弹出、旋转、浮动**的动画，每个小球显示对应姓名；
- 多人抽取结果**自动去重**且不超过名单总人数；主窗口同时以“姓名1、姓名2…”形式展示全部中奖者；
- **多球透明度可调**：设置界面“悬浮球外观”新增“多球透明度”滑块（50~255）；
- **尾迹渐隐动画**：中奖者小球运动轨迹以渐隐尾迹呈现（逐帧 Alpha 衰减），尾迹逐渐消失；
- **多环防重叠布局**：中奖人数较多时自动排列为多个同心圆环——每环容量按“周长 ÷ (小球直径 + 间隙)”计算，相邻环错开半个相位、内环转速快于外环，保证各小球互不重叠。

### 2.13 插件生态（不修改主程序即可扩展功能）

- 将插件 JAR（清单文件含 `Plugin-Class` 属性）放入程序运行目录下的 `extensions/` 文件夹，程序启动时**自动扫描并加载**；
- 标准扩展点：主窗口“插件”菜单、主窗口插件按钮区、系统托盘菜单、悬浮球右键菜单、设置窗口插件面板；
- **UI 篡改接口**：插件可注入自定义菜单/组件（`registerMainComponent`/`addMainMenu`）、修改主窗口显示文本（`setMainDisplayText`）、直接获取主窗口实例（`getMainApp()`）自由改造界面；
- 插件异常被隔离：加载/运行失败只记录日志，不影响主程序与其他插件；程序退出时自动调用 `onUnload()` 卸载；
- 附带 `ExamplePlugin` 示例插件与构建脚本（`examples/ExamplePlugin/`），完整演示全部扩展点与篡改接口；开发规范见[第 12 节](#12-插件开发指南)。

---

## 3. 运行环境

| 项目 | 要求 |
| --- | --- |
| 操作系统 | Windows（使用系统托盘、注册表自启动等 Windows 特性） |
| JDK | JDK 8 及以上（使用了 `PBKDF2WithHmacSHA256`、AES-GCM、`SystemTray` 等 API） |
| 依赖 | 无第三方依赖，纯 JDK 标准库 |
| 编码 | 源文件与数据文件均为 UTF-8 |

> 编译时请务必加 `-encoding UTF-8`，否则中文字符串会乱码。

---

## 4. 系统架构

### 4.1 总体架构

程序采用经典的分层架构，自上而下分为表现层、模式抽象层、业务逻辑层和数据访问层：

```
┌────────────────────────────────────────────────────────────────┐
│                          表现层（UI 层）                         │
│  Main（入口/系统托盘/自启动）  NamePickerApp（主窗口）            │
│  FloatingBall（悬浮球）  ConfigWindow（名单管理）                │
│  SettingsWindow（设置）  SchemeManagerDialog（方案管理）         │
│  NumberPicker（数字设置）  SeatPicker（座位设置）                │
│  PasswordDialog / OldPasswordDialog / NewPasswordDialog /       │
│  ChangePasswordDialog（密码系列对话框）                          │
└───────────────────────────┬────────────────────────────────────┘
                            │ 事件监听与直接调用
┌───────────────────────────▼────────────────────────────────────┐
│                    模式抽象层（策略模式）                        │
│  ModeHandler（抽象基类）                                         │
│    ├── NameListModeHandler（导入/导出名单按钮逻辑）              │
│    ├── NumberModeHandler（设置/保存数字范围按钮逻辑）            │
│    └── SeatModeHandler（设置/保存座位布局按钮逻辑）              │
└───────────────────────────┬────────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────────┐
│                     业务逻辑层（服务层）                         │
│  NameManager（名单 CRUD/导入导出）  SchemeManager（方案/范围/座位）│
│  PasswordManager（锁定状态/密码校验与修改）                      │
│  领域模型：Scheme、NumberRange、SeatConfig                      │
└───────────────────────────┬────────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────────┘
│                      数据访问与工具层                            │
│  DataManager（加密读写/哈希校验/三副本备份恢复）                 │
│  ConfigManager（config.properties 配置读写）                    │
│  LogManager（操作日志）  ModificationLogManager（名单修改日志）  │
│  EncryptionUtil（AES-GCM/RSA/SHA-256）                          │
│                                                                │
│  运行时数据文件：data/config.properties、data/schemes/、         │
│  data/backup/、log/、modification_log.txt                       │
└────────────────────────────────────────────────────────────────┘
              │
              │  插件生态（PluginManager 按 Plugin-Class 清单属性加载）
              ▼
┌────────────────────────────────────────────────────────────────┐
│                   extensions/*.jar（第三方插件）                 │
│  实现 Plugin 接口，通过 PluginContext 注册扩展点：               │
│  主菜单/按钮区/托盘/悬浮球菜单/设置面板 + UI 篡改接口             │
└────────────────────────────────────────────────────────────────┘
```

### 4.2 设计要点

1. **模式抽象（策略模式）**：`NamePickerApp` 不关心具体模式逻辑，仅通过 `ModeHandler` 抽象接口获取模式专属按钮文本与点击行为。切换模式时创建对应的 Handler 子类并重绑按钮监听器，扩展新模式只需新增一个 `ModeHandler` 子类。
2. **分层解耦**：UI 类只持有 `NameManager`/`SchemeManager` 服务引用，所有文件操作收敛到 `DataManager`，所有密码学操作收敛到 `EncryptionUtil`。
3. **静态工具 + 实例服务混合**：`ConfigManager`、`LogManager`、`PasswordManager`、`EncryptionUtil` 以静态方法提供全局能力；`NameManager`、`SchemeManager`、`DataManager` 以实例方式由 `NamePickerApp` 持有并注入到各窗口。
4. **Swing 事件驱动**：所有抽取动画基于 `javax.swing.Timer`，所有界面交互基于 `ActionListener`/`MouseAdapter`，主线程通过 `SwingUtilities.invokeLater` 启动界面。

### 4.3 类与模块职责

| 类 | 层级 | 职责 |
| --- | --- | --- |
| `Main` | 表现层 | 程序入口；系统托盘创建与菜单；注册表开机自启动的注册/注销/查询（自动定位主程序路径，非 Windows 跳过） |
| `NamePickerApp` | 表现层 | 主窗口：方案/模式选择、抽取动画控制、多人点名结果展示、调度各子窗口、悬浮球生命周期管理 |
| `FloatingBall` | 表现层 | 置顶悬浮球：拖拽、双击抽取、右键菜单、置顶保持、多人点名多球动画调度 |
| `MultiPickBallWindow` | 表现层 | 多人点名动画窗口：主球体 + 中奖者小球弹出/旋转/浮动、渐隐尾迹、多环防重叠布局、多球透明度 |
| `ConfigWindow` | 表现层 | 名单管理对话框：表格增删、导入导出、保存 |
| `SettingsWindow` | 表现层 | 系统设置对话框：自启动、托盘策略、悬浮球外观（含多球透明度）、单次抽取数量、日志导出/查看、插件面板、锁定/改密 |
| `SchemeManagerDialog` | 表现层 | 方案的创建与删除 |
| `NumberPicker` | 表现层 | 数字范围设置与试抽 |
| `SeatPicker` | 表现层 | 座位网格生成、座位点选、试抽与保存 |
| `PasswordDialog` 等 4 个 | 表现层 | 密码验证、原密码验证、新密码设置、修改密码对话框 |
| `ModeHandler` + 3 子类 | 模式抽象层 | 各模式的专属按钮文本与点击处理 |
| `NameManager` | 业务层 | 名单加载/增删/清空/导入/导出；保存时对比前后内容写入名单修改日志 |
| `SchemeManager` | 业务层 | 方案索引管理；数字范围与座位配置的序列化/反序列化 |
| `PasswordManager` | 业务层 | 锁定状态机、密码校验、修改密码、备用解锁通道 |
| `DataManager` | 数据层 | 加密文件读写、完整性校验、三副本备份与自动恢复 |
| `ConfigManager` | 数据层 | `data/config.properties` 的加载/保存与各配置项读写（含 pickCount） |
| `LogManager` | 数据层 | 操作日志追加、读取、清空 |
| `ModificationLogManager` | 数据层 | 名单修改日志（modification_log.txt）追加写入与读取 |
| `PluginManager` | 插件层 | 扫描 extensions/*.jar 并加载插件；维护各扩展点注册表；退出时统一卸载 |
| `Plugin` / `PluginContext` | 插件层 | 插件开发接口与插件上下文（标准扩展点 + UI 篡改接口） |
| `EncryptionUtil` | 工具层 | AES-GCM 加解密（PBKDF2 派生密钥）、RSA 加解密、SHA-256 哈希、Base64 |

---

## 5. 目录结构

```
RandomNamePicker/
├── src/                          # 全部源代码（26 个类，默认包）
│   ├── Main.java                 # 程序入口 + 系统托盘 + 开机自启动
│   ├── NamePickerApp.java        # 主窗口
│   ├── FloatingBall.java         # 桌面悬浮球
│   ├── MultiPickBallWindow.java  # 多人点名多球动画窗口
│   ├── ConfigWindow.java         # 名单配置窗口
│   ├── SettingsWindow.java       # 系统设置窗口
│   ├── SchemeManagerDialog.java  # 方案管理窗口
│   ├── NumberPicker.java         # 数字范围设置窗口
│   ├── SeatPicker.java           # 座位布局设置窗口
│   ├── PasswordDialog.java       # 密码验证对话框
│   ├── OldPasswordDialog.java    # 原密码验证对话框
│   ├── NewPasswordDialog.java    # 新密码设置对话框
│   ├── ChangePasswordDialog.java # 修改密码对话框
│   ├── ModeHandler.java          # 模式抽象基类
│   ├── NameListModeHandler.java  # 名单模式处理器
│   ├── NumberModeHandler.java    # 数字模式处理器
│   ├── SeatModeHandler.java      # 座位模式处理器
│   ├── NameManager.java          # 名单业务逻辑
│   ├── SchemeManager.java        # 方案业务逻辑（含 Scheme/NumberRange/SeatConfig 模型）
│   ├── DataManager.java          # 加密数据读写与备份恢复
│   ├── ConfigManager.java        # 配置文件管理
│   ├── LogManager.java           # 操作日志管理
│   ├── ModificationLogManager.java # 名单修改日志管理
│   ├── Plugin.java               # 插件开发接口
│   ├── PluginContext.java        # 插件上下文（扩展点 + UI 篡改接口）
│   ├── PluginManager.java        # 插件扫描/加载/注册/卸载
│   ├── PluginContextImpl.java    # 插件上下文实现
│   ├── EncryptionUtil.java       # 加密与哈希工具
│   └── PasswordManager.java      # 密码与锁定状态管理
├── plugins/                      # 辅助脚本
│   ├── package.bat               # 一键编译打包并生成 release 目录（推荐）
│   ├── selfcheck.bat             # 核心逻辑自检脚本（在临时目录运行）
│   ├── selftest/SelfCheck.java   # 自检类（名单日志/去重抽取/配置项）
│   ├── Creator.bat               # 注册开机自启动（注册表）
│   └── LogClearer.bat            # 注销开机自启动（注册表）
├── examples/                     # 插件开发示例
│   └── ExamplePlugin/            # 示例插件（源码 + 构建脚本 + 成品 JAR）
├── extensions/                   # 运行时插件目录：放入插件 JAR 即自动加载
│   └── ExamplePlugin.jar         # 示例插件（可删除以禁用）
├── build.bat                     # 编译打包脚本（位于根目录，双击即可运行）
├── data/                         # 运行时生成：配置与加密数据
│   ├── config.properties         # 全局配置
│   ├── schemes/                  # 各方案加密数据（主副本）
│   └── backup/                   # 加密数据备份副本
├── log/                          # 运行时生成：操作日志与伪装备份
│   ├── Modifylog.txt             # 操作日志
│   ├── sys_cache_*.dat           # 名单数据的伪装备份
│   ├── usr_config_*.tmp          # 数字范围数据的伪装备份
│   └── app_data_*.log            # 座位数据的伪装备份
├── modification_log.txt          # 运行时生成：名单修改日志（追加写入）
├── 示例名单.txt                  # 示例名单（随 release 分发）
├── .gitignore
└── README.md                     # 本文档
```

> `data/`、`log/`、`modification_log.txt` 与 `extensions/` 由程序首次运行时自动创建。

---

## 6. 核心流程

### 6.1 启动流程

```
Main.main()
  ├─ 设置系统外观（SystemLookAndFeel）
  ├─ PasswordManager.isLocked()     → 加载密码配置，默认处于“锁定”状态
  ├─ applyAutoStartSetting()        → 若配置启用自启动则注册注册表项
  └─ SwingUtilities.invokeLater:
       ├─ 创建 NamePickerApp（初始化组件、加载方案、恢复上次方案）
       ├─ 若支持系统托盘 → 创建托盘图标与菜单
       └─ 显示主窗口
```

### 6.2 抽取流程（以名字模式为例）

```
点击“开始抽取”
  → 校验方案/模式已选择
  → 读取名单（DataManager 解密 → 哈希校验 → 必要时三副本恢复）
  → 启动 Timer(50ms)：每 50ms 用 SecureRandom 取随机名刷新显示
点击“停止”
  → 停止 Timer，定格结果
  → LogManager.log(方案-模式=结果, "抽取结果")
```

### 6.3 数据保存流程

```
业务层（如 NameManager.saveNamesForScheme）
  ├─ 读取旧名单（before）
  ├─ DataManager.saveNamesFile：
  │    1. 明文 → EncryptionUtil.encryptData()：PBKDF2 派生 AES 密钥 → AES-GCM 加密（随机 12 字节 IV）
  │    2. 明文 → EncryptionUtil.calculateHash()：SHA-256
  │    3. 写入主副本 data/schemes/<方案>_names.txt（#ENCRYPTED_DATA + #HASH 分段格式）
  │    4. 同步写入 data/backup/ 备份副本与 log/ 伪装副本
  └─ ModificationLogManager.logChange：对比 before/after，
       将差异自动归类为 新增/删除/修改 并追加写入 modification_log.txt
```

### 6.4 数据损坏自恢复流程

```
读取主副本 → 格式/解密/哈希任一失败
  → recoverAndSyncData()：分别校验主副本、backup 副本、log 副本
  → 按 log → backup → data 优先级选出有效副本作为“母本”
  → 将其余副本同步为母本内容，实现三副本自动修复
  → 若全部无效 → 提示“名单已损坏，请重新导入”并清理损坏文件
```

---

## 7. 数据存储与安全设计

### 7.1 加密方案

- **对称加密**：`AES/GCM/NoPadding`（认证加密，防篡改），随机 12 字节 IV + 128 位 GCM Tag，每次加密 IV 均随机；
- **密钥派生**：`PBKDF2WithHmacSHA256`，以 `固定盐 + 方案名` 为密码、固定静态盐为盐值，迭代 100,000 次，派生 128 位 AES 密钥；
- **存储格式**：密文 Base64 编码，文件结构为：

```
#ENCRYPTED_DATA
<Base64(IV + 密文)>
#HASH
<Base64(SHA-256(明文))>
```

- **密码存储**：密码仅存 SHA-256 哈希（`#include<bits/stdc++.h>...` 默认密码亦不存明文），配置键为混淆键名 `x7f9a2b1c4e8d3f6`；
- **完整性**：解密后对明文重算 SHA-256 与文件内哈希比对，不一致即判定损坏并触发自动恢复；
- **冗余备份**：每份数据同时写入三处（主副本、`data/backup/`、`log/` 下的伪装文件名副本），任意一份损坏均可自动修复。

### 7.2 锁定机制

- 程序启动默认处于**锁定**状态，所有修改类操作（增删名单、导入导出、创建/删除方案、修改密码等）被拒绝并提示“请先解锁”；
- 在“设置 → 安全设置”中点击解锁并输入正确密码后进入解锁状态，也可手动重新锁定；
- 密码对话框中提供备用解锁通道（快速连续按 `Ctrl+L` 十次）。

> **初始默认密码**：
> `#include<bits/stdc++.h>usingnamespacestd;intmain(){return0;}`
> （首次运行由程序自动写入配置，建议在设置中立即修改。）

---

## 8. 编译与运行

### 8.1 命令行编译打包（推荐）

在项目根目录执行：

```bat
:: 编译（务必指定 UTF-8）
javac -encoding UTF-8 -d out src\*.java

:: 生成清单文件
echo Main-Class: Main> manifest.txt

:: 打包
jar cfm RandomNamePicker.jar manifest.txt -C out .

:: 运行
java -jar RandomNamePicker.jar
```

### 8.2 使用 package.bat（推荐）

`plugins/package.bat` 一键完成“编译 → 生成清单 → 打包 JAR → 生成 release 目录”，release 目录包含 `RandomNamePicker.jar`（主程序）、`示例名单.txt` 与 `README.md`。release 目录**仅保留主程序与名单文件即可运行**：`data/`、`log/`、`modification_log.txt` 等文件均由程序首次运行时自动生成。

### 8.3 自检

打包完成后运行 `plugins/selfcheck.bat`，会在系统临时目录中自动执行核心逻辑自检（名单修改日志、多人去重抽取、单次抽取数量配置），检查通过后自动清理临时文件。

### 8.4 使用 build.bat

项目根目录的 `build.bat` 完成同样的编译打包流程。脚本内 `cd /d %~dp0` 会自动切换到脚本所在目录，因此**双击即可运行**（需已配置 JDK 的 PATH 环境变量）。

### 8.5 打包为 EXE（可选）

项目 `.gitignore` 中包含 `launch4j.log`、`RandomNamePicker.xml` 等文件，说明曾使用 [Launch4j](http://launch4j.sourceforge.net/) 将 JAR 打包为 `RandomNamePicker.exe`（含内置 JRE）。开机自启动会自动定位当前主程序路径（优先代码源定位的 jar/exe，其次工作目录下的 `RandomNamePicker.exe`），并将其完整路径（含引号）写入注册表。

### 8.6 注意事项

- 所有类位于**默认包**，`Main-Class` 为 `Main`；
- 首次运行会在程序工作目录生成 `data/` 与 `log/` 目录，请确保有写入权限；
- 若编译时出现中文乱码，请确认已加 `-encoding UTF-8` 参数。

---

## 9. 使用说明

1. **启动程序**：主窗口顶部选择“预设方案”与“抽取模式”，中部为结果展示区；
2. **配置数据**：
   - 名字模式：点击“配置名单”打开名单窗口增删/导入导出；模式专属按钮“导入名单/导出名单”可快捷导入导出；
   - 数字模式：点击“设置数字范围”输入区间并保存；
   - 座位模式：点击“设置座位布局”生成网格、点选座位（黄色高亮）并保存；
3. **开始抽取**：点击“开始抽取”进入滚动状态，再次点击“停止”定格结果；
4. **方案管理**：点击“方案管理”创建（需先解锁）或删除方案；
5. **悬浮球**：点击“悬浮球”开启，双击抽取，右键打开快捷菜单，拖动改变位置；名单模式下若单次抽取数量大于 1，抽取结束后主球周围会弹出中奖者小球动画（点击动画区域关闭）；
6. **设置**：点击“设置”调整自启动、托盘策略、悬浮球外观、单次抽取数量，导出/查看日志，锁定/解锁与修改密码；
7. **托盘**：关闭主窗口时按设置最小化到托盘，右键托盘图标可恢复窗口或完全退出。

---

## 10. 配置项说明

配置文件：`data/config.properties`（键值对，自动创建，默认值如下）

| 键 | 含义 | 默认值 |
| --- | --- | --- |
| `autoStart` | 开机自启动 | `false` |
| `floatingBallRadius` | 悬浮球半径（像素） | `50` |
| `floatingBallOpacity` | 悬浮球透明度（0~255） | `200` |
| `multiBallOpacity` | 多人点名动画多球透明度（0~255） | `200` |
| `minimizeToTray` | 关闭窗口时最小化到托盘 | `true` |
| `pickCount` | 单次抽取数量（多人点名，1 ~ 当前名单总人数） | `1` |
| `lastScheme` | 上次使用的方案（自动记忆） | （空） |
| `x7f9a2b1c4e8d3f6` | 密码的 SHA-256 哈希（混淆键名） | 默认密码的哈希 |

---

## 11. 已知问题与改进方向

1. ~~**build.bat 路径问题**~~（已修复）：`build.bat` 现已移至项目根目录，脚本内 `cd /d %~dp0` 自动定位，双击即可运行；
2. **明文记忆风险**：密钥由“固定盐 + 方案名”派生，方案名公开可见，属于轻量级防窥探设计，不适合高安全等级场景（可改进为随机生成密钥并用主密钥加密保存）；
3. **锁定状态不持久化**：程序重启后始终回到锁定状态（运行期内存变量 `isUnlocked`），如需持久锁定策略可写入配置文件；
4. **冗余代码**：`EncryptionUtil` 中的 RSA 加解密方法与 `ChangePasswordDialog` 未被主流程使用（设置窗口改密走 `OldPasswordDialog` + `NewPasswordDialog` 流程），可清理；
5. **抽取公平性**：当前每次抽取相互独立，同一目标可能被连续抽中；多人点名已支持去重，如需“历史不重复抽取”可引入已抽取集合与去重逻辑；
6. **界面**：主窗口中部结果区功能较简单，可扩展为抽奖转盘、历史结果列表等更丰富的可视化效果。
7. **多人动画**：中奖者小球姓名超过 6 字时以省略号展示（与单人模式截断逻辑一致），动画窗口点击或 15 秒后自动关闭。
8. **插件权限**：插件与主程序运行在同一 JVM、共享全部权限，请仅加载可信来源的插件。

---

## 12. 插件开发指南

### 12.1 快速上手

1. 新建 Java 类，实现 `Plugin` 接口（`getName` / `getVersion` / `getDescription` / `onLoad` / `onUnload`）；
2. 在 `onLoad(PluginContext context)` 中通过 `context` 注册功能；
3. 打包 JAR 时在清单文件（manifest）中加入一行 `Plugin-Class: 你的类全名`；
4. 把 JAR 放入程序运行目录下的 `extensions/` 文件夹，启动程序即自动加载。

参考 `examples/ExamplePlugin/`（源码 + `build.bat` 一键构建）。

### 12.2 标准扩展点（PluginContext）

| 方法 | 作用 |
| --- | --- |
| `registerMainMenuItem(text, action)` | 主窗口“插件”菜单项 |
| `registerMainButton(text, action)` | 主窗口底部插件按钮 |
| `registerTrayMenuItem(text, action)` | 系统托盘菜单项 |
| `registerFloatingBallMenuItem(text, action)` | 悬浮球右键菜单项 |
| `registerSettingsPanel(title, panel)` | 设置窗口插件面板 |
| `getNameManager()` / `getSchemeManager()` | 访问名单/方案服务 |
| `getCurrentScheme()` / `getCurrentMode()` | 读取当前上下文 |

### 12.3 UI 篡改接口

| 方法 | 作用 |
| --- | --- |
| `setMainDisplayText(text)` | 修改主窗口中央显示文本 |
| `registerMainComponent(component)` | 向主窗口底部注入任意 Swing 组件 |
| `addMainMenu(menu)` | 向主窗口菜单栏注入自定义菜单 |
| `getMainApp()` | 获取主窗口实例，自由改造 UI 与调用服务（高级入口） |

### 12.4 注意事项

- 插件运行在 Swing 事件线程与主程序同一 JVM 中，异常会被捕获并写入 `log/Modifylog.txt`，不影响主程序；
- 卸载（`onUnload`）在程序退出时自动调用，用于释放插件资源；
- 每个插件使用独立 `URLClassLoader` 加载，插件间互不干扰；
- 请勿在主程序目录放置无关 JAR（`extensions/` 下所有 `.jar` 都会被尝试加载）。

---

*本项目为 Java 课程设计/桌面应用实践作品，欢迎在此基础上继续完善。*

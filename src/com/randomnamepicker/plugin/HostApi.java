package com.randomnamepicker.plugin;

/**
 * 宿主公开 API 面与版本/级别常量（插件体系二次开发一期，API 级别 1→2）。
 * <p>
 * <b>受支持公开 API 面（V1.1.0 基线 + 本期，插件可依赖）</b>：
 * <ul>
 *   <li>{@code plugin} 包全部（{@link Plugin}/{@link PluginContext}/{@link PluginManager} 只读查询/
 *       {@link UiZone}/{@link HostEvent}/{@link HostEventListener}/{@link HostApi}）；</li>
 *   <li>{@code mode} 包 {@link com.randomnamepicker.mode.ModeHandler}/{@link com.randomnamepicker.mode.ModeHost}
 *       （含本期新增只读几何查询与 default 上下文菜单方法）；</li>
 *   <li>{@code model} 只读类（Scheme/NumberRange/SeatConfig 只读用法）；</li>
 *   <li>{@link PluginContext} 提供句柄的 Manager（NameManager/SchemeManager/DataManager/LogManager）的方法子集。</li>
 * </ul>
 * 其余（{@code PasswordManager}/{@code ConfigManager}/{@code FloatingBall}/{@code NamePickerApp}，
 * {@code ui/floating/theme/main} 各包、{@code core} 静态入口）为<b>宿主内部、不承诺兼容</b>；
 * 第三方插件依赖它们即脱离公开 API（使用即自行承担宿主升级风险）。
 * </p>
 * <p>
 * <b>API 级别增量纪律（D8b）</b>：级别按“交付单元”判定一次——含插件可见公开面变更（新增/删除/改名公开
 * 方法、事件类型、UiZone 值、只读查询、公开类；破坏性/语义契约变更另配 ≥1 版本弃用期 + 破坏性清单）即 +1，
 * 同一交付单元最多 +1；纯内部改动（默认密码、加密内部算法、托盘图标、主题内部实现等）不涨。插件经 jar
 * MANIFEST.MF 声明 {@code Api-Level-Min}（可选，不声明=永远尝试加载，无 Max 上限）；宿主门控 =
 * {@link #PLUGIN_API_LEVEL} ≥ 声明值，不满足则整 jar 拒载（VERSION_MISMATCH）。
 * </p>
 */
public final class HostApi {

    private HostApi() {
    }

    /**
     * 产品发布号（x.y.z）。仅用于 UI/日志/文档/打包命名，<b>不参与插件兼容门控</b>（门控只认
     * {@link #PLUGIN_API_LEVEL}）。交付发布新版本时按仓库发布流程同步更新本常量与 README/manifest/release 文案。
     */
    public static final String HOST_VERSION = "1.1.0";

    /**
     * 插件 API 级别（唯一兼容门控）。基线：V1.1.0 既有公开面 = 1；本期（宿主插件生态一期）新增
     * 事件订阅/HostEvent 九类/isHostLocked/ModeHost 几何查询/ModeHandler 右键口子/状态查询等公开 API → 2。
     */
    public static final int PLUGIN_API_LEVEL = 2;
}

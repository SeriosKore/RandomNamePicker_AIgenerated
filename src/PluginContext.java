import javax.swing.*;

/**
 * 插件上下文：插件与主程序交互的唯一入口。
 * 提供标准扩展点（菜单/按钮/托盘/悬浮球/设置面板），
 * 以及“UI 篡改接口”——插件可以据此修改主界面，而无需直接反射或修改主程序源码。
 */
public interface PluginContext {

    // ---------- 标准扩展点 ----------

    /** 在主窗口“插件”菜单下注册一个菜单项。 */
    void registerMainMenuItem(String text, Runnable action);

    /** 在主窗口底部插件按钮区注册一个按钮。 */
    void registerMainButton(String text, Runnable action);

    /** 在系统托盘菜单注册一个菜单项（需系统支持托盘）。 */
    void registerTrayMenuItem(String text, Runnable action);

    /** 在悬浮球右键菜单注册一个菜单项。 */
    void registerFloatingBallMenuItem(String text, Runnable action);

    /** 在设置窗口注册一个插件设置面板（以标题分组展示）。 */
    void registerSettingsPanel(String title, JComponent panel);

    // ---------- UI 篡改接口 ----------

    /** 直接修改主窗口中央显示文本。 */
    void setMainDisplayText(String text);

    /** 向主窗口底部插件按钮区注入任意 Swing 组件。 */
    void registerMainComponent(JComponent component);

    /** 向主窗口菜单栏注入自定义菜单。 */
    void addMainMenu(JMenu menu);

    // ---------- 服务与数据访问 ----------

    /** 获取主窗口实例（高级“篡改”入口：插件可在此基础上自由改造 UI）。 */
    NamePickerApp getMainApp();

    /** 获取名单管理服务。 */
    NameManager getNameManager();

    /** 获取方案管理服务。 */
    SchemeManager getSchemeManager();

    /** 获取当前选中的方案。 */
    Scheme getCurrentScheme();

    /** 获取当前抽取模式（名字列表模式/数字模式/座位模式）。 */
    String getCurrentMode();
}

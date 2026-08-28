/**
 * 插件接口。
 * 插件实现类需提供无参构造方法，并打包为 JAR（清单文件需包含
 * 形如 "Plugin-Class: 完整类名" 的属性），放入程序运行目录下的
 * extensions/ 文件夹即会被自动加载。
 */
public interface Plugin {

    /** 插件名称（用于界面展示与日志）。 */
    String getName();

    /** 插件版本号。 */
    String getVersion();

    /** 插件功能描述。 */
    String getDescription();

    /**
     * 插件被加载时调用。可通过 context 注册各扩展点，
     * 或通过 UI 篡改接口修改主界面。任何异常都会被主程序捕获并记录日志，
     * 不影响其他插件与主程序运行。
     */
    void onLoad(PluginContext context);

    /** 插件被卸载（程序退出）时调用，用于释放资源。 */
    void onUnload();
}

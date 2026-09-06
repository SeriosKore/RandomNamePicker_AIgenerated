package com.randomnamepicker.plugin;

/**
 * 插件接口（Stage3）。
 * <p>
 * 插件以独立 jar 形式部署到运行目录 plugins/ 下，由 PluginManager 加载。
 * onLoad 在后台加载线程中调用，注册类操作只写入本插件的暂存区（提交后生效）；
 * onUnload 在程序退出（Main.cleanupAndExit → PluginManager.shutdown）时调用。
 * </p>
 */
public interface Plugin {

    String getName();

    String getVersion();

    void onLoad(PluginContext context);

    void onUnload();
}

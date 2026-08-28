import javax.swing.*;

/**
 * 插件上下文实现：把插件的注册请求写入 PluginManager 注册表。
 * （包级可见，仅主程序内部使用。）
 */
class PluginContextImpl implements PluginContext {
    private NamePickerApp app;
    private PluginManager manager;

    PluginContextImpl(NamePickerApp app, PluginManager manager) {
        this.app = app;
        this.manager = manager;
    }

    @Override
    public void registerMainMenuItem(String text, Runnable action) {
        manager.addMainMenuItem(text, action);
    }

    @Override
    public void registerMainButton(String text, Runnable action) {
        manager.addMainButton(text, action);
    }

    @Override
    public void registerTrayMenuItem(String text, Runnable action) {
        manager.addTrayMenuItem(text, action);
    }

    @Override
    public void registerFloatingBallMenuItem(String text, Runnable action) {
        manager.addFloatingBallMenuItem(text, action);
    }

    @Override
    public void registerSettingsPanel(String title, JComponent panel) {
        manager.addSettingsPanel(title, panel);
    }

    @Override
    public void setMainDisplayText(String text) {
        app.setDisplayLabelText(text);
    }

    @Override
    public void registerMainComponent(JComponent component) {
        manager.addMainComponent(component);
    }

    @Override
    public void addMainMenu(JMenu menu) {
        manager.addExtraMenu(menu);
    }

    @Override
    public NamePickerApp getMainApp() {
        return app;
    }

    @Override
    public NameManager getNameManager() {
        return app.getNameManager();
    }

    @Override
    public SchemeManager getSchemeManager() {
        return app.getSchemeManager();
    }

    @Override
    public Scheme getCurrentScheme() {
        return app.getCurrentScheme();
    }

    @Override
    public String getCurrentMode() {
        return app.getCurrentMode();
    }
}

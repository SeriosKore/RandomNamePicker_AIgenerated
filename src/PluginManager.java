import javax.swing.*;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * 插件管理器：扫描运行目录下 extensions/*.jar，
 * 依据 JAR 清单中的 "Plugin-Class" 属性加载插件（不修改主程序即可扩展功能）。
 * 插件与主程序运行在同一 JVM 中，享有完整访问权限，加载失败会被隔离并记录日志。
 */
public class PluginManager {

    /** 插件存放目录（相对程序运行目录）。 */
    public static final String PLUGIN_DIR = "extensions";

    /** 主菜单/按钮/托盘/悬浮球菜单项的描述。 */
    public static class MenuItemSpec {
        public String text;
        public Runnable action;

        public MenuItemSpec(String text, Runnable action) {
            this.text = text;
            this.action = action;
        }
    }

    /** 设置面板的描述。 */
    public static class SettingsPanelSpec {
        public String title;
        public JComponent panel;

        public SettingsPanelSpec(String title, JComponent panel) {
            this.title = title;
            this.panel = panel;
        }
    }

    private static class PluginHolder {
        Plugin plugin;
        URLClassLoader classLoader;
        File jarFile;
    }

    private NamePickerApp mainApp;
    private List<PluginHolder> holders = new ArrayList<>();
    private List<MenuItemSpec> mainMenuItems = new ArrayList<>();
    private List<MenuItemSpec> mainButtonItems = new ArrayList<>();
    private List<MenuItemSpec> trayItems = new ArrayList<>();
    private List<MenuItemSpec> floatingBallItems = new ArrayList<>();
    private List<SettingsPanelSpec> settingsPanels = new ArrayList<>();
    private List<JComponent> mainComponents = new ArrayList<>();
    private List<JMenu> extraMenus = new ArrayList<>();
    private List<FloatingBallPickHandler> floatingBallPickHandlers = new ArrayList<>();

    public PluginManager(NamePickerApp mainApp) {
        this.mainApp = mainApp;
    }

    /**
     * 扫描并加载 extensions/ 目录下的全部插件。
     */
    public void loadPlugins() {
        File dir = new File(PLUGIN_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File[] jars = dir.listFiles((d, name) -> name != null && name.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            LogManager.log("extensions/ 目录下没有插件", "PLUGIN_NONE");
            return;
        }
        for (File jarFile : jars) {
            loadPluginJar(jarFile);
        }
        LogManager.log("插件加载完成，共成功加载 " + holders.size() + " 个", "PLUGIN_LOAD_DONE");
    }

    private void loadPluginJar(File jarFile) {
        String jarName = jarFile.getName();
        try {
            String className = null;
            try (JarFile jar = new JarFile(jarFile)) {
                Manifest manifest = jar.getManifest();
                if (manifest != null) {
                    className = manifest.getMainAttributes().getValue("Plugin-Class");
                }
            }
            if (className == null || className.trim().isEmpty()) {
                LogManager.log("插件 " + jarName + " 缺少清单属性 Plugin-Class，已跳过", "PLUGIN_SKIP");
                return;
            }
            className = className.trim();

            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jarFile.toURI().toURL()}, getClass().getClassLoader());
            Class<?> clazz = Class.forName(className, true, loader);
            Object instance = clazz.newInstance();
            if (!(instance instanceof Plugin)) {
                LogManager.log("插件 " + jarName + " 的 Plugin-Class 未实现 Plugin 接口，已跳过", "PLUGIN_SKIP");
                loader.close();
                return;
            }

            Plugin plugin = (Plugin) instance;
            PluginContext context = new PluginContextImpl(mainApp, this);
            plugin.onLoad(context);

            PluginHolder holder = new PluginHolder();
            holder.plugin = plugin;
            holder.classLoader = loader;
            holder.jarFile = jarFile;
            holders.add(holder);

            LogManager.log("插件已加载: " + plugin.getName() + " v" + plugin.getVersion()
                    + "（" + jarName + "）", "PLUGIN_LOADED");
        } catch (Throwable t) {
            LogManager.log("插件加载失败: " + jarName + " - " + t, "PLUGIN_ERROR");
        }
    }

    /**
     * 卸载全部插件（程序退出前调用）。
     */
    public void unloadAll() {
        for (int i = holders.size() - 1; i >= 0; i--) {
            PluginHolder holder = holders.get(i);
            try {
                holder.plugin.onUnload();
            } catch (Throwable t) {
                LogManager.log("插件卸载异常: " + holder.jarFile.getName() + " - " + t, "PLUGIN_UNLOAD_ERROR");
            }
            try {
                holder.classLoader.close();
            } catch (Exception e) {
                // 忽略类加载器关闭异常
            }
        }
        holders.clear();
    }

    /** 已加载插件数量。 */
    public int getLoadedPluginCount() {
        return holders.size();
    }

    // ---------- 各扩展点注册（由 PluginContextImpl 调用） ----------

    public void addMainMenuItem(String text, Runnable action) {
        mainMenuItems.add(new MenuItemSpec(text, action));
    }

    public void addMainButton(String text, Runnable action) {
        mainButtonItems.add(new MenuItemSpec(text, action));
    }

    public void addTrayMenuItem(String text, Runnable action) {
        trayItems.add(new MenuItemSpec(text, action));
    }

    public void addFloatingBallMenuItem(String text, Runnable action) {
        floatingBallItems.add(new MenuItemSpec(text, action));
    }

    public void addSettingsPanel(String title, JComponent panel) {
        settingsPanels.add(new SettingsPanelSpec(title, panel));
    }

    public void addMainComponent(JComponent component) {
        mainComponents.add(component);
    }

    public void addExtraMenu(JMenu menu) {
        extraMenus.add(menu);
    }

    /**
     * 注册悬浮球名字抽取拦截器（插件接管抽取的扩展点）。
     */
    public void addFloatingBallPickHandler(FloatingBallPickHandler handler) {
        floatingBallPickHandlers.add(handler);
    }

    public List<FloatingBallPickHandler> getFloatingBallPickHandlers() {
        return new ArrayList<>(floatingBallPickHandlers);
    }

    public List<MenuItemSpec> getMainMenuItemSpecs() {
        return new ArrayList<>(mainMenuItems);
    }

    public List<MenuItemSpec> getMainButtonSpecs() {
        return new ArrayList<>(mainButtonItems);
    }

    public List<MenuItemSpec> getTrayMenuItemSpecs() {
        return new ArrayList<>(trayItems);
    }

    public List<MenuItemSpec> getFloatingBallMenuItemSpecs() {
        return new ArrayList<>(floatingBallItems);
    }

    public List<SettingsPanelSpec> getSettingsPanelSpecs() {
        return new ArrayList<>(settingsPanels);
    }

    public List<JComponent> getMainComponents() {
        return new ArrayList<>(mainComponents);
    }

    public List<JMenu> getExtraMenus() {
        return new ArrayList<>(extraMenus);
    }
}

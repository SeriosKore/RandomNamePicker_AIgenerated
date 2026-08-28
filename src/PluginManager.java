import javax.swing.*;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * 插件管理器：扫描运行目录下 extensions/*.jar，
 * 依据 JAR 清单中的 "Plugin-Class" 属性加载插件（不修改主程序即可扩展功能）。
 * 支持：查看插件信息、启用/禁用（持久化到配置）、卸载（删除 JAR）、安装（复制 JAR）。
 * 插件与主程序运行在同一 JVM 中，享有完整访问权限，加载失败会被隔离并记录日志。
 */
public class PluginManager {

    /** 插件存放目录（相对程序运行目录）。 */
    public static final String PLUGIN_DIR = "extensions";

    /** 已知插件标识（Plugin-Class 值）与中文名称。 */
    public static final String PLUGIN_ID_MULTI_BALL = "MultiBallPlugin";
    public static final String PLUGIN_ID_PEN = "PenPlugin";

    public static final Map<String, String> KNOWN_PLUGIN_NAMES = new LinkedHashMap<>();
    static {
        KNOWN_PLUGIN_NAMES.put(PLUGIN_ID_MULTI_BALL, "多悬浮球插件(示例插件)");
        KNOWN_PLUGIN_NAMES.put(PLUGIN_ID_PEN, "屏幕画笔插件");
    }

    /** 主菜单/按钮/托盘/悬浮球菜单项的描述（含所属插件，便于禁用时清理）。 */
    public static class MenuItemSpec {
        public String text;
        public Runnable action;
        public String owner;

        public MenuItemSpec(String text, Runnable action, String owner) {
            this.text = text;
            this.action = action;
            this.owner = owner;
        }
    }

    /** 设置面板的描述。 */
    public static class SettingsPanelSpec {
        public String title;
        public JComponent panel;
        public String owner;

        public SettingsPanelSpec(String title, JComponent panel, String owner) {
            this.title = title;
            this.panel = panel;
            this.owner = owner;
        }
    }

    /** 带归属的组件/菜单/抽取拦截器。 */
    public static class OwnedComponent {
        public String owner;
        public JComponent component;

        public OwnedComponent(String owner, JComponent component) {
            this.owner = owner;
            this.component = component;
        }
    }

    public static class OwnedMenu {
        public String owner;
        public JMenu menu;

        public OwnedMenu(String owner, JMenu menu) {
            this.owner = owner;
            this.menu = menu;
        }
    }

    public static class OwnedPickHandler {
        public String owner;
        public FloatingBallPickHandler handler;

        public OwnedPickHandler(String owner, FloatingBallPickHandler handler) {
            this.owner = owner;
            this.handler = handler;
        }
    }

    /** 插件信息（管理界面用）。 */
    public static class PluginInfo {
        public String id;        // Plugin-Class 值
        public String jarName;   // 插件 JAR 文件名（未安装为 null）
        public String name;      // 显示名称
        public String version;   // 版本号（未加载为 "-"）
        public boolean installed;
        public boolean enabled;
        public boolean loaded;
    }

    private static class PluginHolder {
        String id;
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
    private List<OwnedComponent> mainComponents = new ArrayList<>();
    private List<OwnedMenu> extraMenus = new ArrayList<>();
    private List<OwnedPickHandler> floatingBallPickHandlers = new ArrayList<>();

    /** 当前正在加载的插件（用于给注册项打归属标记）。 */
    private String loadingOwner;

    public PluginManager(NamePickerApp mainApp) {
        this.mainApp = mainApp;
    }

    /**
     * 扫描并加载 extensions/ 目录下的全部“已启用”插件。
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
            String id = readPluginId(jarFile);
            if (id == null) {
                LogManager.log("插件 " + jarFile.getName() + " 缺少清单属性 Plugin-Class，已跳过", "PLUGIN_SKIP");
                continue;
            }
            if (!ConfigManager.isPluginEnabled(id)) {
                LogManager.log("插件已禁用，跳过加载: " + id + "（" + jarFile.getName() + "）", "PLUGIN_DISABLED");
                continue;
            }
            loadPluginJar(jarFile);
        }
        LogManager.log("插件加载完成，共成功加载 " + holders.size() + " 个", "PLUGIN_LOAD_DONE");
    }

    /** 读取 JAR 的 Plugin-Class 属性。 */
    private String readPluginId(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String className = manifest.getMainAttributes().getValue("Plugin-Class");
                if (className != null && !className.trim().isEmpty()) {
                    return className.trim();
                }
            }
        } catch (Exception e) {
            // 读取失败按无标识处理
        }
        return null;
    }

    private void loadPluginJar(File jarFile) {
        String jarName = jarFile.getName();
        try {
            String className = readPluginId(jarFile);
            if (className == null) {
                LogManager.log("插件 " + jarName + " 缺少清单属性 Plugin-Class，已跳过", "PLUGIN_SKIP");
                return;
            }
            if (findHolder(className) != null) {
                LogManager.log("插件已加载，跳过重复加载: " + className, "PLUGIN_SKIP");
                return;
            }

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
            loadingOwner = className;
            try {
                plugin.onLoad(context);
            } finally {
                loadingOwner = null;
            }

            PluginHolder holder = new PluginHolder();
            holder.id = className;
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

    private PluginHolder findHolder(String id) {
        for (PluginHolder holder : holders) {
            if (holder.id.equals(id)) {
                return holder;
            }
        }
        return null;
    }

    /** 根据插件标识查找 extensions 目录下的 JAR。 */
    private File findJarFor(String id) {
        File dir = new File(PLUGIN_DIR);
        File[] jars = dir.listFiles((d, name) -> name != null && name.toLowerCase().endsWith(".jar"));
        if (jars == null) {
            return null;
        }
        for (File jar : jars) {
            if (id.equals(readPluginId(jar))) {
                return jar;
            }
        }
        return null;
    }

    /** 移除某插件的全部注册项。 */
    private void removePluginEntries(String owner) {
        mainMenuItems.removeIf(s -> owner.equals(s.owner));
        mainButtonItems.removeIf(s -> owner.equals(s.owner));
        trayItems.removeIf(s -> owner.equals(s.owner));
        floatingBallItems.removeIf(s -> owner.equals(s.owner));
        settingsPanels.removeIf(s -> owner.equals(s.owner));
        mainComponents.removeIf(s -> owner.equals(s.owner));
        extraMenus.removeIf(s -> owner.equals(s.owner));
        floatingBallPickHandlers.removeIf(s -> owner.equals(s.owner));
    }

    /** 卸载单个插件实例（不删除文件，不改配置）。 */
    private void unloadHolder(PluginHolder holder) {
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
        holders.remove(holder);
        removePluginEntries(holder.id);
    }

    /**
     * 卸载全部插件（程序退出前调用）。
     */
    public void unloadAll() {
        for (int i = holders.size() - 1; i >= 0; i--) {
            unloadHolder(holders.get(i));
        }
    }

    /** 已加载插件数量。 */
    public int getLoadedPluginCount() {
        return holders.size();
    }

    /**
     * 插件是否已安装且启用。
     */
    public boolean isPluginEnabled(String id) {
        return findJarFor(id) != null && ConfigManager.isPluginEnabled(id);
    }

    /**
     * 启用插件（立即加载，若已安装）。
     */
    public boolean enablePlugin(String id) {
        ConfigManager.setPluginEnabled(id, true);
        File jar = findJarFor(id);
        if (jar == null) {
            return false;
        }
        if (findHolder(id) == null) {
            loadPluginJar(jar);
        }
        LogManager.log("插件已启用: " + id, "PLUGIN_ENABLED");
        return true;
    }

    /**
     * 禁用插件（立即卸载并清理注册项）。
     */
    public boolean disablePlugin(String id) {
        ConfigManager.setPluginEnabled(id, false);
        PluginHolder holder = findHolder(id);
        if (holder != null) {
            unloadHolder(holder);
        }
        LogManager.log("插件已禁用: " + id, "PLUGIN_DISABLED");
        return true;
    }

    /**
     * 卸载插件：禁用并删除 extensions/ 下的 JAR 文件。
     */
    public boolean uninstallPlugin(String id) {
        disablePlugin(id);
        File jar = findJarFor(id);
        if (jar != null && jar.delete()) {
            LogManager.log("插件已卸载（文件已删除）: " + id, "PLUGIN_UNINSTALLED");
            return true;
        }
        LogManager.log("插件卸载时删除文件失败: " + id, "PLUGIN_UNINSTALL_ERROR");
        return false;
    }

    /**
     * 安装插件：把 JAR 复制到 extensions/ 并按配置状态加载。
     * 返回 null 表示成功，否则返回错误信息。
     */
    public String installPlugin(File sourceJar) {
        try {
            String id = readPluginId(sourceJar);
            if (id == null) {
                return "所选文件不是有效插件（缺少 Plugin-Class 清单属性）";
            }
            File dir = new File(PLUGIN_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File dest = new File(dir, sourceJar.getName());
            Files.copy(sourceJar.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // 若已有同标识插件在运行，先卸载旧实例再加载新文件
            PluginHolder old = findHolder(id);
            if (old != null) {
                unloadHolder(old);
            }
            // 安装即启用
            ConfigManager.setPluginEnabled(id, true);
            loadPluginJar(dest);
            LogManager.log("插件已安装: " + id + "（" + dest.getName() + "）", "PLUGIN_INSTALLED");
            return null;
        } catch (Exception e) {
            LogManager.log("插件安装失败: " + sourceJar.getName() + " - " + e, "PLUGIN_INSTALL_ERROR");
            return "插件安装失败：" + e.getMessage();
        }
    }

    /**
     * 获取全部插件信息（已知插件 + extensions 目录中发现的插件）。
     */
    public List<PluginInfo> getPluginInfos() {
        Map<String, PluginInfo> map = new LinkedHashMap<>();

        // 已知插件占位（未安装状态）
        for (Map.Entry<String, String> entry : KNOWN_PLUGIN_NAMES.entrySet()) {
            PluginInfo info = new PluginInfo();
            info.id = entry.getKey();
            info.name = entry.getValue();
            info.installed = false;
            info.enabled = true;
            info.loaded = false;
            info.version = "-";
            map.put(info.id, info);
        }

        // extensions 目录中实际存在的插件
        File dir = new File(PLUGIN_DIR);
        File[] jars = dir.listFiles((d, name) -> name != null && name.toLowerCase().endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                String id = readPluginId(jar);
                if (id == null) {
                    continue;
                }
                PluginInfo info = map.get(id);
                if (info == null) {
                    info = new PluginInfo();
                    info.id = id;
                    info.name = id;
                }
                info.installed = true;
                info.jarName = jar.getName();
                info.enabled = ConfigManager.isPluginEnabled(id);
                info.loaded = findHolder(id) != null;
                PluginHolder holder = findHolder(id);
                if (holder != null) {
                    info.name = holder.plugin.getName();
                    info.version = holder.plugin.getVersion();
                } else {
                    info.version = "-";
                }
                map.put(id, info);
            }
        }

        return new ArrayList<>(map.values());
    }

    // ---------- 各扩展点注册（由 PluginContextImpl 调用，自动打归属标记） ----------

    public void addMainMenuItem(String text, Runnable action) {
        mainMenuItems.add(new MenuItemSpec(text, action, loadingOwner));
    }

    public void addMainButton(String text, Runnable action) {
        mainButtonItems.add(new MenuItemSpec(text, action, loadingOwner));
    }

    public void addTrayMenuItem(String text, Runnable action) {
        trayItems.add(new MenuItemSpec(text, action, loadingOwner));
    }

    public void addFloatingBallMenuItem(String text, Runnable action) {
        floatingBallItems.add(new MenuItemSpec(text, action, loadingOwner));
    }

    public void addSettingsPanel(String title, JComponent panel) {
        settingsPanels.add(new SettingsPanelSpec(title, panel, loadingOwner));
    }

    public void addMainComponent(JComponent component) {
        mainComponents.add(new OwnedComponent(loadingOwner, component));
    }

    public void addExtraMenu(JMenu menu) {
        extraMenus.add(new OwnedMenu(loadingOwner, menu));
    }

    public void addFloatingBallPickHandler(FloatingBallPickHandler handler) {
        floatingBallPickHandlers.add(new OwnedPickHandler(loadingOwner, handler));
    }

    public List<FloatingBallPickHandler> getFloatingBallPickHandlers() {
        List<FloatingBallPickHandler> result = new ArrayList<>();
        for (OwnedPickHandler entry : floatingBallPickHandlers) {
            result.add(entry.handler);
        }
        return result;
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
        List<JComponent> result = new ArrayList<>();
        for (OwnedComponent entry : mainComponents) {
            result.add(entry.component);
        }
        return result;
    }

    public List<JMenu> getExtraMenus() {
        List<JMenu> result = new ArrayList<>();
        for (OwnedMenu entry : extraMenus) {
            result.add(entry.menu);
        }
        return result;
    }
}

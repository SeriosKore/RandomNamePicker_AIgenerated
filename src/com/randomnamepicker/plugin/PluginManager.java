package com.randomnamepicker.plugin;

import com.randomnamepicker.core.DataManager;
import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.core.NameManager;
import com.randomnamepicker.core.SchemeManager;
import com.randomnamepicker.mode.ModeHandler;
import com.randomnamepicker.mode.ModeHost;
import com.randomnamepicker.mode.ModeRegistry;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.SwingUtilities;

/**
 * 插件管理器（Stage3，进程内单例）。
 * <p>
 * 加载模型（后台线程 + EDT 刷新）：
 * 1. 扫描运行目录 plugins/（不存在则创建），按文件名排序逐个处理 *.jar；
 * 2. 每个 jar 为一个加载/提交事务单元（D0.9/D0.10）：
 *    - 新建 parent-first URLClassLoader（parent=宿主类加载器）；
 *    - 遍历全部 .class 条目（跳过 module-info.class 与 META-INF/versions/），
 *      任一条目加载失败 → 整 jar 拒载；
 *    - 收集 Plugin 实现类（非抽象、public、无参构造），逐个实例化并 onLoad(ctx)，
 *      注册动作只进该插件暂存区（D0.11）；任一失败 → 整 jar 拒载；
 *    - 全部成功后在提交阶段做冲突预检（modeId/displayName 撞内置、撞更早提交插件、
 *      同 jar 候选互撞均判冲突），通过才整体生效；任一冲突 → 整 jar 原子拒载，
 *      已 onLoad 的候选补 onUnload，无 UI/注册表残留；
 * 3. 有插件提交成功时，调度 EDT 执行一次 UI 刷新监听器（主窗菜单/下拉框、托盘重建）。
 * </p>
 * <p>
 * 退出：shutdown() 对已提交插件逐个 onUnload（try/catch）并关闭 URLClassLoader。
 * 运行期间不重扫/不热插拔；菜单项标题不判重；内置模式/菜单无任何可移除路径。
 * </p>
 */
public final class PluginManager {

    private static final PluginManager INSTANCE = new PluginManager();
    private static final String PLUGIN_DIR = "plugins";

    public static PluginManager getInstance() {
        return INSTANCE;
    }

    /** 只读插件信息（名称/版本）。 */
    public static final class PluginInfo {
        private final String name;
        private final String version;

        PluginInfo(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }
    }

    /** 菜单项（title 不判重；pluginName 用于出错日志归属）。 */
    public static final class MenuAction {
        private final String title;
        private final String pluginName;
        private final ActionListener action;

        MenuAction(String title, String pluginName, ActionListener action) {
            this.title = title;
            this.pluginName = pluginName;
            this.action = action;
        }

        public String getTitle() {
            return title;
        }

        public String getPluginName() {
            return pluginName;
        }

        public ActionListener getAction() {
            return action;
        }
    }

    /** 已提交加载结果（一个 Plugin 实现类一条；同一 jar 的多条共享同一 loader）。 */
    private static final class LoadedPlugin {
        final Plugin plugin;
        final String name;
        final String version;
        final URLClassLoader loader;
        final List<ModeHandler> modes;
        /** 按 Zone 组织的已提交动作（各列表均只读）；顺序 = 提交时注册序。 */
        final Map<UiZone, List<MenuAction>> actions;

        LoadedPlugin(Plugin plugin, URLClassLoader loader, List<ModeHandler> modes,
                     Map<UiZone, List<MenuAction>> actions) {
            this.plugin = plugin;
            this.name = plugin.getName();
            this.version = plugin.getVersion();
            this.loader = loader;
            this.modes = Collections.unmodifiableList(new ArrayList<>(modes));
            Map<UiZone, List<MenuAction>> copy = new EnumMap<>(UiZone.class);
            for (Map.Entry<UiZone, List<MenuAction>> e : actions.entrySet()) {
                copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
            }
            this.actions = Collections.unmodifiableMap(copy);
        }
    }

    private static final class Pair {
        final Plugin plugin;
        final PerPluginContext ctx;

        Pair(Plugin plugin, PerPluginContext ctx) {
            this.plugin = plugin;
            this.ctx = ctx;
        }
    }

    private static final class PluginLoadException extends Exception {
        PluginLoadException(String message) {
            super(message);
        }

        PluginLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 单插件上下文实现：注册动作写入暂存区；服务访问委托宿主。 */
    private final class PerPluginContext implements PluginContext {
        private final Plugin plugin;
        private final List<ModeHandler> pendingModes = new ArrayList<>();
        private final Map<UiZone, List<MenuAction>> pendingActions = new EnumMap<>(UiZone.class);

        PerPluginContext(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public synchronized void registerModeHandler(ModeHandler handler) {
            if (handler != null) {
                pendingModes.add(handler);
            }
        }

        @Override
        public synchronized void addUiAction(UiZone zone, String title, ActionListener action) {
            // D8 严格失败语义：zone 为 null = 插件缺陷 → 抛异常（宿主整 jar 拒载并记 PLUGIN_LOAD_ERROR）
            if (zone == null) {
                throw new IllegalArgumentException("插件 " + plugin.getName()
                        + " 调用 addUiAction 时 zone 为 null（UI 槽一期仅开放 SETTINGS_WINDOW）");
            }
            if (title == null || title.trim().isEmpty() || action == null) {
                return; // 与既有菜单注册一致：空 title/action 忽略
            }
            pendingActions.computeIfAbsent(zone, z -> new ArrayList<>())
                    .add(new MenuAction(title, plugin.getName(), action));
        }

        @Override
        public NameManager getNameManager() {
            return host().getNameManager();
        }

        @Override
        public SchemeManager getSchemeManager() {
            return host().getSchemeManager();
        }

        @Override
        public DataManager getDataManager() {
            return host().getDataManager();
        }

        @Override
        public LogManager getLogManager() {
            return host().getLogManager();
        }

        @Override
        public ModeHost getModeHost() {
            return host();
        }

        @Override
        public void log(String detail, String opCode) {
            LogManager.log(detail, opCode);
        }
    }

    private final List<Runnable> uiRefreshListeners = new ArrayList<>();
    private final List<LoadedPlugin> committed = new ArrayList<>();
    private final Map<String, String> committedModeIds = new HashMap<>();
    private final Map<String, String> committedModeNames = new HashMap<>();
    private ModeHost host;
    private boolean inited = false;
    private boolean stopped = false;
    private boolean loading = false;

    private PluginManager() {
    }

    // ===================== 生命周期 =====================

    /** 绑定宿主（NamePickerApp 即 ModeHost）。仅需在 loadAll 前调用一次。 */
    public synchronized void init(ModeHost host) {
        this.host = host;
        this.inited = true;
    }

    public synchronized boolean isInited() {
        return inited;
    }

    public synchronized boolean isLoading() {
        return loading;
    }

    /** 后台线程加载（生产路径：Main 启动时调用）；完成后 EDT 刷新一次 UI。 */
    public void loadAllAsync() {
        if (!isReadyToLoad()) {
            return;
        }
        setLoading(true);
        Thread worker = new Thread(this::loadAndCommitAll, "plugin-loader");
        worker.setDaemon(false);
        worker.start();
    }

    /** 同步加载（测试/特殊场景用）：调用线程执行扫描与提交；提交成功后会调度 EDT 刷新。 */
    public void loadAllSync() {
        if (!isReadyToLoad()) {
            return;
        }
        loadAndCommitAll();
    }

    private synchronized boolean isReadyToLoad() {
        return inited && !stopped && !loading;
    }

    private synchronized void setLoading(boolean value) {
        this.loading = value;
    }

    /** 注册 UI 刷新监听器（提交成功后于 EDT 触发一次）。 */
    public synchronized void addUIListener(Runnable listener) {
        if (listener != null) {
            uiRefreshListeners.add(listener);
        }
    }

    /** 退出：逐插件 onUnload（try/catch）→ 关闭全部 loader → 清空注册。 */
    public synchronized void shutdown() {
        if (stopped) {
            return;
        }
        stopped = true;
        loading = false;
        List<LoadedPlugin> toUnload = new ArrayList<>(committed);
        committed.clear();
        committedModeIds.clear();
        committedModeNames.clear();
        host = null;
        for (LoadedPlugin lp : toUnload) {
            unloadOne(lp);
        }
    }

    private void unloadOne(LoadedPlugin lp) {
        try {
            lp.plugin.onUnload();
            LogManager.log("插件卸载 - " + lp.name, "PLUGIN_UNLOADED");
        } catch (Throwable t) {
            LogManager.log("插件 onUnload 异常 - " + lp.name + ": " + t, "PLUGIN_LOAD_ERROR");
        }
        try {
            lp.loader.close();
        } catch (IOException e) {
            // 忽略关闭失败（类加载器泄漏可忽略，但已尝试关闭）
        }
    }

    // ===================== 加载核心 =====================

    private void loadAndCommitAll() {
        boolean any = false;
        try {
            File dir = new File(PLUGIN_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File[] jars = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
            if (jars != null) {
                Arrays.sort(jars, Comparator.comparing(File::getName));
                for (File jar : jars) {
                    if (isStopped()) {
                        break;
                    }
                    if (loadOneJar(jar)) {
                        any = true;
                    }
                }
            }
        } finally {
            setLoading(false);
        }
        if (any && !isStopped()) {
            SwingUtilities.invokeLater(this::notifyUIListeners);
        }
    }

    private synchronized boolean isStopped() {
        return stopped;
    }

    /** 返回是否提交成功（true=已生效）。 */
    private boolean loadOneJar(File jar) {
        URLClassLoader loader = null;
        List<Pair> loaded = new ArrayList<>();
        try {
            loader = new URLClassLoader(new URL[]{jar.toURI().toURL()},
                    PluginManager.class.getClassLoader());
            List<String> classNames = collectClassNames(jar);
            List<Class<?>> candidates = new ArrayList<>();
            for (String cn : classNames) {
                Class<?> c;
                try {
                    c = Class.forName(cn, false, loader);
                } catch (Throwable t) {
                    throw new PluginLoadException("类条目加载失败: " + cn + "（" + t + "）", t);
                }
                if (Plugin.class.isAssignableFrom(c) && !c.isInterface()
                        && !Modifier.isAbstract(c.getModifiers())) {
                    candidates.add(c);
                }
            }
            for (Class<?> cand : candidates) {
                Plugin p;
                try {
                    p = (Plugin) cand.getConstructor().newInstance();
                } catch (Throwable t) {
                    throw new PluginLoadException("插件实例化失败: " + cand.getName() + "（" + t + "）", t);
                }
                PerPluginContext ctx = new PerPluginContext(p);
                try {
                    p.onLoad(ctx);
                } catch (Throwable t) {
                    throw new PluginLoadException("插件 onLoad 异常: " + cand.getName() + "（" + t + "）", t);
                }
                loaded.add(new Pair(p, ctx));
            }

            List<LoadedPlugin> committedList;
            try {
                committedList = buildAndCheckCommit(jar.getName(), loader, loaded);
            } catch (Throwable t) {
                throw new PluginLoadException("提交失败（冲突或注册信息异常）: " + t, t);
            }

            for (LoadedPlugin lp : committedList) {
                LogManager.log("插件加载成功 - " + lp.name + " " + lp.version, "PLUGIN_LOADED");
            }
            appendCommitted(committedList);
            return true;
        } catch (PluginLoadException e) {
            LogManager.log("插件拒载 - " + jar.getName() + ": " + e.getMessage(), "PLUGIN_LOAD_ERROR");
            rollbackLoaded(loaded);
            closeQuietly(loader);
            return false;
        } catch (Throwable t) {
            // 任何未预期错误（I/O、链接等）同样按整 jar 拒载处理，不影响其它 jar
            LogManager.log("插件拒载 - " + jar.getName() + ": " + t, "PLUGIN_LOAD_ERROR");
            rollbackLoaded(loaded);
            closeQuietly(loader);
            return false;
        }
    }

    private List<String> collectClassNames(File jar) throws IOException {
        List<String> names = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String n = entry.getName();
                if (!n.endsWith(".class")) {
                    continue;
                }
                if (n.startsWith("META-INF/versions/")) {
                    continue;
                }
                String cn = n.substring(0, n.length() - ".class".length()).replace('/', '.');
                if (cn.equals("module-info")) {
                    continue;
                }
                names.add(cn);
            }
        }
        return names;
    }

    /** 冲突预检 + 构建提交结果（不产生任何副作用，抛异常=拒载）。 */
    private synchronized List<LoadedPlugin> buildAndCheckCommit(String jarName, URLClassLoader loader,
                                                               List<Pair> loaded) throws PluginLoadException {
        Map<String, String> seenIds = new HashMap<>();
        Map<String, String> seenNames = new HashMap<>();
        List<LoadedPlugin> result = new ArrayList<>();
        for (Pair pair : loaded) {
            PerPluginContext ctx = pair.ctx;
            List<ModeHandler> modes = new ArrayList<>();
            synchronized (ctx) {
                for (ModeHandler h : ctx.pendingModes) {
                    String id = h.getModeId();
                    String nm = h.getDisplayName();
                    if (seenIds.containsKey(id)) {
                        throw new PluginLoadException("同 jar 内 modeId 重复: " + id);
                    }
                    if (seenNames.containsKey(nm)) {
                        throw new PluginLoadException("同 jar 内 displayName 重复: " + nm);
                    }
                    if (ModeRegistry.getByModeId(id) != null) {
                        throw new PluginLoadException("modeId 与内置冲突: " + id);
                    }
                    if (ModeRegistry.getByDisplayName(nm) != null) {
                        throw new PluginLoadException("displayName 与内置冲突: " + nm);
                    }
                    if (committedModeIds.containsKey(id)) {
                        throw new PluginLoadException("modeId 与已加载插件冲突: " + id);
                    }
                    if (committedModeNames.containsKey(nm)) {
                        throw new PluginLoadException("displayName 与已加载插件冲突: " + nm);
                    }
                    seenIds.put(id, nm);
                    seenNames.put(nm, id);
                    modes.add(h);
                }
            }
            Map<UiZone, List<MenuAction>> actions = new EnumMap<>(UiZone.class);
            synchronized (ctx) {
                for (Map.Entry<UiZone, List<MenuAction>> e : ctx.pendingActions.entrySet()) {
                    actions.put(e.getKey(), new ArrayList<>(e.getValue()));
                }
            }
            result.add(new LoadedPlugin(pair.plugin, loader, modes, actions));
        }
        return result;
    }

    private synchronized void appendCommitted(List<LoadedPlugin> list) {
        for (LoadedPlugin lp : list) {
            committed.add(lp);
            for (ModeHandler h : lp.modes) {
                committedModeIds.put(h.getModeId(), lp.name);
                committedModeNames.put(h.getDisplayName(), lp.name);
            }
        }
    }

    private void rollbackLoaded(List<Pair> loaded) {
        for (Pair pair : loaded) {
            try {
                pair.plugin.onUnload();
            } catch (Throwable ignore) {
                // 回滚时忽略
            }
        }
    }

    private void closeQuietly(URLClassLoader loader) {
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException ignore) {
                // 忽略
            }
        }
    }

    private void notifyUIListeners() {
        List<Runnable> copy;
        synchronized (this) {
            copy = new ArrayList<>(uiRefreshListeners);
        }
        for (Runnable r : copy) {
            try {
                r.run();
            } catch (Throwable t) {
                LogManager.log("插件 UI 刷新异常: " + t, "PLUGIN_LOAD_ERROR");
            }
        }
    }

    private ModeHost host() {
        ModeHost h;
        synchronized (this) {
            h = host;
        }
        if (h == null) {
            throw new IllegalStateException("PluginManager 尚未初始化（需先 init(ModeHost)）");
        }
        return h;
    }

    // ===================== 查询（只读快照） =====================

    /** 已提交插件信息（按提交序）。 */
    public synchronized List<PluginInfo> getPluginInfos() {
        List<PluginInfo> out = new ArrayList<>();
        for (LoadedPlugin lp : committed) {
            out.add(new PluginInfo(lp.name, lp.version));
        }
        return Collections.unmodifiableList(out);
    }

    /** 已提交插件模式显示名（按提交序）。 */
    public synchronized List<String> getPluginModeDisplayNames() {
        List<String> out = new ArrayList<>();
        for (LoadedPlugin lp : committed) {
            for (ModeHandler h : lp.modes) {
                out.add(h.getDisplayName());
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** 按显示名取已提交插件模式 Handler；不存在返回 null。 */
    public synchronized ModeHandler getPluginModeHandler(String displayName) {
        for (LoadedPlugin lp : committed) {
            for (ModeHandler h : lp.modes) {
                if (h.getDisplayName().equals(displayName)) {
                    return h;
                }
            }
        }
        return null;
    }

    /** 指定 UI 区域的动作列表（插件提交序 × 插件内注册序，只读快照）；zone 为 null 返回空列表。 */
    public synchronized List<MenuAction> getUiActions(UiZone zone) {
        if (zone == null) {
            return Collections.emptyList();
        }
        List<MenuAction> out = new ArrayList<>();
        for (LoadedPlugin lp : committed) {
            List<MenuAction> list = lp.actions.get(zone);
            if (list != null) {
                out.addAll(list);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** 主窗“插件”菜单项（委托 UiZone.MAIN_MENU，只读快照）。 */
    public List<MenuAction> getMainMenuActions() {
        return getUiActions(UiZone.MAIN_MENU);
    }

    /** 托盘菜单插件项（委托 UiZone.TRAY_MENU，只读快照）。 */
    public List<MenuAction> getTrayMenuActions() {
        return getUiActions(UiZone.TRAY_MENU);
    }

    /** 悬浮球右键菜单插件项（委托 UiZone.FLOATING_BALL_MENU，只读快照）。 */
    public List<MenuAction> getFloatingBallMenuActions() {
        return getUiActions(UiZone.FLOATING_BALL_MENU);
    }

    /** 在 UI 线程安全执行插件菜单/按钮动作（菜单与 UI 按钮动作共用；异常只记日志）。 */
    public void runMenuActionSafely(String pluginName, ActionListener action, ActionEvent event) {
        try {
            action.actionPerformed(event);
        } catch (Throwable t) {
            LogManager.log("插件动作执行异常 - " + pluginName + ": " + t, "PLUGIN_LOAD_ERROR");
        }
    }
}

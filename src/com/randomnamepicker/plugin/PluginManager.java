package com.randomnamepicker.plugin;

import com.randomnamepicker.core.DataManager;
import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.core.NameManager;
import com.randomnamepicker.core.PasswordManager;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import javax.swing.SwingUtilities;

/**
 * 插件管理器（Stage3；宿主插件生态一期扩展）。
 * <p>
 * 加载模型（后台线程 + EDT 刷新）：
 * 1. 扫描运行目录 plugins/（不存在则创建），按文件名排序逐个处理 *.jar；
 * 2. 每个 jar 为一个加载/提交事务单元：
 *    - 读取 Manifest 可选声明 Api-Level-Min（未声明/无法解析视为未声明，照常加载）；不满足 → 整 jar 拒载
 *      VERSION_MISMATCH（宿主 API 级别见 {@link HostApi#PLUGIN_API_LEVEL}）；
 *    - 新建 parent-first URLClassLoader（parent=宿主类加载器）；
 *    - 遍历全部 .class 条目（跳过 module-info.class、META-INF/ 与 META-INF/versions/），任一宿主包
 *      （com/randomnamepicker/）前缀条目 → 整 jar 拒载 BUNDLED_HOST_CLASS；其余条目逐条 Class.forName
 *      预加载，任一失败 → 整 jar 拒载；
 *    - 收集 Plugin 实现类，逐个实例化并 onLoad(ctx)，注册动作只进该插件暂存区；任一失败 → 整 jar 拒载；
 *    - 全部成功后在提交阶段做冲突预检，通过才整体生效；
 * 3. 有插件提交成功时调度 EDT 执行一次 UI 刷新，并派发 PLUGINS_CHANGED 宿主事件。
 * </p>
 * <p>
 * 宿主事件（插件体系二次开发一期）：插件经 PluginContext.addHostEventListener 订阅，回调 EDT 派发、
 * 逐监听器防御；订阅按“jar 文件名 + 实现类全名”归属（唯一），onUnload / 整 jar 拒载回滚 / onLoad
 * 抛异常候选均自动清理；提交前登记的订阅在提交成功后激活。锁事件 LOCK_CHANGED 由各既有 UI 翻转点调用
 * {@link #notifyLockStateIfChanged()} 派发（PasswordManager 零改动，仅真实翻转派发）。
 * </p>
 * <p>
 * 退出：shutdown() 对已提交插件逐个 onUnload（try/catch）、清理其事件订阅并关闭 URLClassLoader。
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

        @Override
        public String toString() {
            return name + " " + version;
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

    /** 拒载原因码（附“建议文案”，对齐《插件开发文档.md》§11 调试速查）。 */
    public enum RejectReason {
        CLASS_LOAD_FAIL("类条目加载失败：通常是插件缺依赖或类版本与宿主 JRE 不兼容；请按文档 §4 检查依赖与 --release 目标。"),
        INSTANTIATE_FAIL("插件实例化失败：实现类须 public + 无参构造，且构造不抛异常。"),
        ONLOAD_FAIL("onLoad 抛异常：请检查插件初始化逻辑；日志含首个异常信息。"),
        COMMIT_CONFLICT("提交冲突：模式 modeId/displayName 与内置或更早提交插件重复，或同 jar 内候选互撞。"),
        NULL_ZONE("插件缺陷：addUiAction 的 zone 为 null（违规调用）。"),
        BUNDLED_HOST_CLASS("打包违规：jar 内包含 com/randomnamepicker/ 的 .class 条目；请仅打包插件自身类，宿主 API 由宿主提供。"),
        VERSION_MISMATCH("宿主 API 级别不足：插件声明的 Api-Level-Min 高于当前宿主（见宿主插件菜单的只读版本信息）。"),
        UNEXPECTED("未预期错误：见日志异常正文。");

        private final String suggestion;

        RejectReason(String suggestion) {
            this.suggestion = suggestion;
        }

        public String getSuggestion() {
            return suggestion;
        }
    }

    /** 单个 jar 的加载结果（只读；成功时 pluginInfos 非空、reason 为 null）。 */
    public static final class LoadOutcome {
        private final String jarName;
        private final boolean loaded;
        private final List<String> pluginInfos;
        private final RejectReason reason;
        private final String detail;
        private final long timestamp;

        LoadOutcome(String jarName, boolean loaded, List<String> pluginInfos,
                    RejectReason reason, String detail) {
            this.jarName = jarName;
            this.loaded = loaded;
            this.pluginInfos = pluginInfos == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(pluginInfos));
            this.reason = reason;
            this.detail = detail;
            this.timestamp = System.currentTimeMillis();
        }

        public String getJarName() {
            return jarName;
        }

        public boolean isLoaded() {
            return loaded;
        }

        public List<String> getPluginInfos() {
            return pluginInfos;
        }

        public RejectReason getReason() {
            return reason;
        }

        public String getDetail() {
            return detail;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /** 单插件事件订阅（按 jar 文件名 + 实现类全名归属，唯一）。 */
    private static final class ListenerSub {
        final String ownerKey;
        final String pluginName;
        final List<HostEventListener> listeners = new ArrayList<>();
        volatile boolean active;

        ListenerSub(String ownerKey, String pluginName) {
            this.ownerKey = ownerKey;
            this.pluginName = pluginName;
        }
    }

    /** 已提交加载结果（一个 Plugin 实现类一条；同一 jar 的多条共享同一 loader）。 */
    private static final class LoadedPlugin {
        final Plugin plugin;
        final String name;
        final String version;
        final String ownerKey;
        final URLClassLoader loader;
        final List<ModeHandler> modes;
        /** 按 Zone 组织的已提交动作（各列表均只读）；顺序 = 提交时注册序。 */
        final Map<UiZone, List<MenuAction>> actions;

        LoadedPlugin(Plugin plugin, String ownerKey, URLClassLoader loader,
                     List<ModeHandler> modes, Map<UiZone, List<MenuAction>> actions) {
            this.plugin = plugin;
            this.name = plugin.getName();
            this.version = plugin.getVersion();
            this.ownerKey = ownerKey;
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
        final RejectReason reason;

        PluginLoadException(RejectReason reason, String message) {
            super(message);
            this.reason = reason;
        }

        PluginLoadException(RejectReason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }
    }

    /** 单插件上下文实现：注册动作写入暂存区；服务访问委托宿主；事件订阅委托 PluginManager（按 ownerKey 归属）。 */
    private final class PerPluginContext implements PluginContext {
        private final Plugin plugin;
        private final String ownerKey;
        private final List<ModeHandler> pendingModes = new ArrayList<>();
        private final Map<UiZone, List<MenuAction>> pendingActions = new EnumMap<>(UiZone.class);

        PerPluginContext(Plugin plugin, String ownerKey) {
            this.plugin = plugin;
            this.ownerKey = ownerKey;
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
                        + " 调用 addUiAction 时 zone 为 null");
            }
            if (title == null || title.trim().isEmpty() || action == null) {
                return; // 与既有菜单注册一致：空 title/action 忽略
            }
            pendingActions.computeIfAbsent(zone, z -> new ArrayList<>())
                    .add(new MenuAction(title, plugin.getName(), action));
        }

        @Override
        public synchronized void addHostEventListener(HostEventListener listener) {
            if (listener != null) {
                registerPluginListener(ownerKey, plugin.getName(), listener);
            }
        }

        @Override
        public synchronized void removeHostEventListener(HostEventListener listener) {
            if (listener != null) {
                removePluginListener(ownerKey, listener);
            }
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
    private final List<LoadOutcome> loadHistory = new ArrayList<>();
    /** 已提交插件的 ownerKey 集合（决定订阅激活与运行时登记）。 */
    private final Set<String> committedOwnerKeys = new HashSet<>();
    /** ownerKey → 订阅（含 pending 与 active）。 */
    private final Map<String, ListenerSub> listenerSubs = new HashMap<>();
    private volatile Boolean lastDispatchedLocked = null;
    private ModeHost host;
    private boolean inited = false;
    private boolean stopped = false;
    private boolean loading = false;

    private PluginManager() {
    }

    // ===================== 生命周期 =====================

    /** 绑定宿主（NamePickerApp 即 ModeHost）。仅需在 loadAll 前调用一次；同时记录当前锁态作 LOCK_CHANGED 去重基线。 */
    public synchronized void init(ModeHost host) {
        this.host = host;
        this.inited = true;
        if (lastDispatchedLocked == null) {
            lastDispatchedLocked = PasswordManager.isLocked();
        }
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

    /** 退出：逐插件 onUnload（try/catch）→ 清理订阅 → 关闭全部 loader → 清空注册。 */
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
        committedOwnerKeys.clear();
        host = null;
        for (LoadedPlugin lp : toUnload) {
            unloadOne(lp);
        }
        synchronized (listenerSubs) {
            listenerSubs.clear();
        }
    }

    private void unloadOne(LoadedPlugin lp) {
        try {
            lp.plugin.onUnload();
            LogManager.log("插件卸载 - " + lp.name, "PLUGIN_UNLOADED");
        } catch (Throwable t) {
            LogManager.log("插件 onUnload 异常 - " + lp.name + ": " + t, "PLUGIN_LOAD_ERROR");
        }
        removeListenerSub(lp.ownerKey);
        try {
            lp.loader.close();
        } catch (IOException e) {
            // 忽略关闭失败（类加载器泄漏可忽略，但已尝试关闭）
        }
    }

    // ===================== 宿主事件（订阅/派发/锁事件） =====================

    /** 登记插件事件订阅（提交前 = pending，提交成功后随激活可派发；运行期登记视为已激活可派发）。 */
    private synchronized void registerPluginListener(String ownerKey, String pluginName, HostEventListener listener) {
        ListenerSub sub = listenerSubs.get(ownerKey);
        if (sub == null) {
            sub = new ListenerSub(ownerKey, pluginName);
            listenerSubs.put(ownerKey, sub);
        }
        sub.listeners.add(listener);
        if (committedOwnerKeys.contains(ownerKey)) {
            sub.active = true;
        }
    }

    /** 注销单个事件监听（幂等）。 */
    private synchronized void removePluginListener(String ownerKey, HostEventListener listener) {
        ListenerSub sub = listenerSubs.get(ownerKey);
        if (sub != null) {
            sub.listeners.remove(listener);
        }
    }

    /** 提交成功后激活该 ownerKey 的订阅（pending → 可派发）。 */
    private synchronized void activateListenerSub(String ownerKey) {
        ListenerSub sub = listenerSubs.get(ownerKey);
        if (sub != null) {
            sub.active = true;
        }
    }

    /** 卸载/回滚/onLoad 抛异常时清理该 ownerKey 的全部订阅。 */
    private synchronized void removeListenerSub(String ownerKey) {
        listenerSubs.remove(ownerKey);
        committedOwnerKeys.remove(ownerKey);
    }

    /**
     * 宿主事件统一派发入口（插件体系一期；宿主 UI/浮球收敛出口调用）。非 EDT 调用会调度到 EDT；
     * 逐监听器 try/catch，异常只记日志不影响其它监听器；仅派发给已提交（激活）插件的订阅。
     */
    public void dispatchHostEvent(HostEvent event) {
        if (event == null) {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> dispatchHostEvent(event));
            return;
        }
        List<ListenerSub> subs;
        synchronized (listenerSubs) {
            subs = new ArrayList<>(listenerSubs.values());
        }
        for (ListenerSub sub : subs) {
            if (!sub.active) {
                continue;
            }
            List<HostEventListener> listeners;
            synchronized (sub) {
                listeners = new ArrayList<>(sub.listeners);
            }
            for (HostEventListener l : listeners) {
                try {
                    l.onHostEvent(event);
                } catch (Throwable t) {
                    LogManager.log("插件事件监听异常 - " + sub.pluginName + ": " + t, "PLUGIN_LOAD_ERROR");
                }
            }
        }
    }

    /**
     * 锁事件去重小方法（宿主内部，供各既有 UI 翻转点动作后调用；非公开插件 API）。
     * 读当前 PasswordManager.isLocked() 与上次已派发锁态比对，真实翻转才派发 LOCK_CHANGED；
     * 基线在 {@link #init} 时记录（PasswordManager 零改动）。
     */
    public void notifyLockStateIfChanged() {
        boolean locked = PasswordManager.isLocked();
        Boolean last;
        synchronized (this) {
            last = lastDispatchedLocked;
            if (last == null) {
                lastDispatchedLocked = locked;
                return;
            }
            if (last == locked) {
                return;
            }
            lastDispatchedLocked = locked;
        }
        dispatchHostEvent(HostEvent.lockChanged(locked));
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

    /** 读取 jar Manifest 的 Api-Level-Min（可选声明）；未声明/非法返回 null（视为未声明）。 */
    private Integer readApiLevelMin(File jar) {
        try (JarFile jf = new JarFile(jar)) {
            Manifest mf = jf.getManifest();
            if (mf == null) {
                return null;
            }
            Attributes attrs = mf.getMainAttributes();
            if (attrs == null) {
                return null;
            }
            String v = attrs.getValue("Api-Level-Min");
            if (v == null || v.trim().isEmpty()) {
                return null;
            }
            return Integer.valueOf(v.trim());
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    /** 返回是否提交成功（true=已生效）。 */
    private boolean loadOneJar(File jar) {
        URLClassLoader loader = null;
        List<Pair> loaded = new ArrayList<>();
        List<String> loadedPluginInfos = new ArrayList<>();
        try {
            Integer minLevel = readApiLevelMin(jar);
            if (minLevel != null && minLevel > HostApi.PLUGIN_API_LEVEL) {
                throw new PluginLoadException(RejectReason.VERSION_MISMATCH,
                        "宿主 API 级别不足：插件声明 Api-Level-Min=" + minLevel
                                + "、宿主当前=" + HostApi.PLUGIN_API_LEVEL);
            }
            loader = new URLClassLoader(new URL[]{jar.toURI().toURL()},
                    PluginManager.class.getClassLoader());
            List<String> classNames = collectClassNames(jar);
            List<Class<?>> candidates = new ArrayList<>();
            for (String cn : classNames) {
                if (cn.startsWith("com.randomnamepicker.")) {
                    throw new PluginLoadException(RejectReason.BUNDLED_HOST_CLASS,
                            "打包违规：jar 内包含宿主包 class 条目: " + cn + "（宿主 API 由宿主 parent-first 提供，禁止打包）");
                }
                Class<?> c;
                try {
                    c = Class.forName(cn, false, loader);
                } catch (Throwable t) {
                    throw new PluginLoadException(RejectReason.CLASS_LOAD_FAIL,
                            "类条目加载失败: " + cn + "（" + t + "）", t);
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
                    throw new PluginLoadException(RejectReason.INSTANTIATE_FAIL,
                            "插件实例化失败: " + cand.getName() + "（" + t + "）", t);
                }
                String ownerKey = jar.getName() + "/" + cand.getName();
                PerPluginContext ctx = new PerPluginContext(p, ownerKey);
                try {
                    p.onLoad(ctx);
                } catch (Throwable t) {
                    removeListenerSub(ownerKey);
                    RejectReason reason = (t instanceof IllegalArgumentException
                            && String.valueOf(t.getMessage()).contains("zone 为 null"))
                            ? RejectReason.NULL_ZONE : RejectReason.ONLOAD_FAIL;
                    throw new PluginLoadException(reason,
                            "插件 onLoad 异常: " + cand.getName() + "（" + t + "）", t);
                }
                loaded.add(new Pair(p, ctx));
            }

            List<LoadedPlugin> committedList;
            try {
                committedList = buildAndCheckCommit(jar.getName(), loader, loaded);
            } catch (PluginLoadException e) {
                throw e; // 冲突/注册信息异常已带 COMMIT_CONFLICT 原因，直接透传
            } catch (Throwable t) {
                throw new PluginLoadException(RejectReason.COMMIT_CONFLICT,
                        "提交失败（冲突或注册信息异常）: " + t, t);
            }

            for (LoadedPlugin lp : committedList) {
                LogManager.log("插件加载成功 - " + lp.name + " " + lp.version, "PLUGIN_LOADED");
                loadedPluginInfos.add(lp.name + " " + lp.version);
            }
            appendCommitted(committedList);
            recordOutcome(new LoadOutcome(jar.getName(), true, loadedPluginInfos, null, null));
            return true;
        } catch (PluginLoadException e) {
            LogManager.log("插件拒载 - " + jar.getName() + ": " + e.getMessage(), "PLUGIN_LOAD_ERROR");
            rollbackLoaded(loaded);
            closeQuietly(loader);
            recordOutcome(new LoadOutcome(jar.getName(), false, null, e.reason, e.getMessage()));
            return false;
        } catch (Throwable t) {
            // 任何未预期错误（I/O、链接等）同样按整 jar 拒载处理，不影响其它 jar
            LogManager.log("插件拒载 - " + jar.getName() + ": " + t, "PLUGIN_LOAD_ERROR");
            rollbackLoaded(loaded);
            closeQuietly(loader);
            recordOutcome(new LoadOutcome(jar.getName(), false, null,
                    RejectReason.UNEXPECTED, String.valueOf(t)));
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
                if (n.startsWith("META-INF/")) {
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
                        throw new PluginLoadException(RejectReason.COMMIT_CONFLICT,
                                "同 jar 内 modeId 重复: " + id);
                    }
                    if (seenNames.containsKey(nm)) {
                        throw new PluginLoadException(RejectReason.COMMIT_CONFLICT,
                                "同 jar 内 displayName 重复: " + nm);
                    }
                    if (ModeRegistry.getByModeId(id) != null) {
                        throw new PluginLoadException(RejectReason.COMMIT_CONFLICT,
                                "modeId 与内置冲突: " + id);
                    }
                    if (ModeRegistry.getByDisplayName(nm) != null) {
                        throw new PluginLoadException(RejectReason.COMMIT_CONFLICT,
                                "displayName 与内置冲突: " + nm);
                    }
                    if (committedModeIds.containsKey(id)) {
                        throw new PluginLoadException(RejectReason.COMMIT_CONFLICT,
                                "modeId 与已加载插件冲突: " + id);
                    }
                    if (committedModeNames.containsKey(nm)) {
                        throw new PluginLoadException(RejectReason.COMMIT_CONFLICT,
                                "displayName 与已加载插件冲突: " + nm);
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
            result.add(new LoadedPlugin(pair.plugin, ctx.ownerKey, loader, modes, actions));
        }
        return result;
    }

    private synchronized void appendCommitted(List<LoadedPlugin> list) {
        for (LoadedPlugin lp : list) {
            committed.add(lp);
            committedOwnerKeys.add(lp.ownerKey);
            activateListenerSub(lp.ownerKey);
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
            removeListenerSub(pair.ctx.ownerKey);
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

    private synchronized void recordOutcome(LoadOutcome outcome) {
        if (outcome != null) {
            loadHistory.add(outcome);
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
        // 宿主插件集变化事件（有提交才派发，与 UI 刷新同口径；纯拒载由加载历史查询承担）
        List<LoadOutcome> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(loadHistory);
        }
        dispatchHostEvent(HostEvent.pluginsChanged(snapshot));
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

    /** 本次启动的全部加载结果（jar 处理序，只读快照）。 */
    public synchronized List<LoadOutcome> getLoadHistory() {
        return Collections.unmodifiableList(new ArrayList<>(loadHistory));
    }

    /** 指定 jar 的最后一条加载结果；不存在返回 null。 */
    public synchronized LoadOutcome getLoadOutcome(String jarName) {
        if (jarName == null) {
            return null;
        }
        for (int i = loadHistory.size() - 1; i >= 0; i--) {
            LoadOutcome o = loadHistory.get(i);
            if (jarName.equals(o.getJarName())) {
                return o;
            }
        }
        return null;
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

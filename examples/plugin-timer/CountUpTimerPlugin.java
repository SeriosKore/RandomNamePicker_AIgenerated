import com.randomnamepicker.plugin.Plugin;
import com.randomnamepicker.plugin.PluginContext;
import com.randomnamepicker.plugin.UiZone;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import javax.swing.SwingUtilities;

/**
 * 正计时子球插件（依《正计时子球开发方案.md》实现；宿主 src/ 零改动，纯插件 jar 部署 plugins/）。
 * <p>
 * 能力：
 * <ul>
 *   <li>在宿主悬浮球上叠加正计时子球——单击悬浮球（非拖动/非双击序列）=冒出/收回并同步开始/暂停/继续；
 *       子球单击=暂停/继续；暂停态双击子球=归零并收回；悬浮球双击抽取、拖动均保持宿主原样；</li>
 *   <li>宿主 UI 槽一期（UiZone.SETTINGS_WINDOW）：设置窗“插件”区注册“计时子球”按钮 → 弹出
 *       “计时子球设置”对话框（§3.3：尺寸模式/固定直径/三态配色/间距/恢复默认，即时生效并持久化）；</li>
 *   <li>辅助菜单（主窗/托盘/悬浮球右键）：“计时子球：冒出/收回”“计时子球：归零”。</li>
 * </ul>
 * 生命周期：onLoad（后台线程）只做注册与 EDT 调度初始化；onUnload 完整清理
 * （卸载 AWT 监听/停轮询/销毁子球窗口），并兼容整包拒载回滚路径。
 */
public class CountUpTimerPlugin implements Plugin {

    private static final String NAME = "正计时子球";
    private static final String VERSION = "1.0";

    private PluginContext ctx;
    private volatile TimerBallConfig config;
    private volatile FloatingBallTracker tracker;
    private volatile boolean inactive = false;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public void onLoad(PluginContext context) {
        this.ctx = context;

        // 设置窗“插件”区按钮（宿主 UI 槽一期；点击经宿主 runMenuActionSafely 于 EDT 防御执行）
        context.addUiAction(UiZone.SETTINGS_WINDOW, "计时子球", this::openSettings);

        // 三类菜单（旧便捷 API，等价 addUiAction(MAIN_MENU/TRAY_MENU/FLOATING_BALL_MENU, …)）
        context.addMainMenuAction("计时子球：冒出/收回", e -> runOnEdt(this::toggleFromMenu));
        context.addTrayMenuAction("计时子球：冒出/收回", e -> runOnEdt(this::toggleFromMenu));
        context.addFloatingBallMenuAction("计时子球：冒出/收回", e -> runOnEdt(this::toggleFromMenu));

        context.addMainMenuAction("计时子球：归零", e -> runOnEdt(this::resetFromMenu));
        context.addTrayMenuAction("计时子球：归零", e -> runOnEdt(this::resetFromMenu));
        context.addFloatingBallMenuAction("计时子球：归零", e -> runOnEdt(this::resetFromMenu));

        // EDT 初始化（加载配置 + 建 Tracker：装全局 AWT 监听、启几何轮询、预建隐藏子球）
        SwingUtilities.invokeLater(this::initOnEdt);
    }

    @Override
    public void onUnload() {
        inactive = true;
        FloatingBallTracker t = tracker;
        tracker = null;
        config = null;
        ctx = null;
        if (t != null) {
            t.stop();
        }
    }

    /** EDT 初始化；若 onUnload 先于本任务执行则放弃。 */
    private void initOnEdt() {
        if (inactive) {
            return;
        }
        ensureInitializedOnEdt();
    }

    /** 惰性装配：加载外观配置（data/timerball_plugin.properties）并创建/启动 Tracker（仅 EDT）。 */
    private void ensureInitializedOnEdt() {
        if (inactive) {
            return;
        }
        if (config == null) {
            TimerBallConfig c = new TimerBallConfig();
            c.load();
            config = c;
        }
        if (tracker == null) {
            FloatingBallTracker t = new FloatingBallTracker(config);
            t.start();
            tracker = t;
        }
    }

    // ===================== 设置窗按钮 → 外观设置对话框 =====================

    private void openSettings(ActionEvent ev) {
        runOnEdt(() -> {
            if (inactive) {
                return;
            }
            ensureInitializedOnEdt();
            Window owner = ownerOf(ev);
            if (owner == null) {
                owner = ownerWindow();
            }
            // 模态对话框；关闭（含“完成”）后由 DISPOSE_ON_CLOSE 释放
            TimerBallSettingsDialog dlg = new TimerBallSettingsDialog(owner, config,
                    this::applyConfigNow);
            dlg.setVisible(true);
            dlg.dispose();
        });
    }

    /** 设置变更实时应用到已显示的计时子球（§3.3）。 */
    private void applyConfigNow() {
        FloatingBallTracker t = tracker;
        if (t != null) {
            t.applyConfig();
        }
    }

    /** 按钮事件源所在 Window（= SettingsWindow，宿主 UI 槽按钮位于设置窗内）。 */
    private Window ownerOf(ActionEvent ev) {
        try {
            if (ev != null && ev.getSource() instanceof Component) {
                Window w = SwingUtilities.getWindowAncestor((Component) ev.getSource());
                if (w != null) {
                    return w;
                }
            }
        } catch (Throwable ignore) {
            // 忽略
        }
        return null;
    }

    // ===================== 菜单动作（§2.2 P2 定稿口径） =====================

    private void toggleFromMenu() {
        if (inactive) {
            return;
        }
        ensureInitializedOnEdt();
        FloatingBallTracker t = tracker;
        if (t != null) {
            t.toggleShowFromMenu(ownerWindow());
        }
    }

    private void resetFromMenu() {
        if (inactive) {
            return;
        }
        ensureInitializedOnEdt();
        FloatingBallTracker t = tracker;
        if (t != null) {
            t.resetFromMenu(ownerWindow());
        }
    }

    /** 宿主主窗（ModeHost.getOwner() 为 java.awt.Window；顶级窗时可能为 null，可作对话框父窗）。 */
    private Window ownerWindow() {
        try {
            if (ctx != null && ctx.getModeHost() != null) {
                return ctx.getModeHost().getOwner();
            }
        } catch (Throwable ignore) {
            // 忽略
        }
        return null;
    }

    private void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}

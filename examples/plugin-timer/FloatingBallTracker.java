import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.floating.FloatingBall;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * 计时子球控制器（正计时子球开发方案 §1.2 通路 A+B / §2.2 迁移表 / P1、P2 定稿口径）。
 * <p>
 * 通路 B（几何追踪）：EDT 轮询 {@code Window.getWindows()} 定位可见悬浮球，子球跟随其位移、
 * 悬浮球消失即收回并暂停（保留读数），重现后不自动冒出。
 * 通路 A（观察式单击钩子）：全局 AWT 监听只观察不拦截；当左键单击落在悬浮球窗口内且非拖动、
 * 非双击序列时触发“冒出/收回”（与宿主双击抽取、拖动互不干扰——单击延迟、双击/拖动取消，见 §1.3）。
 * </p>
 * 菜单动作口径（评审 P2）：“冒出/收回”≡单击悬浮球；任何“计时归零”最终落点固定 HIDDEN+READY
 * （运行态先暂停→清零→收回）。所有方法仅限 EDT 调用（start 例外：由调用方保证）。
 */
public class FloatingBallTracker {

    private static final int POLL_MS = 80;
    /** AUTO 联动口径（§3.3 默认）：悬浮球直径 × SIZE_RATIO，夹 AUTO_MIN..AUTO_MAX。 */
    private static final double SIZE_RATIO = 0.55;
    private static final int AUTO_MIN_DIAMETER = 36;
    private static final int AUTO_MAX_DIAMETER = 60;
    private static final int INITIAL_DIAMETER = 44;

    private final TimerBallConfig config;
    private TimerBall timerBall;
    private final Timer pollTimer = new Timer(POLL_MS, e -> onPollTick());
    private AWTEventListener awtListener;
    private FloatingBall currentBall;
    private boolean childVisible = false;
    private boolean stopped = false;

    // 悬浮球单击延迟-取消判定状态（§1.3）
    private boolean pressedInBall = false;
    private boolean dragSincePress = false;
    private Timer singleToggleTimer;
    private int multiClickIntervalMs = 500;

    public FloatingBallTracker(TimerBallConfig config) {
        this.config = config;
        this.timerBall = new TimerBall(config, INITIAL_DIAMETER);
        readMultiClickInterval();
        // 暂停态双击子球 → 归零并收回（§2.2 双击重置）
        timerBall.setHideRequest(this::hideChildQuiet);
    }

    private void readMultiClickInterval() {
        try {
            Object v = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
            if (v instanceof Integer && (Integer) v > 0) {
                multiClickIntervalMs = Math.max(120, Math.min(1000, (Integer) v));
            }
        } catch (Throwable ignore) {
            // 保持默认 500ms
        }
    }

    // ===================== 生命周期（EDT 调用） =====================

    /** 安装全局 AWT 监听 + 启动轮询（onLoad 内经 invokeLater 调度到 EDT）。 */
    public void start() {
        if (stopped) {
            return;
        }
        awtListener = this::onAwtEvent;
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(awtListener,
                    AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        } catch (Throwable t) {
            awtListener = null;
            LogManager.log("正计时子球-AWT监听注册失败: " + t, "PLUGIN_LOAD_ERROR");
        }
        pollTimer.start();
    }

    /** 停止监听/轮询并销毁子球窗口（onUnload；线程安全，内部会切到 EDT 处置窗口）。 */
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        pollTimer.stop();
        if (singleToggleTimer != null) {
            singleToggleTimer.stop();
        }
        if (awtListener != null) {
            try {
                Toolkit.getDefaultToolkit().removeAWTEventListener(awtListener);
            } catch (Throwable ignore) {
                // 忽略
            }
            awtListener = null;
        }
        currentBall = null;
        runOnEdtNow(() -> {
            try {
                timerBall.dispose();
            } catch (Throwable ignore) {
                // 忽略
            }
        });
    }

    private void runOnEdtNow(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ignore) {
            // 忽略
        }
    }

    // ===================== 菜单动作（P2 定稿口径） =====================

    /**
     * 菜单“计时子球：冒出/收回”≡单击悬浮球（同一动作）。
     * 悬浮球不存在且子球收起时：仅提示“请先打开悬浮球”。
     */
    public void toggleShowFromMenu(Window parent) {
        if (stopped) {
            return;
        }
        if (childVisible) {
            hideAndPauseIfRunning();
            return;
        }
        FloatingBall anchor = findCurrentBall();
        if (anchor == null) {
            JOptionPane.showMessageDialog(parent,
                    "请先打开悬浮球（主窗“悬浮球”按钮），再使用计时子球。",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        showAndRun(anchor);
    }

    /**
     * 菜单“计时子球：归零”——最终落点固定 HIDDEN+READY（§2.2 P2 定稿）：
     * 运行态先暂停再清零并伴随收回；暂停态清零并收回；隐藏保留读数态清零保持收起；已收起且 00:00 无操作。
     */
    public void resetFromMenu(Window parent) {
        if (stopped) {
            return;
        }
        boolean visible = childVisible;
        if (!visible && timerBall.isReady()) {
            return; // HIDDEN+READY：无操作
        }
        if (timerBall.isRunning()) {
            timerBall.pauseCount(); // 先暂停（P2：运行态先暂停再清零）
            LogManager.log("正计时子球-暂停(归零前)", "TIMER_PAUSE");
        }
        timerBall.resetCount();
        LogManager.log("正计时子球-归零(菜单)", "TIMER_RESET");
        if (visible) {
            hideChild();
        }
    }

    // ===================== 冒出/收回核心（§2.2 迁移表） =====================

    /** 子球冒出并自动开始/继续（HIDDEN+READY→开始；HIDDEN+PAUSED→继续）。 */
    private void showAndRun(FloatingBall anchor) {
        syncDiameter(anchor);
        placeRelative(anchor);
        timerBall.setVisible(true);
        childVisible = true;
        LogManager.log("正计时子球-冒出", "TIMER_BALL_SHOW");
        if (timerBall.isReady()) {
            timerBall.startCount(); // READY：自 00:00 开始正计时
            LogManager.log("正计时子球-开始(冒出)", "TIMER_START");
        } else if (timerBall.isPaused()) {
            timerBall.resumeCount(); // PAUSED：重新冒出并继续
            LogManager.log("正计时子球-继续(冒出)", "TIMER_RESUME");
        }
        timerBall.toFront();
    }

    /** 收回（运行中先暂停并保留读数，P2：VISIBLE+RUNNING=收回并暂停）。 */
    private void hideAndPauseIfRunning() {
        if (timerBall.isRunning()) {
            timerBall.pauseCount();
            LogManager.log("正计时子球-暂停(收回)", "TIMER_PAUSE");
        }
        hideChild();
    }

    private void hideChild() {
        if (childVisible) {
            timerBall.setVisible(false);
            childVisible = false;
            LogManager.log("正计时子球-收回", "TIMER_BALL_HIDE");
        }
    }

    /** 子球双击归零后由 TimerBall 回调的收回（此时已 READY，仅隐藏）。 */
    private void hideChildQuiet() {
        if (childVisible) {
            timerBall.setVisible(false);
            childVisible = false;
            LogManager.log("正计时子球-收回(双击归零后)", "TIMER_BALL_HIDE");
        }
    }

    // ===================== 通路 B：几何追踪轮询 =====================

    private void onPollTick() {
        if (stopped) {
            return;
        }
        FloatingBall found = findCurrentBall();
        if (found == null) {
            if (currentBall != null && childVisible) {
                // 悬浮球被关闭/隐藏/dispose → 子球收回并暂停（保留读数），重现后不自动冒出
                hideAndPauseIfRunning();
            }
            currentBall = null;
            return;
        }
        currentBall = found;
        if (childVisible) {
            try {
                syncDiameter(found);
                placeRelative(found);
                timerBall.toFront();
            } catch (Throwable ignore) {
                // 几何异常忽略，下个 tick 重试
            }
        }
    }

    /** 定位当前可见悬浮球（宿主每次开关都会 dispose+重建，故持续重探测）。 */
    private FloatingBall findCurrentBall() {
        Window[] windows = Window.getWindows();
        for (Window w : windows) {
            if (w instanceof FloatingBall && w.isDisplayable() && w.isVisible()) {
                return (FloatingBall) w;
            }
        }
        return null;
    }

    private void syncDiameter(FloatingBall anchor) {
        int d;
        if (config.isFixed()) {
            d = config.getFixedDiameter(); // §3.3：固定直径（对话框限幅 30–90）
        } else {
            d = (int) Math.round(anchor.getWidth() * SIZE_RATIO);
            d = Math.max(AUTO_MIN_DIAMETER, Math.min(AUTO_MAX_DIAMETER, d));
        }
        if (timerBall.getDiameter() != d) {
            timerBall.setDiameter(d);
        }
    }

    /** 冒出方向（§0 规则）：悬浮球位于所在屏幕下半部分→子球在其正上方；上半部分→正下方；边界 clamp 防出屏。 */
    private void placeRelative(FloatingBall anchor) {
        Rectangle ball;
        Rectangle screen;
        try {
            ball = new Rectangle(anchor.getLocationOnScreen(), anchor.getSize());
            screen = anchor.getGraphicsConfiguration().getBounds();
        } catch (Throwable t) {
            return; // 尚未 displayable / 已销毁，下个 tick 重试
        }
        int d = timerBall.getDiameter();
        int gap = config.getGapPx(); // §3.3：与悬浮球间距可调（4–20px）
        int cx = ball.x + ball.width / 2;
        boolean putAbove = (ball.y + ball.height / 2) > (screen.y + screen.height / 2);

        int x = clamp(cx - d / 2, screen.x, screen.x + Math.max(0, screen.width - d));
        int y;
        if (putAbove) {
            y = ball.y - gap - d;
            if (y < screen.y) {
                y = Math.max(screen.y, ball.y + ball.height + gap); // 兜底：改放下方
            }
        } else {
            y = ball.y + ball.height + gap;
            if (y + d > screen.y + screen.height) {
                y = Math.max(screen.y, ball.y - gap - d); // 兜底：改放上方
            }
        }
        timerBall.setLocation(x, y);
    }

    /** 外观配置变更即时应用（方案 §3.3）：已显示时按新尺寸/间距重排，并重绘配色。 */
    public void applyConfig() {
        if (stopped) {
            return;
        }
        if (childVisible) {
            FloatingBall b = findCurrentBall();
            if (b != null) {
                try {
                    syncDiameter(b);
                    placeRelative(b);
                } catch (Throwable ignore) {
                    // 几何异常忽略，下个轮询 tick 重试
                }
            }
        }
        timerBall.refresh();
    }

    private int clamp(int v, int lo, int hi) {
        if (lo > hi) {
            return lo;
        }
        return Math.max(lo, Math.min(hi, v));
    }

    // ===================== 通路 A：全局 AWT 观察监听 =====================

    private void onAwtEvent(AWTEvent ev) {
        if (stopped || !(ev instanceof MouseEvent)) {
            return;
        }
        MouseEvent me = (MouseEvent) ev;
        switch (me.getID()) {
            case MouseEvent.MOUSE_PRESSED:
                if (!SwingUtilities.isLeftMouseButton(me)) {
                    pressedInBall = false;
                    break;
                }
                pressedInBall = (ballOf(me) != null);
                dragSincePress = false;
                break;
            case MouseEvent.MOUSE_DRAGGED:
                // P1 口径：按下期间任一 MOUSE_DRAGGED 即视为拖动（无位移阈值，1px 也算）
                if (pressedInBall) {
                    dragSincePress = true;
                }
                break;
            case MouseEvent.MOUSE_RELEASED:
                pressedInBall = false;
                break;
            case MouseEvent.MOUSE_CLICKED:
                if (ballOf(me) == null || !SwingUtilities.isLeftMouseButton(me)) {
                    break;
                }
                if (me.getClickCount() == 1) {
                    if (dragSincePress) {
                        dragSincePress = false; // 拖动后释放：不触发
                        break;
                    }
                    scheduleSingleToggle();
                } else {
                    // 双击（宿主已在其第二次按下时执行随机抽取）：取消挂起单击，不冒出/收回
                    cancelSingleToggle();
                    dragSincePress = false;
                }
                break;
            default:
                break;
        }
    }

    /** 单击事件命中的悬浮球窗口（沿祖先链上溯；若命中其它顶层窗口则返回 null）。 */
    private FloatingBall ballOf(MouseEvent me) {
        Component c = me.getComponent();
        while (c != null) {
            if (c instanceof FloatingBall) {
                return (FloatingBall) c;
            }
            if (c instanceof Window) {
                return null;
            }
            c = c.getParent();
        }
        return null;
    }

    private void scheduleSingleToggle() {
        if (singleToggleTimer == null) {
            singleToggleTimer = new Timer(multiClickIntervalMs, e -> {
                singleToggleTimer.stop();
                fireSingleToggle();
            });
            singleToggleTimer.setRepeats(false);
        }
        singleToggleTimer.stop();
        singleToggleTimer.setInitialDelay(multiClickIntervalMs);
        singleToggleTimer.start();
    }

    private void cancelSingleToggle() {
        if (singleToggleTimer != null) {
            singleToggleTimer.stop();
        }
    }

    private void fireSingleToggle() {
        if (stopped) {
            return;
        }
        if (childVisible) {
            hideAndPauseIfRunning();
            return;
        }
        FloatingBall anchor = findCurrentBall();
        if (anchor == null) {
            return; // 球已不在（如点击后 500ms 内被关闭）：静默忽略
        }
        showAndRun(anchor);
    }
}

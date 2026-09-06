import com.randomnamepicker.core.LogManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * 计时子球（正计时子球开发方案 §2.4 视觉 / §2.2 子球侧交互 / §2.3 计时实现）。
 * <p>
 * 自绘圆形窗口：状态配色 READY=灰蓝 / RUNNING=绿 / PAUSED=橙，居中显示正计时文本。
 * 交互（本类只负责“子球自身被点击”）：
 * <ul>
 *   <li>单击：READY→开始（防御）、RUNNING→暂停、PAUSED→继续；</li>
 *   <li>双击：仅 PAUSED 有效 → 归零 00:00 并经 {@link HideRequest#onResetHide()} 请求收回；其余状态忽略。</li>
 * </ul>
 * 单击/双击按 §1.3 的“延迟单击、双击取消”区分（单击有约 0.2–0.5s 可感知延迟）。
 * 计时采用系统时间戳累计：显示值 = accumulatedMs + (RUNNING ? now - resumeBaseMs : 0)，防 tick 漂移。
 * </p>
 * 本类所有公开方法仅在 EDT 调用（由 FloatingBallTracker 保证）。
 */
public class TimerBall extends JWindow {

    /** 计时状态（§2.1）。 */
    public enum TimerState { READY, RUNNING, PAUSED }

    /** 双击重置后的收回请求回调（由控制器实现）。 */
    public interface HideRequest {
        void onResetHide();
    }

    private static final long TICK_MS = 200L;

    private final TimerBallConfig config;
    private final TimerPanel panel = new TimerPanel();
    private TimerState state = TimerState.READY;
    private long accumulatedMs = 0L;
    private long resumeBaseMs = 0L;
    private Timer tickTimer;
    private Timer singleTimer; // 单击延迟判定（可被双击取消）
    private HideRequest hideRequest;
    private int multiClickIntervalMs = 500;

    public TimerBall(TimerBallConfig config, int diameter) {
        this.config = config;
        setAlwaysOnTop(true);
        setFocusableWindowState(false); // 不抢主程序键盘焦点
        setBackground(new Color(0, 0, 0, 0));
        Component content = getContentPane();
        if (content instanceof JComponent) {
            ((JComponent) content).setOpaque(false); // 圆外四角保持透明
        }
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        setSize(Math.max(1, diameter), Math.max(1, diameter));
        setLocation(-20000, -20000); // 初始远离屏幕（不可见）

        readMultiClickInterval();

        // 走表刷新（仅运行中需要重绘；200ms 秒级显示）
        tickTimer = new Timer((int) TICK_MS, e -> {
            if (state == TimerState.RUNNING) {
                panel.repaint();
            }
        });
        tickTimer.start();

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (e.getClickCount() >= 2) {
                    cancelScheduledSingle();
                    onDoubleClick();
                } else {
                    scheduleSingleClick();
                }
            }
        };
        panel.addMouseListener(mouse);
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

    // ===================== 子球自身单击/双击 =====================

    private void scheduleSingleClick() {
        if (singleTimer == null) {
            singleTimer = new Timer(multiClickIntervalMs, e -> {
                singleTimer.stop();
                onSingleClick();
            });
            singleTimer.setRepeats(false);
        }
        singleTimer.stop();
        singleTimer.setInitialDelay(multiClickIntervalMs);
        singleTimer.start();
    }

    private void cancelScheduledSingle() {
        if (singleTimer != null) {
            singleTimer.stop();
        }
    }

    private void onSingleClick() {
        switch (state) {
            case READY: // 防御分支：展开而未计时时单击=开始
                startCount();
                LogManager.log("正计时子球-开始(子球单击)", "TIMER_START");
                break;
            case RUNNING:
                pauseCount();
                LogManager.log("正计时子球-暂停(子球单击)", "TIMER_PAUSE");
                break;
            case PAUSED:
                resumeCount();
                LogManager.log("正计时子球-继续(子球单击)", "TIMER_RESUME");
                break;
        }
        panel.repaint();
    }

    private void onDoubleClick() {
        if (state == TimerState.PAUSED) {
            // 双击重置（§2.2）：仅暂停态有效；归零后请求收回（由控制器隐藏）
            resetCount();
            LogManager.log("正计时子球-归零(子球双击)", "TIMER_RESET");
            panel.repaint();
            if (hideRequest != null) {
                hideRequest.onResetHide();
            }
        }
        // 运行态/就绪态双击：忽略（先经取消挂起单击，避免误暂停/误清零）
    }

    // ===================== 计时核心（§2.3） =====================

    private long now() {
        return System.currentTimeMillis();
    }

    /** 从 00:00 开始正计时（控制器仅应在 READY 时调用）。 */
    public void startCount() {
        accumulatedMs = 0L;
        resumeBaseMs = now();
        state = TimerState.RUNNING;
        panel.repaint();
    }

    /** 继续（控制器仅应在 PAUSED 时调用）。 */
    public void resumeCount() {
        if (state == TimerState.PAUSED) {
            resumeBaseMs = now();
            state = TimerState.RUNNING;
            panel.repaint();
        }
    }

    /** 暂停并保留读数（控制器仅应在 RUNNING 时调用）。 */
    public void pauseCount() {
        if (state == TimerState.RUNNING) {
            accumulatedMs += now() - resumeBaseMs;
            state = TimerState.PAUSED;
            panel.repaint();
        }
    }

    /** 归零：任意状态 → READY（读数 00:00）。 */
    public void resetCount() {
        accumulatedMs = 0L;
        resumeBaseMs = now();
        state = TimerState.READY;
        panel.repaint();
    }

    public TimerState getState() {
        return state;
    }

    public boolean isReady() {
        return state == TimerState.READY;
    }

    public boolean isRunning() {
        return state == TimerState.RUNNING;
    }

    public boolean isPaused() {
        return state == TimerState.PAUSED;
    }

    public void setHideRequest(HideRequest hideRequest) {
        this.hideRequest = hideRequest;
    }

    /** 当前显示读数（含运行中未暂停的增量）。 */
    private long elapsedMs() {
        long base = accumulatedMs;
        if (state == TimerState.RUNNING) {
            base += now() - resumeBaseMs;
        }
        return Math.max(0L, base);
    }

    private String text() {
        long sec = elapsedMs() / 1000L;
        long h = sec / 3600L;
        long m = (sec % 3600L) / 60L;
        long s = sec % 60L;
        if (h > 0L) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }

    // ===================== 直径 =====================

    public int getDiameter() {
        return getWidth();
    }

    public void setDiameter(int d) {
        d = Math.max(1, d);
        if (d != getWidth()) {
            setSize(d, d);
            panel.repaint();
        }
    }

    /** 外观设置变更后强制重绘（配色/尺寸即时生效，方案 §3.3）。 */
    public void refresh() {
        panel.repaint();
    }

    @Override
    public void dispose() {
        if (tickTimer != null) {
            tickTimer.stop();
        }
        if (singleTimer != null) {
            singleTimer.stop();
        }
        super.dispose();
    }

    // ===================== 自绘面板 =====================

    private class TimerPanel extends JPanel {

        TimerPanel() {
            setOpaque(false);
            setBackground(new Color(0, 0, 0, 0));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            Color fill;
            switch (state) {
                case RUNNING:
                    fill = config.getColorRunning();
                    break;
                case PAUSED:
                    fill = config.getColorPaused();
                    break;
                default:
                    fill = config.getColorReady();
                    break;
            }
            g2d.setColor(fill);
            g2d.fillOval(0, 0, w, h);
            g2d.setColor(new Color(255, 255, 255, 190));
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawOval(1, 1, Math.max(0, w - 2), Math.max(0, h - 2));

            String txt = text();
            int fs = Math.max(8, (int) (h * 0.30));
            Font font = new Font("微软雅黑", Font.BOLD, fs);
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics(font);
            while (fs > 8 && fm.stringWidth(txt) > w - 8) {
                fs--;
                font = new Font("微软雅黑", Font.BOLD, fs);
                g2d.setFont(font);
                fm = g2d.getFontMetrics(font);
            }
            g2d.setColor(Color.WHITE);
            int tw = fm.stringWidth(txt);
            int x = (w - tw) / 2;
            int y = (h - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent();
            g2d.drawString(txt, x, y);

            g2d.dispose();
        }
    }
}

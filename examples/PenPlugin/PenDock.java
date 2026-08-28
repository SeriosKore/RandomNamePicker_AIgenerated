import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * 附着于悬浮球的画笔入口（参考 Seewo 白板悬浮球）：
 * 铅笔图标按钮吸附在悬浮球右侧并随球移动；
 * 点击展开/收起工具条（画笔颜色、开始/停止画笔、清屏、收起），
 * 不占用悬浮球本体，不影响悬浮球拖拽与点名。
 */
public class PenDock {
    private PenPlugin plugin;
    private JWindow dockWindow;
    private JWindow toolWindow;
    private Timer trackTimer;
    private boolean expanded;
    private JButton penToggleButton;

    private static final Color[] COLORS = {
            Color.BLACK, new Color(220, 30, 30), new Color(30, 90, 220), new Color(30, 160, 60)
    };

    public PenDock(PenPlugin plugin) {
        this.plugin = plugin;
        buildDockWindow();
        buildToolWindow();
        trackTimer = new Timer(120, e -> trackBall());
        trackTimer.start();
    }

    private void buildDockWindow() {
        dockWindow = new JWindow();
        dockWindow.setAlwaysOnTop(true);

        JButton dockButton = new JButton("\u270F");   // ✏
        dockButton.setFont(new Font("微软雅黑", Font.BOLD, 16));
        dockButton.setPreferredSize(new Dimension(38, 38));
        dockButton.setFocusable(false);
        dockButton.setToolTipText("画笔工具（点击展开/收起）");
        dockButton.addActionListener(e -> setExpanded(!expanded));

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setBackground(new Color(245, 245, 245));
        wrapper.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 120), 1));
        wrapper.add(dockButton);
        dockWindow.getContentPane().add(wrapper);
        dockWindow.pack();
    }

    private void buildToolWindow() {
        toolWindow = new JWindow();
        toolWindow.setAlwaysOnTop(true);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 120, 120), 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        bar.setBackground(new Color(245, 245, 245));

        for (Color color : COLORS) {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(24, 24));
            button.setBackground(color);
            button.setOpaque(true);
            button.setBorderPainted(true);
            button.setToolTipText("画笔颜色");
            button.setFocusable(false);
            button.addActionListener(e -> plugin.setCurrentColor(color));
            bar.add(button);
        }

        bar.add(new JLabel(" "));

        penToggleButton = new JButton("开始画笔");
        penToggleButton.setFocusable(false);
        penToggleButton.addActionListener(e -> plugin.togglePenMode());
        bar.add(penToggleButton);

        JButton clearButton = new JButton("清屏");
        clearButton.setFocusable(false);
        clearButton.addActionListener(e -> plugin.clearScreen());
        bar.add(clearButton);

        JButton collapseButton = new JButton("收起");
        collapseButton.setFocusable(false);
        collapseButton.addActionListener(e -> setExpanded(false));
        bar.add(collapseButton);

        toolWindow.getContentPane().add(bar);
        toolWindow.pack();
    }

    /** 跟随悬浮球：吸附在球体右侧；悬浮球隐藏时同步隐藏。 */
    private void trackBall() {
        FloatingBall ball = plugin.getMainApp().getFloatingBall();
        if (ball != null && ball.isVisible()) {
            Point p = ball.getLocationOnScreen();
            int dockX = p.x + ball.getWidth() + 6;
            int dockY = p.y + ball.getHeight() / 2 - dockWindow.getHeight() / 2;
            dockWindow.setLocation(dockX, dockY);
            dockWindow.setVisible(true);
            dockWindow.toFront();
            if (expanded) {
                toolWindow.setLocation(dockX + dockWindow.getWidth() + 6,
                        dockY + dockWindow.getHeight() / 2 - toolWindow.getHeight() / 2);
                toolWindow.setVisible(true);
                toolWindow.toFront();
            } else {
                toolWindow.setVisible(false);
            }
        } else {
            dockWindow.setVisible(false);
            toolWindow.setVisible(false);
        }
    }

    private void setExpanded(boolean value) {
        expanded = value;
        trackBall();
    }

    /** 画笔模式状态（开始画笔/停止画笔按钮文案切换）。 */
    public void setPenMode(boolean active) {
        penToggleButton.setText(active ? "停止画笔" : "开始画笔");
    }

    public void dispose() {
        trackTimer.stop();
        dockWindow.dispose();
        toolWindow.dispose();
    }
}

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 多人点名结果动画窗口（透明、置顶）。
 * 中心绘制主球体，每位中奖者以小球形式从主球周围弹出，并持续旋转、浮动；
 * 点击窗口任意位置或超时后自动关闭。
 */
public class MultiPickBallWindow extends JWindow {
    private List<String> winners;
    private int mainRadius;
    private AnimationPanel panel;
    private Timer animationTimer;
    private Timer autoCloseTimer;
    private long startTime;

    private static final long POP_DURATION_MS = 900;      // 小球弹出动画时长（毫秒）
    private static final long AUTO_CLOSE_MS = 15000;      // 超时自动关闭（毫秒）
    private static final Color[] WINNER_COLORS = {
            new Color(255, 99, 71), new Color(60, 179, 113), new Color(255, 165, 0),
            new Color(147, 112, 219), new Color(30, 144, 255), new Color(255, 105, 180),
            new Color(0, 206, 209), new Color(255, 215, 0)
    };

    public MultiPickBallWindow(Window owner, Point centerOnScreen, List<String> winners, int mainRadius) {
        super(owner);
        if (winners == null) {
            winners = new ArrayList<>();
        }
        this.winners = winners;
        this.mainRadius = Math.max(30, mainRadius);
        this.startTime = System.currentTimeMillis();

        setBackground(new Color(0, 0, 0, 0));
        setAlwaysOnTop(true);

        int winnerRadius = Math.max(16, Math.min(30, this.mainRadius * 2 / 3));
        int orbitRadius = this.mainRadius + winnerRadius + 30 + (winners.size() / 8) * 10;
        int size = (orbitRadius + winnerRadius) * 2 + 60;

        panel = new AnimationPanel(winnerRadius, orbitRadius);
        panel.setPreferredSize(new Dimension(size, size));
        setContentPane(panel);
        pack();

        setLocation(centerOnScreen.x - getWidth() / 2, centerOnScreen.y - getHeight() / 2);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dispose();
            }
        });

        animationTimer = new Timer(30, e -> panel.repaint());
        animationTimer.start();

        autoCloseTimer = new Timer((int) AUTO_CLOSE_MS, e -> dispose());
        autoCloseTimer.setRepeats(false);
        autoCloseTimer.start();
    }

    @Override
    public void dispose() {
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
        if (autoCloseTimer != null && autoCloseTimer.isRunning()) {
            autoCloseTimer.stop();
        }
        super.dispose();
    }

    /**
     * 动画绘制面板：主球体 + 围绕旋转的中奖者小球。
     */
    private class AnimationPanel extends JPanel {
        private int winnerRadius;
        private int orbitRadius;

        AnimationPanel(int winnerRadius, int orbitRadius) {
            this.winnerRadius = winnerRadius;
            this.orbitRadius = orbitRadius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            long elapsed = System.currentTimeMillis() - startTime;

            // 弹出进度：缓出曲线（先快后慢）
            double progress = Math.min(1.0, elapsed / (double) POP_DURATION_MS);
            double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
            // 整体旋转速度
            double baseAngle = elapsed / 1000.0 * 0.9;

            // 主球体
            g2d.setColor(new Color(70, 130, 180));
            g2d.fillOval(cx - mainRadius, cy - mainRadius, mainRadius * 2, mainRadius * 2);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("微软雅黑", Font.BOLD, mainRadius / 2));
            drawCenteredString(g2d, "抽", cx, cy);

            // 中奖者小球：围绕主球弹出、旋转、浮动
            int n = winners.size();
            for (int i = 0; i < n; i++) {
                double angle = baseAngle + i * 2.0 * Math.PI / n;
                double radius = eased * orbitRadius;
                double bob = Math.sin(elapsed / 250.0 + i) * 4.0;
                int bx = (int) Math.round(cx + Math.cos(angle) * radius);
                int by = (int) Math.round(cy + Math.sin(angle) * radius + bob);

                g2d.setColor(WINNER_COLORS[i % WINNER_COLORS.length]);
                g2d.fillOval(bx - winnerRadius, by - winnerRadius, winnerRadius * 2, winnerRadius * 2);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("微软雅黑", Font.BOLD, Math.max(10, winnerRadius * 2 / 5)));
                String name = winners.get(i);
                String showName = name.length() > 6 ? name.substring(0, 6) + "…" : name;
                drawCenteredString(g2d, showName, bx, by);
            }

            // 提示文字
            g2d.setColor(new Color(255, 255, 255, 180));
            g2d.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            String hint = "共" + n + "位中奖者，点击任意位置关闭";
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(hint, (getWidth() - fm.stringWidth(hint)) / 2, getHeight() - 10);

            g2d.dispose();
        }

        private void drawCenteredString(Graphics2D g2d, String text, int x, int y) {
            FontMetrics fm = g2d.getFontMetrics();
            int tx = x - fm.stringWidth(text) / 2;
            int ty = y - fm.getHeight() / 2 + fm.getAscent();
            g2d.drawString(text, tx, ty);
        }
    }
}

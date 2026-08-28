import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 多人点名结果动画窗口（透明、置顶）。
 * 中心绘制主球体，每位中奖者以小球形式围绕主球弹出、旋转、浮动；
 * 中奖人数较多时自动排列为多个同心圆环，保证小球互不重叠；
 * 小球运动带有逐渐消失的尾迹；点击窗口任意位置或超时后自动关闭。
 */
public class MultiPickBallWindow extends JWindow {
    private List<String> winners;
    private int mainRadius;
    private int ballOpacity;
    private AnimationPanel panel;
    private Timer animationTimer;
    private Timer autoCloseTimer;
    private long startTime;

    private static final long POP_DURATION_MS = 900;      // 小球弹出动画时长（毫秒）
    private static final long AUTO_CLOSE_MS = 15000;      // 超时自动关闭（毫秒）
    private static final int BALL_GAP = 8;                // 同环相邻小球之间的最小间隙
    private static final double TRAIL_DECAY = 0.86;       // 尾迹衰减系数（越小消失越快）
    private static final Color[] WINNER_COLORS = {
            new Color(255, 99, 71), new Color(60, 179, 113), new Color(255, 165, 0),
            new Color(147, 112, 219), new Color(30, 144, 255), new Color(255, 105, 180),
            new Color(0, 206, 209), new Color(255, 215, 0)
    };

    // 多环布局参数
    private int winnerRadius;
    private double[] ringRadius;    // 每个环的半径
    private double[] ringSpeed;     // 每个环的旋转速度（内环快、外环慢）
    private int[] ballRing;         // 每个中奖者所在环
    private double[] ballAngle;     // 每个中奖者在环内的初始角度

    public MultiPickBallWindow(Window owner, Point centerOnScreen, List<String> winners, int mainRadius) {
        super(owner);
        if (winners == null) {
            winners = new ArrayList<>();
        }
        this.winners = winners;
        this.mainRadius = Math.max(30, mainRadius);
        this.ballOpacity = clampOpacity(ConfigManager.getMultiBallOpacity());
        this.startTime = System.currentTimeMillis();

        setBackground(new Color(0, 0, 0, 0));
        setAlwaysOnTop(true);

        computeMultiRingLayout();

        int maxRingRadius = 0;
        for (double r : ringRadius) {
            maxRingRadius = Math.max(maxRingRadius, (int) Math.round(r));
        }
        int size = (maxRingRadius + winnerRadius) * 2 + 80;

        panel = new AnimationPanel();
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

    /**
     * 计算多环布局：中奖人数多时排列为多个同心圆环，保证小球互不重叠。
     * 每环容量 = 周长 / (小球直径 + 间隙)；环与环之间间隔一个小球直径 + 间隙。
     */
    private void computeMultiRingLayout() {
        int n = winners.size();
        winnerRadius = Math.max(16, Math.min(30, mainRadius * 2 / 3));
        int step = winnerRadius * 2 + BALL_GAP;

        List<Double> radii = new ArrayList<>();
        List<Integer> capacities = new ArrayList<>();
        int total = 0;
        int ring = 0;
        while (total < n) {
            double r = mainRadius + winnerRadius + 30 + ring * (double) step;
            int cap = Math.max(1, (int) Math.floor(2 * Math.PI * r / step));
            radii.add(r);
            capacities.add(cap);
            total += cap;
            ring++;
        }

        int ringCount = radii.size();
        ringRadius = new double[ringCount];
        ringSpeed = new double[ringCount];
        ballRing = new int[n];
        ballAngle = new double[n];

        for (int i = 0; i < ringCount; i++) {
            ringRadius[i] = radii.get(i);
            ringSpeed[i] = Math.max(0.3, 1.0 - 0.12 * i); // 内环快、外环慢
        }

        // 把中奖者依次分配到各环，均匀铺满；相邻环错开半个间隔角，避免视觉重叠
        int ballIndex = 0;
        for (int i = 0; i < ringCount && ballIndex < n; i++) {
            int count = Math.min(capacities.get(i), n - ballIndex);
            double angleStep = 2 * Math.PI / count;
            double stagger = (i % 2 == 0) ? 0 : angleStep / 2;
            for (int k = 0; k < count; k++) {
                ballRing[ballIndex] = i;
                ballAngle[ballIndex] = angleStep * k + stagger;
                ballIndex++;
            }
        }
    }

    private static int clampOpacity(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
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
     * 动画绘制面板：主球体 + 围绕旋转的中奖者小球 + 渐隐尾迹。
     */
    private class AnimationPanel extends JPanel {
        private BufferedImage trailBuffer;
        private BufferedImage fadeScratch;

        AnimationPanel() {
            setOpaque(false);
        }

        private void ensureBuffers() {
            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());
            if (trailBuffer == null || trailBuffer.getWidth() != w || trailBuffer.getHeight() != h) {
                trailBuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }
            if (fadeScratch == null || fadeScratch.getWidth() != w || fadeScratch.getHeight() != h) {
                fadeScratch = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D sg = fadeScratch.createGraphics();
                sg.setColor(new Color(0, 0, 0, (int) (TRAIL_DECAY * 255)));
                sg.fillRect(0, 0, w, h);
                sg.dispose();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            ensureBuffers();

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            long elapsed = System.currentTimeMillis() - startTime;

            // 弹出进度：缓出曲线（先快后慢）
            double progress = Math.min(1.0, elapsed / (double) POP_DURATION_MS);
            double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
            // 整体旋转基准角速度
            double baseAngle = elapsed / 1000.0 * 0.9;

            int n = winners.size();

            // 1) 尾迹衰减：旧内容按 TRAIL_DECAY 系数逐渐变淡，形成“渐渐消失”的效果
            Graphics2D tg = trailBuffer.createGraphics();
            tg.setComposite(AlphaComposite.DstIn);
            tg.drawImage(fadeScratch, 0, 0, null);
            tg.setComposite(AlphaComposite.SrcOver);
            tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 2) 把当前小球位置画进尾迹缓冲
            for (int i = 0; i < n; i++) {
                int bx = ballX(i, cx, eased, baseAngle);
                int by = ballY(i, cy, eased, baseAngle, elapsed);
                tg.setColor(colorOf(i));
                tg.fillOval(bx - winnerRadius, by - winnerRadius, winnerRadius * 2, winnerRadius * 2);
            }
            tg.dispose();

            // 3) 绘制尾迹
            g2d.drawImage(trailBuffer, 0, 0, null);

            // 4) 主球体
            g2d.setColor(new Color(70, 130, 180));
            g2d.fillOval(cx - mainRadius, cy - mainRadius, mainRadius * 2, mainRadius * 2);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("微软雅黑", Font.BOLD, mainRadius / 2));
            drawCenteredString(g2d, "抽", cx, cy);

            // 5) 当前中奖者小球（清晰显示在尾迹之上）
            for (int i = 0; i < n; i++) {
                int bx = ballX(i, cx, eased, baseAngle);
                int by = ballY(i, cy, eased, baseAngle, elapsed);
                g2d.setColor(colorOf(i));
                g2d.fillOval(bx - winnerRadius, by - winnerRadius, winnerRadius * 2, winnerRadius * 2);
                g2d.setColor(new Color(255, 255, 255, ballOpacity));
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

        /** 计算第 i 个小球当前 X 坐标。 */
        private int ballX(int i, int cx, double eased, double baseAngle) {
            double angle = ballAngle[i] + baseAngle * ringSpeed[ballRing[i]];
            double r = eased * ringRadius[ballRing[i]];
            return (int) Math.round(cx + Math.cos(angle) * r);
        }

        /** 计算第 i 个小球当前 Y 坐标（含上下浮动）。 */
        private int ballY(int i, int cy, double eased, double baseAngle, long elapsed) {
            double angle = ballAngle[i] + baseAngle * ringSpeed[ballRing[i]];
            double r = eased * ringRadius[ballRing[i]];
            double bob = Math.sin(elapsed / 250.0 + i) * 4.0;
            return (int) Math.round(cy + Math.sin(angle) * r + bob);
        }

        /** 第 i 个小球的颜色（应用多球透明度设置）。 */
        private Color colorOf(int i) {
            Color base = WINNER_COLORS[i % WINNER_COLORS.length];
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), ballOpacity);
        }

        private void drawCenteredString(Graphics2D g2d, String text, int x, int y) {
            FontMetrics fm = g2d.getFontMetrics();
            int tx = x - fm.stringWidth(text) / 2;
            int ty = y - fm.getHeight() / 2 + fm.getAscent();
            g2d.drawString(text, tx, ty);
        }
    }
}

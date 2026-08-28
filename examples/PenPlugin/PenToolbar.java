import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 画笔工具条：颜色选择、清屏、退出。可拖动，始终置顶。
 */
public class PenToolbar extends JWindow {

    private Point dragStart;

    public PenToolbar(Window owner, PenOverlayWindow overlay) {
        super(owner);
        setAlwaysOnTop(true);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 120, 120), 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        bar.setBackground(new Color(245, 245, 245));

        Color[] colors = {
                Color.BLACK, new Color(220, 30, 30), new Color(30, 90, 220), new Color(30, 160, 60)
        };
        for (Color color : colors) {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(26, 26));
            button.setBackground(color);
            button.setOpaque(true);
            button.setBorderPainted(true);
            button.setToolTipText("画笔颜色");
            button.addActionListener(e -> overlay.setCurrentColor(color));
            bar.add(button);
        }

        bar.add(new JLabel("　"));

        JButton clearButton = new JButton("清屏");
        clearButton.addActionListener(e -> overlay.clearScreen());
        bar.add(clearButton);

        JButton exitButton = new JButton("退出");
        exitButton.addActionListener(e -> overlay.dispose());
        bar.add(exitButton);

        JLabel tipLabel = new JLabel("Esc 退出 · 触屏大面积接触=橡皮 · 鼠标右键=橡皮");
        tipLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        tipLabel.setForeground(new Color(100, 100, 100));
        bar.add(tipLabel);

        // 拖动
        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point now = getLocation();
                setLocation(now.x + e.getX() - dragStart.x, now.y + e.getY() - dragStart.y);
            }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);

        setContentPane(bar);
        pack();

        // 默认停靠屏幕底部中央
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        setLocation(bounds.x + (bounds.width - getWidth()) / 2, bounds.y + bounds.height - getHeight() - 30);
    }
}

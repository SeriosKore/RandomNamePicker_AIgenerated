package com.randomnamepicker.theme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * 自由裁切预览对话框（宿主内建主题，报告 3 决策：手动裁剪框）。
 * 预览区等比显示图片；鼠标按下-拖动画出裁剪框（松手定格），确定后返回源图归一化 (x,y,w,h)。
 */
public class CropSelectorDialog extends JDialog {

    private static final int PREVIEW_W = 560;
    private static final int PREVIEW_H = 380;

    private final BufferedImage image;
    private final PreviewPane preview = new PreviewPane();
    private Rectangle2D.Double crop;

    private CropSelectorDialog(Window owner, BufferedImage image, Rectangle2D.Double initial) {
        super(owner, "自由裁切", ModalityType.APPLICATION_MODAL);
        this.image = image;
        this.crop = initial != null ? initial : (Rectangle2D.Double) ThemeStyle.FULL_CROP.clone();
        setLayout(new BorderLayout(8, 8));
        preview.setPreferredSize(new Dimension(PREVIEW_W, PREVIEW_H));
        add(preview, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        JButton ok = new JButton("确定");
        ok.addActionListener(e -> dispose());
        JButton cancel = new JButton("取消");
        cancel.addActionListener(e -> {
            crop = null;
            dispose();
        });
        bottom.add(ok);
        bottom.add(cancel);
        add(bottom, BorderLayout.SOUTH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(PREVIEW_W + 24, PREVIEW_H + 90);
        setMinimumSize(new Dimension(PREVIEW_W + 24, PREVIEW_H + 90));
        setLocationRelativeTo(owner);
    }

    /** 打开裁剪框；返回新的归一化裁剪框；取消返回 null。 */
    public static Rectangle2D.Double showDialog(Window owner, File imageFile, Rectangle2D.Double initial) {
        BufferedImage img = null;
        if (imageFile != null && imageFile.isFile()) {
            try {
                img = ImageIO.read(imageFile);
            } catch (Exception ignore) {
                img = null;
            }
        }
        if (img == null) {
            JOptionPane.showMessageDialog(owner, "无法读取该图片，请重新导入。", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        CropSelectorDialog dlg = new CropSelectorDialog(owner, img, initial);
        dlg.setVisible(true);
        return dlg.crop;
    }

    /** 图片在预览区的适配区域（等比 contain 居中）。 */
    private Rectangle fitRect() {
        int w = preview.getWidth();
        int h = preview.getHeight();
        if (w <= 0 || h <= 0 || image == null) {
            return new Rectangle(0, 0, w, h);
        }
        int iw = image.getWidth();
        int ih = image.getHeight();
        double scale = Math.min((double) w / iw, (double) h / ih);
        int dw = Math.max(1, (int) Math.round(iw * scale));
        int dh = Math.max(1, (int) Math.round(ih * scale));
        int x = (w - dw) / 2;
        int y = (h - dh) / 2;
        return new Rectangle(x, y, dw, dh);
    }

    private final class PreviewPane extends JPanel {
        Rectangle dragStart;
        Rectangle dragEnd;

        PreviewPane() {
            setBackground(new Color(60, 60, 60));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStart = new Rectangle(e.getX(), e.getY(), 0, 0);
                    dragEnd = dragStart;
                    repaint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    dragEnd = new Rectangle(e.getX(), e.getY(), 0, 0);
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragEnd = new Rectangle(e.getX(), e.getY(), 0, 0);
                    Rectangle imgArea = fitRect();
                    Rectangle sel = selectionRect();
                    if (sel.width >= 4 && sel.height >= 4) {
                        double x = (sel.x - imgArea.x) / (double) Math.max(1, imgArea.width);
                        double y = (sel.y - imgArea.y) / (double) Math.max(1, imgArea.height);
                        double w = sel.width / (double) Math.max(1, imgArea.width);
                        double h = sel.height / (double) Math.max(1, imgArea.height);
                        crop = new Rectangle2D.Double(
                                clamp(x, 0, 1), clamp(y, 0, 1),
                                clamp(w, 0, 1), clamp(h, 0, 1));
                    }
                    dragStart = null;
                    dragEnd = null;
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        Rectangle selectionRect() {
            if (dragStart == null || dragEnd == null) {
                return null;
            }
            int x = Math.min(dragStart.x, dragEnd.x);
            int y = Math.min(dragStart.y, dragEnd.y);
            int w = Math.abs(dragEnd.x - dragStart.x);
            int h = Math.abs(dragEnd.y - dragStart.y);
            return new Rectangle(x, y, w, h);
        }

        double clamp(double v, double lo, double hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) {
                return;
            }
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            Rectangle area = fitRect();
            g2d.drawImage(image, area.x, area.y, area.width, area.height, null);

            Rectangle sel;
            if (crop != null) {
                sel = new Rectangle(
                        area.x + (int) Math.round(crop.x * area.width),
                        area.y + (int) Math.round(crop.y * area.height),
                        (int) Math.round(crop.width * area.width),
                        (int) Math.round(crop.height * area.height));
            } else {
                sel = selectionRect();
            }
            if (sel != null) {
                g2d.setColor(new Color(255, 255, 255, 120));
                g2d.fill(sel);
                g2d.setColor(Color.RED);
                g2d.draw(sel);
            }
            g2d.dispose();
        }
    }
}

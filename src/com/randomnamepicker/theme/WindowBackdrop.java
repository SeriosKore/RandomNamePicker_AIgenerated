package com.randomnamepicker.theme;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

/**
 * 自绘背景组件（置于窗口内容层之下，报告 2 宿主内建主题）。
 * 按适配模式把照片画满本组件区域，并先做“遮罩=半透明白混合”再显示（不新增内容层之上的拦截层）。
 * 解码结果按绝对路径缓存；缩放每帧按 Graphics2D 插值完成（阶段 1 简化版，性能优化留待接线后灰度）。
 */
public class WindowBackdrop extends JComponent {

    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();

    private ThemeStyle style = ThemeStyle.NONE;

    public WindowBackdrop() {
        setOpaque(false);
    }

    public void setStyle(ThemeStyle style) {
        this.style = style != null ? style : ThemeStyle.NONE;
        repaint();
    }

    public ThemeStyle getStyle() {
        return style;
    }

    private BufferedImage loadImage(File file) {
        if (file == null) {
            return null;
        }
        String key = file.getAbsolutePath();
        BufferedImage img = IMAGE_CACHE.get(key);
        if (img != null) {
            return img;
        }
        try {
            BufferedImage loaded = ImageIO.read(file);
            if (loaded != null) {
                IMAGE_CACHE.put(key, loaded);
                return loaded;
            }
        } catch (Exception ignore) {
            // 图片损坏/无法解码：视为无图
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!style.isActive()) {
            return;
        }
        BufferedImage src = loadImage(style.getImageFile());
        if (src == null) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 源矩形（先按归一化裁剪框求，再按模式缩放目标）
        Rectangle2D.Double crop = style.getCrop();
        double crx = clamp01(crop.x);
        double cry = clamp01(crop.y);
        double crw = Math.max(0.001, clamp01(crop.width));
        double crh = Math.max(0.001, clamp01(crop.height));
        if (crx + crw > 1) {
            crw = 1 - crx;
        }
        if (cry + crh > 1) {
            crh = 1 - cry;
        }
        int sx0 = (int) Math.round(crx * sw);
        int sy0 = (int) Math.round(cry * sh);
        int sx1 = (int) Math.round((crx + crw) * sw);
        int sy1 = (int) Math.round((cry + crh) * sh);
        if (sx1 - sx0 <= 0 || sy1 - sy0 <= 0) {
            g2d.dispose();
            return;
        }
        int cw = sx1 - sx0;
        int ch = sy1 - sy0;

        int dx0 = 0;
        int dy0 = 0;
        int dx1 = w;
        int dy1 = h;

        switch (style.getMode()) {
            case CENTER:
                dx0 = Math.max(0, (w - cw) / 2);
                dy0 = Math.max(0, (h - ch) / 2);
                dx1 = dx0 + cw;
                dy1 = dy0 + ch;
                break;
            case FIT: {
                double scale = Math.min((double) w / cw, (double) h / ch);
                int dw = (int) Math.round(cw * scale);
                int dh = (int) Math.round(ch * scale);
                dx0 = (w - dw) / 2;
                dy0 = (h - dh) / 2;
                dx1 = dx0 + dw;
                dy1 = dy0 + dh;
                break;
            }
            case STRETCH:
                break; // 铺满
            case CROP:
            case FILL:
            default: {
                double scale = Math.max((double) w / cw, (double) h / ch);
                int dw = (int) Math.ceil(cw * scale);
                int dh = (int) Math.ceil(ch * scale);
                dx0 = (w - dw) / 2;
                dy0 = (h - dh) / 2;
                dx1 = dx0 + dw;
                dy1 = dy0 + dh;
                break;
            }
        }

        g2d.drawImage(src, dx0, dy0, dx1, dy1, sx0, sy0, sx1, sy1, null);

        // 遮罩：背景图内半透明白混合（报告 3 决策：不做内容层之上的拦截层，不挡鼠标）
        int mask = Math.max(0, Math.min(180, style.getMaskAlpha()));
        if (mask > 0) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, mask / 255f));
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, w, h);
        }
        g2d.dispose();
    }

    private double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0;
        }
        return Math.max(0, Math.min(1, v));
    }
}

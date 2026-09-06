package com.randomnamepicker.theme;

import java.awt.geom.Rectangle2D;
import java.io.File;

/**
 * 窗口背景解析结果（宿主内建主题）。
 * active=false 表示不贴图（未启用/无图/图缺失）；active=true 时按 image/mode/crop/mask 渲染。
 * crop 为源图归一化 (x,y,w,h)（0..1）；mask 为背景图内半透明白混合值（0..180，默认 40，0=关闭）。
 */
public final class ThemeStyle {

    /** 默认归一化裁剪框 = 整图。 */
    public static final Rectangle2D.Double FULL_CROP = new Rectangle2D.Double(0, 0, 1, 1);

    private final boolean active;
    private final File imageFile;
    private final BackdropMode mode;
    private final Rectangle2D.Double crop;
    private final int maskAlpha;

    public static final ThemeStyle NONE = new ThemeStyle(false, null, BackdropMode.FILL, FULL_CROP, 0);

    public ThemeStyle(boolean active, File imageFile, BackdropMode mode,
                      Rectangle2D.Double crop, int maskAlpha) {
        this.active = active;
        this.imageFile = imageFile;
        this.mode = mode != null ? mode : BackdropMode.FILL;
        this.crop = crop != null ? crop : FULL_CROP;
        this.maskAlpha = maskAlpha;
    }

    public boolean isActive() {
        return active;
    }

    public File getImageFile() {
        return imageFile;
    }

    public BackdropMode getMode() {
        return mode;
    }

    public Rectangle2D.Double getCrop() {
        return crop;
    }

    public int getMaskAlpha() {
        return maskAlpha;
    }
}

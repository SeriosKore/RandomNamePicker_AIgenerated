package com.randomnamepicker.theme;

import java.util.Locale;

/**
 * 背景适配模式（宿主内建主题，报告 2/3）。
 * CENTER=居中原尺寸；FIT=适应（等比完整显示）；FILL=填充（等比裁切填满，默认）；
 * STRETCH=拉伸（非等比铺满）；CROP=自由裁切（按归一化裁剪框显示）。
 */
public enum BackdropMode {

    CENTER,
    FIT,
    FILL,
    STRETCH,
    CROP;

    /** 解析配置串（大小写不敏感），失败回退 FILL。 */
    public static BackdropMode parse(String value) {
        if (value != null) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignore) {
                // 回退默认
            }
        }
        return FILL;
    }
}

package com.randomnamepicker.theme;

import java.awt.Window;

/**
 * 窗口背景装配入口（宿主内建主题）：各目标窗口构造尾部一行调用
 * {@code WindowThemeSupport.install(this, ThemeStore.CATEGORY_XXX)}。
 * 豁免窗口（悬浮球/消息弹窗等）不调用本方法即可。
 */
public final class WindowThemeSupport {

    private WindowThemeSupport() {
    }

    /** 装配背景（重复调用幂等；窗口关闭自动清理）。仅 EDT 调用。 */
    public static void install(Window window, String category) {
        BackdropManager.getInstance().install(window, category);
    }
}

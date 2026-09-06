package com.randomnamepicker.plugin;

/**
 * 插件 UI 动作区域（插件 UI 槽一期）。
 * <p>
 * MAIN_MENU / TRAY_MENU / FLOATING_BALL_MENU 对应既有三类菜单能力（归位于统一 Zone 注册表，
 * 供旧 API 便捷方法委托）；SETTINGS_WINDOW 为一期开放的窗口按钮区试点（设置窗“插件”面板）。
 * 二期按需增补其它窗口 Zone（如 MAIN_WINDOW / CONFIG_WINDOW），每新增一个 Zone = 加一个
 * 枚举值 + 对应窗口一处渲染调用，插件 API 无需再改。
 * </p>
 */
public enum UiZone {
    /** 主窗“插件”菜单 */
    MAIN_MENU,
    /** 系统托盘菜单 */
    TRAY_MENU,
    /** 悬浮球右键菜单 */
    FLOATING_BALL_MENU,
    /** 设置窗“插件”按钮区（一期试点） */
    SETTINGS_WINDOW,
    /** 主窗按钮区（二期：按钮阵下方，两列向下） */
    MAIN_WINDOW,
    /** 配置名单窗口按钮区（二期） */
    CONFIG_WINDOW,
    /** 方案管理对话框按钮区（二期） */
    SCHEME_MANAGER_WINDOW,
    /** 数字设置对话框按钮区（二期） */
    NUMBER_PICKER,
    /** 座位设置对话框按钮区（二期） */
    SEAT_PICKER
}

/**
 * 屏幕画笔插件（参考 Seewo 白板悬浮球画笔）。
 * 功能：
 * - 全屏透明画布置顶绘制，书写流畅不卡手（原生指针输入 + 增量渲染 + 局部重绘）；
 * - 笔锋：宽度随速度/压感变化，起笔收笔渐细（Catmull-Rom 平滑 + 变宽轮廓）；
 * - 橡皮：触屏大面积接触（手掌/拳头）自动识别为橡皮；鼠标右键为橡皮；
 * - Esc 或工具条“退出”关闭；工具条可拖动、可选色、可清屏。
 * 入口：主窗口“插件”菜单 / 插件按钮区 / 悬浮球右键菜单 / 托盘菜单。
 */
public class PenPlugin implements Plugin {

    private PluginContext context;
    private PenOverlayWindow overlay;

    @Override
    public String getName() {
        return "屏幕画笔插件";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "参考 Seewo 白板悬浮球画笔：全屏透明画布，笔锋自然、书写流畅；触屏大面积接触自动识别为橡皮，鼠标右键为橡皮。";
    }

    @Override
    public void onLoad(PluginContext context) {
        this.context = context;

        context.registerMainMenuItem("开启/关闭屏幕画笔（插件）", this::toggle);
        context.registerMainButton("屏幕画笔", this::toggle);
        context.registerFloatingBallMenuItem("画笔（插件）", this::toggle);
        context.registerTrayMenuItem("屏幕画笔（插件）", this::toggle);
    }

    @Override
    public void onUnload() {
        closeOverlay();
    }

    private void toggle() {
        if (overlay != null && overlay.isVisible()) {
            closeOverlay();
        } else {
            openOverlay();
        }
    }

    private void openOverlay() {
        try {
            overlay = new PenOverlayWindow();
            PenToolbar toolbar = new PenToolbar(overlay, overlay);
            overlay.setToolbar(toolbar);
            overlay.showOverlay();
            LogManager.log("屏幕画笔插件：画笔模式已开启", "PEN_START");
        } catch (Throwable t) {
            LogManager.log("屏幕画笔插件开启失败: " + t, "PEN_ERROR");
            closeOverlay();
        }
    }

    private void closeOverlay() {
        if (overlay != null) {
            try {
                overlay.dispose();
            } catch (Exception ignore) {
                // 忽略处置异常
            }
            overlay = null;
        }
    }
}

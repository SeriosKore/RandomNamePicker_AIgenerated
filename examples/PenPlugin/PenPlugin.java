import java.awt.*;

/**
 * 屏幕画笔插件（参考 Seewo 白板悬浮球画笔）。
 * 功能：
 * - 画笔入口附着在悬浮球上（铅笔图标 + 可展开/收起工具条），随球移动，不影响悬浮球拖拽与点名；
 * - 全屏透明画布置顶绘制：鼠标左键/触笔/触摸均可书写，书写流畅不卡手
 *   （原生指针输入 + 传统鼠标消息 + 增量渲染 + 局部重绘）；
 * - 笔锋：宽度随速度/压感变化，起笔收笔渐细（Catmull-Rom 平滑 + 变宽轮廓）；
 * - 橡皮：触屏大面积接触（手掌/拳头）自动识别为橡皮；鼠标右键为橡皮；
 * - 工具条提供：颜色选择、开始/停止画笔、清屏、收起；Esc 退出画笔。
 */
public class PenPlugin implements Plugin {

    private PluginContext context;
    private PenOverlayWindow overlay;
    private PenDock dock;
    private Color currentColor = Color.BLACK;

    @Override
    public String getName() {
        return "屏幕画笔插件";
    }

    @Override
    public String getVersion() {
        return "1.1.0";
    }

    @Override
    public String getDescription() {
        return "参考 Seewo 白板悬浮球画笔：工具附着在悬浮球上，全屏透明画布，笔锋自然、书写流畅；触屏大面积接触自动识别为橡皮，鼠标右键为橡皮。";
    }

    @Override
    public void onLoad(PluginContext context) {
        this.context = context;

        context.registerMainMenuItem("开启/关闭屏幕画笔（插件）", this::togglePenMode);
        context.registerMainButton("屏幕画笔", this::togglePenMode);
        context.registerFloatingBallMenuItem("画笔（插件）", this::togglePenMode);
        context.registerTrayMenuItem("屏幕画笔（插件）", this::togglePenMode);

        // 附着在悬浮球上的画笔入口
        dock = new PenDock(this);
    }

    @Override
    public void onUnload() {
        if (dock != null) {
            dock.dispose();
            dock = null;
        }
        closeOverlay();
    }

    /** 供 PenDock 使用：获取主窗口（进而获取悬浮球）。 */
    public NamePickerApp getMainApp() {
        return context.getMainApp();
    }

    public void togglePenMode() {
        if (overlay != null && overlay.isVisible()) {
            closeOverlay();
        } else {
            openOverlay();
        }
    }

    private void openOverlay() {
        try {
            overlay = new PenOverlayWindow();
            overlay.setCurrentColor(currentColor);
            overlay.showOverlay();
            if (dock != null) {
                dock.setPenMode(true);
            }
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
        if (dock != null) {
            dock.setPenMode(false);
        }
    }

    /** 设置画笔颜色（画笔未开启时暂存，开启后应用）。 */
    public void setCurrentColor(Color color) {
        this.currentColor = color;
        if (overlay != null) {
            overlay.setCurrentColor(color);
        }
    }

    /** 清屏。 */
    public void clearScreen() {
        if (overlay != null) {
            overlay.clearScreen();
        }
    }
}

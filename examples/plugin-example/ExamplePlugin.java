import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.mode.ModeHandler;
import com.randomnamepicker.mode.ModeHost;
import com.randomnamepicker.plugin.Plugin;
import com.randomnamepicker.plugin.PluginContext;
import com.randomnamepicker.plugin.UiZone;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

/**
 * Stage3 示例插件（已并入原 SettingsZoneDemoPlugin 功能，2.0，2026-09-05）：
 * - 注册固定串模式（示例插件模式 → Hello from Plugin）；主窗/托盘/悬浮球各加“关于插件”；
 * - 向全部六个窗口按钮区各注册一个“注意”按钮（主窗/设置/配置名单/方案管理/数字/座位），
 *   点击弹出“无运行中热插拔：增删插件/改按钮都要重启”并写日志 DEMO_BUTTON_CLICKED；
 * - 主窗第 4/6 格按钮隐藏（无附加按钮）。
 * 编译打包见本目录 BUILD.txt；插件 jar 只包含本类（与内部类），不得包含 com/randomnamepicker/**。
 */
public class ExamplePlugin implements Plugin {

    /** “注意”弹窗文案（与 UI 槽演示一致）。 */
    public static final String NOTICE_MESSAGE = "无运行中热插拔：增删插件/改按钮都要重启";

    @Override
    public String getName() {
        return "ExamplePlugin";
    }

    @Override
    public String getVersion() {
        return "2.0";
    }

    @Override
    public void onLoad(PluginContext ctx) {
        ctx.registerModeHandler(new ExampleModeHandler(ctx.getModeHost()));

        ActionListener about = e -> JOptionPane.showMessageDialog(
                (Component) ctx.getModeHost().getOwner(),
                "ExamplePlugin 2.0\n已注册插件模式：示例插件模式（结果固定为 Hello from Plugin）\n"
                        + "注意按钮已注册到六个窗口按钮区",
                "关于插件",
                JOptionPane.INFORMATION_MESSAGE);
        ctx.addMainMenuAction("关于插件", about);
        ctx.addTrayMenuAction("关于插件", about);
        ctx.addFloatingBallMenuAction("关于插件", about);

        // 六窗口“注意”按钮（并入原 SettingsZoneDemoPlugin 行为）
        ActionListener notice = e -> {
            ctx.log("注意按钮被点击", "DEMO_BUTTON_CLICKED");
            JOptionPane.showMessageDialog((Component) ctx.getModeHost().getOwner(),
                    NOTICE_MESSAGE, "提示", JOptionPane.INFORMATION_MESSAGE);
        };
        ctx.addUiAction(UiZone.MAIN_WINDOW, "注意", notice);
        ctx.addUiAction(UiZone.SETTINGS_WINDOW, "注意", notice);
        ctx.addUiAction(UiZone.CONFIG_WINDOW, "注意", notice);
        ctx.addUiAction(UiZone.SCHEME_MANAGER_WINDOW, "注意", notice);
        ctx.addUiAction(UiZone.NUMBER_PICKER, "注意", notice);
        ctx.addUiAction(UiZone.SEAT_PICKER, "注意", notice);

        ctx.log("ExamplePlugin 已加载", "PLUGIN_LOADED");
    }

    @Override
    public void onUnload() {
        LogManager.log("ExamplePlugin 卸载", "PLUGIN_UNLOADED");
    }

    /** 示例模式：modeId=example_plugin，displayName=示例插件模式，候选固定串。 */
    public static class ExampleModeHandler extends ModeHandler {
        public ExampleModeHandler(ModeHost host) {
            super(host);
        }

        @Override
        public String getModeId() {
            return "example_plugin";
        }

        @Override
        public String getDisplayName() {
            return "示例插件模式";
        }

        @Override
        public String canPick() {
            return null;
        }

        @Override
        public Supplier<String> nextCandidate() {
            return () -> "Hello from Plugin";
        }

        @Override
        public void handleButton1Click() {
        }

        @Override
        public void handleButton2Click() {
        }

        @Override
        public String getButton1Text() {
            return null; // 无附加按钮 → 主窗第 4 格隐藏（D0.12）
        }

        @Override
        public String getButton2Text() {
            return null; // 无附加按钮 → 主窗第 6 格隐藏（D0.12）
        }
    }
}

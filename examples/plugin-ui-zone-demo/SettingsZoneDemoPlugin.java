import com.randomnamepicker.plugin.Plugin;
import com.randomnamepicker.plugin.PluginContext;
import com.randomnamepicker.plugin.UiZone;
import java.awt.Component;
import java.awt.event.ActionListener;
import javax.swing.JOptionPane;

/**
 * 【已并入 examples/plugin-example/ExamplePlugin（2.0），本文件仅留档，不再单独打包/放入 plugins】
 * 插件 UI 槽一期/二期演示插件：向全部六个窗口注册“注意”按钮…（历史实现，功能已并入 ExamplePlugin 2.0）。
 */
public class SettingsZoneDemoPlugin implements Plugin {

    /** 弹窗文案（二期定稿文本，无“注意！”前缀）。 */
    public static final String CLICK_MESSAGE = "无运行中热插拔：增删插件/改按钮都要重启";

    @Override
    public String getName() {
        return "SettingsZoneDemo";
    }

    @Override
    public String getVersion() {
        return "2.0";
    }

    @Override
    public void onLoad(PluginContext ctx) {
        ActionListener click = e -> {
            ctx.log("演示按钮被点击", "DEMO_BUTTON_CLICKED");
            JOptionPane.showMessageDialog((Component) ctx.getModeHost().getOwner(),
                    CLICK_MESSAGE, "提示", JOptionPane.INFORMATION_MESSAGE);
        };
        // 全部六个窗口按钮区各注册一个“注意”按钮（同一动作）
        ctx.addUiAction(UiZone.MAIN_WINDOW, "注意", click);
        ctx.addUiAction(UiZone.SETTINGS_WINDOW, "注意", click);
        ctx.addUiAction(UiZone.CONFIG_WINDOW, "注意", click);
        ctx.addUiAction(UiZone.SCHEME_MANAGER_WINDOW, "注意", click);
        ctx.addUiAction(UiZone.NUMBER_PICKER, "注意", click);
        ctx.addUiAction(UiZone.SEAT_PICKER, "注意", click);
    }

    @Override
    public void onUnload() {
    }
}

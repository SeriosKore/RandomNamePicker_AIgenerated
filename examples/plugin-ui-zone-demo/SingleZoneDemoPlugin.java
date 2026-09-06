import com.randomnamepicker.plugin.Plugin;
import com.randomnamepicker.plugin.PluginContext;
import com.randomnamepicker.plugin.UiZone;

/**
 * 插件 UI 槽二期隔离演示插件：仅向【主窗 MAIN_WINDOW】注册一个按钮（“插件只需其中某个窗口”样例）。
 * 点击“仅主窗按钮”：写日志 SINGLEZONE_CLICKED（无弹窗，便于自动化与其它窗口隔离断言）。
 * 其它五个窗口（设置/配置名单/方案管理/数字/座位）不应出现本插件任何按钮。
 */
public class SingleZoneDemoPlugin implements Plugin {

    @Override
    public String getName() {
        return "SingleZoneDemo";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void onLoad(PluginContext ctx) {
        ctx.addUiAction(UiZone.MAIN_WINDOW, "仅主窗按钮",
                e -> ctx.log("仅主窗按钮被点击", "SINGLEZONE_CLICKED"));
    }

    @Override
    public void onUnload() {
    }
}

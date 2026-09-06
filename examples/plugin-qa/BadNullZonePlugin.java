import com.randomnamepicker.plugin.Plugin;
import com.randomnamepicker.plugin.PluginContext;

/**
 * QA 坏样例（D8 严格失败）：onLoad 中 addUiAction 传入 zone=null →
 * 抛 IllegalArgumentException → 整 jar 拒载并记 PLUGIN_LOAD_ERROR，其它插件不受影响。
 * 编译打包见 examples/plugin-qa 说明。
 */
public class BadNullZonePlugin implements Plugin {

    public BadNullZonePlugin() {
    }

    @Override
    public String getName() {
        return "BadNullZonePlugin";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void onLoad(PluginContext context) {
        context.addUiAction(null, "不应出现的按钮", e -> {
        });
    }

    @Override
    public void onUnload() {
    }
}

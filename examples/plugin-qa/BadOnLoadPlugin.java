import com.randomnamepicker.plugin.Plugin;
import com.randomnamepicker.plugin.PluginContext;

/**
 * QA 坏样例 (b)：构造正常，onLoad 抛异常 → 整 jar 拒载（暂存丢弃，无半状态）。
 * 编译打包见 examples/plugin-qa/ 说明。
 */
public class BadOnLoadPlugin implements Plugin {

    public BadOnLoadPlugin() {
    }

    @Override
    public String getName() {
        return "BadOnLoadPlugin";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void onLoad(PluginContext context) {
        context.addMainMenuAction("坏插件残留项", e -> {
        }); // 应被暂存并丢弃，不得出现在菜单
        throw new RuntimeException("BadOnLoadPlugin: onLoad 故意抛异常");
    }

    @Override
    public void onUnload() {
        // 拒载回滚时若被调用则不应产生副作用
    }
}

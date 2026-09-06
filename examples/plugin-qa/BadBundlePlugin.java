import com.randomnamepicker.plugin.Plugin;
import com.randomnamepicker.plugin.PluginContext;

/**
 * 坏样例：打包宿主包 class 条目 → 整 jar 拒载 BUNDLED_HOST_CLASS（宿主插件生态一期 D3）。
 * <p>
 * 本类自身实现合法；违规来自随 jar 一起打包的 com/randomnamepicker/probe/BundledHostProbe.class
 * （宿主包前缀条目）。加载时插件管理器先于任何实例化发现该条目 → 整 jar 拒载。
 * </p>
 */
public class BadBundlePlugin implements Plugin {

    @Override
    public String getName() {
        return "BadBundle";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void onLoad(PluginContext context) {
        // 若未触发打包禁令，此处才执行；实际应在加载期即被拒载
        context.log("BadBundlePlugin onLoad（不应到达）", "PLUGIN_LOADED");
    }

    @Override
    public void onUnload() {
    }
}

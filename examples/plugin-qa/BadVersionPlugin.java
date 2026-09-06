import com.randomnamepicker.plugin.Plugin;
import com.randomnamepicker.plugin.PluginContext;

/**
 * 坏样例：Manifest 声明 Api-Level-Min 高于宿主当前级别 → 整 jar 拒载 VERSION_MISMATCH
 * （宿主插件生态一期 D8）。
 * <p>
 * 构建时用随附 BadVersionPlugin.manifest 打包（含 Api-Level-Min: 9999）；
 * 宿主当前 Api-Level = 2，声明不满足 → 在类加载前即拒载，onLoad 不会执行。
 * </p>
 */
public class BadVersionPlugin implements Plugin {

    @Override
    public String getName() {
        return "BadVersion";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void onLoad(PluginContext context) {
        // 若级别校验通过此处才会执行；实际应在加载期被拒载
        context.log("BadVersionPlugin onLoad（不应到达）", "PLUGIN_LOADED");
    }

    @Override
    public void onUnload() {
    }
}

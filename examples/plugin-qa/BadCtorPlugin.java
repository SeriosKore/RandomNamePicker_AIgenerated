import com.randomnamepicker.plugin.Plugin;
import com.randomnamepicker.plugin.PluginContext;

/**
 * QA 坏样例 (a)：public 无参构造抛异常 → 实例化失败 → 整 jar 拒载。
 * 编译打包见 examples/plugin-qa/ 说明。
 */
public class BadCtorPlugin implements Plugin {

    public BadCtorPlugin() {
        throw new RuntimeException("BadCtorPlugin: 构造器故意抛异常");
    }

    @Override
    public String getName() {
        return "BadCtorPlugin";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void onLoad(PluginContext context) {
        // 不会执行到
    }

    @Override
    public void onUnload() {
        // 不会执行到
    }
}

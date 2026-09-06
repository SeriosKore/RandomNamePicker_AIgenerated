import com.randomnamepicker.mode.ModeHandler;
import com.randomnamepicker.mode.ModeHost;
import com.randomnamepicker.plugin.Plugin;
import com.randomnamepicker.plugin.PluginContext;
import java.util.function.Supplier;

/**
 * QA 坏样例 (c)：注册与内置冲突的模式（modeId=name_list、displayName=名字列表模式）
 * → 提交阶段冲突 → 整 jar 拒载。编译打包见 examples/plugin-qa/ 说明。
 */
public class ConflictPlugin implements Plugin {

    public ConflictPlugin() {
    }

    @Override
    public String getName() {
        return "ConflictPlugin";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void onLoad(PluginContext context) {
        context.addMainMenuAction("冲突插件菜单项", e -> {
        }); // 与冲突模式同属一个事务，也应一并丢弃
        context.registerModeHandler(new ConflictModeHandler(context.getModeHost()));
    }

    @Override
    public void onUnload() {
        // 拒载回滚时若被调用则不应产生副作用
    }

    /** 故意与内置名字列表模式冲突的 Handler。 */
    public static class ConflictModeHandler extends ModeHandler {
        public ConflictModeHandler(ModeHost host) {
            super(host);
        }

        @Override
        public String getModeId() {
            return "name_list";
        }

        @Override
        public String getDisplayName() {
            return "名字列表模式";
        }

        @Override
        public String canPick() {
            return null;
        }

        @Override
        public Supplier<String> nextCandidate() {
            return () -> "冲突";
        }

        @Override
        public void handleButton1Click() {
        }

        @Override
        public void handleButton2Click() {
        }

        @Override
        public String getButton1Text() {
            return null;
        }

        @Override
        public String getButton2Text() {
            return null;
        }
    }
}

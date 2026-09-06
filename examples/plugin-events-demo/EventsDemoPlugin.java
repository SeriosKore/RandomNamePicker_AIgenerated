import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.mode.ModeHandler;
import com.randomnamepicker.mode.ModeHost;
import com.randomnamepicker.plugin.HostEvent;
import com.randomnamepicker.plugin.HostEventListener;
import com.randomnamepicker.plugin.Plugin;
import com.randomnamepicker.plugin.PluginContext;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

/**
 * 宿主事件订阅演示插件（宿主插件生态一期；examples/plugin-events-demo）。
 * <p>
 * 演示内容：
 * <ul>
 *   <li>订阅宿主事件（PICK_STARTED/PICK_FINISHED、SCHEME_CHANGED、MODE_CHANGED、PLUGINS_CHANGED、
 *       LOCK_CHANGED、BALL_SHOWN、BALL_HIDDEN、BALL_MOVED）→ 写日志（操作码 EVENT_DEMO），onUnload 显式退订
 *       （宿主亦会按 jar+实现类归属自动清理，双保险）；</li>
 *   <li>注册一个带“模式专属悬浮球右键项”的插件模式（G4 口子演示）；</li>
 *   <li>Manifest 声明 Api-Level-Min: 2（随附 EventsDemo.manifest），演示版本契约通过场景。</li>
 * </ul>
 */
public class EventsDemoPlugin implements Plugin {

    private static final String NAME = "事件演示";
    private static final String VERSION = "1.0";

    private PluginContext ctx;
    private volatile boolean inactive = false;
    private final HostEventListener listener = this::onEvent;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public void onLoad(PluginContext context) {
        this.ctx = context;
        context.addHostEventListener(listener);
        context.registerModeHandler(new EventsDemoMode(context.getModeHost()));
        context.log("事件演示插件已加载（Api-Level-Min: 2 声明应通过）", "PLUGIN_LOADED");
    }

    @Override
    public void onUnload() {
        inactive = true;
        PluginContext c = ctx;
        ctx = null;
        if (c != null) {
            c.removeHostEventListener(listener);
        }
    }

    private void onEvent(HostEvent event) {
        if (inactive || event == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        switch (event.getType()) {
            case PICK_STARTED:
            case PICK_FINISHED:
                sb.append("抽取事件[").append(event.getSource()).append("] ")
                        .append(event.getType()).append(" scheme=").append(event.getSchemeName())
                        .append(" mode=").append(event.getModeId());
                if (event.getType() == HostEvent.Type.PICK_FINISHED) {
                    sb.append(" result=").append(event.getResult());
                }
                break;
            case SCHEME_CHANGED:
                sb.append("方案切换 name=").append(event.getSchemeName())
                        .append(" type=").append(event.getSchemeType());
                break;
            case MODE_CHANGED:
                sb.append("模式切换 id=").append(event.getModeId())
                        .append(" display=").append(event.getModeDisplayName());
                break;
            case LOCK_CHANGED:
                sb.append("锁状态 change locked=").append(event.getLocked());
                break;
            case PLUGINS_CHANGED:
                sb.append("插件集变化 outcomes=").append(event.getOutcomes().size());
                break;
            case BALL_SHOWN:
            case BALL_HIDDEN:
                sb.append("悬浮球 ").append(event.getType()).append(" bounds=").append(event.getBounds());
                break;
            case BALL_MOVED:
                sb.append("悬浮球移动 old=").append(event.getOldBounds())
                        .append(" new=").append(event.getNewBounds());
                break;
            default:
                sb.append("事件 ").append(event.getType());
                break;
        }
        log(sb.toString());
    }

    private void log(String detail) {
        PluginContext c = ctx;
        if (c != null) {
            c.log(detail, "EVENT_DEMO");
        } else {
            LogManager.log(detail, "EVENT_DEMO");
        }
    }

    /** 演示模式：固定串候选 + 模式专属悬浮球右键项（G4）。 */
    private static class EventsDemoMode extends ModeHandler {

        EventsDemoMode(ModeHost host) {
            super(host);
        }

        @Override
        public String getModeId() {
            return "events_demo_mode";
        }

        @Override
        public String getDisplayName() {
            return "事件演示模式";
        }

        @Override
        public String canPick() {
            return null;
        }

        @Override
        public Supplier<String> nextCandidate() {
            return () -> "事件演示候选";
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

        @Override
        public List<ModeMenuItem> getContextMenuItems() {
            List<ModeMenuItem> items = new ArrayList<>();
            items.add(new ModeMenuItem("事件演示：模式专属项", e -> {
                try {
                    LogManager.log("事件演示模式专属项被点击", "EVENT_DEMO_MENU");
                    JOptionPane.showMessageDialog((Component) host.getOwner(),
                            "这是事件演示模式的悬浮球右键专属项。", "事件演示",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Throwable ignore) {
                    // 仅演示
                }
            }));
            return Collections.unmodifiableList(items);
        }
    }
}

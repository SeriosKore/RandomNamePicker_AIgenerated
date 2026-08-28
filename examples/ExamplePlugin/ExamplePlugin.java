import javax.swing.*;
import java.awt.*;

/**
 * 示例插件：演示全部标准扩展点与 UI 篡改接口。
 * 构建后放入程序运行目录的 extensions/ 文件夹即可自动加载，无需修改主程序。
 */
public class ExamplePlugin implements Plugin {

    private PluginContext context;

    @Override
    public String getName() {
        return "示例插件";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "演示插件：主菜单、插件按钮、托盘菜单、悬浮球菜单、设置面板与 UI 篡改接口。";
    }

    @Override
    public void onLoad(PluginContext context) {
        this.context = context;

        // 标准扩展点 1：主窗口“插件”菜单
        context.registerMainMenuItem("示例：打招呼", () ->
                JOptionPane.showMessageDialog(null,
                        "你好，来自示例插件！",
                        "示例插件",
                        JOptionPane.INFORMATION_MESSAGE));

        // 标准扩展点 2：主窗口底部插件按钮区
        context.registerMainButton("显示当前方案", () -> {
            Scheme scheme = context.getCurrentScheme();
            JOptionPane.showMessageDialog(null,
                    scheme != null
                            ? "当前方案：" + scheme.getName() + "，抽取模式：" + context.getCurrentMode()
                            : "未选择方案",
                    "示例插件",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        // 标准扩展点 3：系统托盘菜单
        context.registerTrayMenuItem("示例插件：版本信息", () ->
                JOptionPane.showMessageDialog(null,
                        getName() + " v" + getVersion(),
                        "示例插件",
                        JOptionPane.INFORMATION_MESSAGE));

        // 标准扩展点 4：悬浮球右键菜单
        context.registerFloatingBallMenuItem("示例插件：统计名单人数", () -> {
            Scheme scheme = context.getCurrentScheme();
            int size = scheme == null
                    ? 0
                    : context.getNameManager().loadNamesForScheme(scheme.getName()).size();
            JOptionPane.showMessageDialog(null,
                    "当前方案名单共 " + size + " 人",
                    "示例插件",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        // 标准扩展点 5：设置窗口插件面板
        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        settingsPanel.add(new JLabel("这是示例插件的设置面板（由插件动态注入）"));
        context.registerSettingsPanel("示例插件设置", settingsPanel);

        // ---- UI 篡改接口演示 ----

        // ① 注入自定义菜单到主窗口菜单栏
        JMenu menu = new JMenu("示例插件");
        JMenuItem aboutItem = new JMenuItem("关于");
        aboutItem.addActionListener(e ->
                JOptionPane.showMessageDialog(null,
                        getDescription(),
                        "示例插件",
                        JOptionPane.INFORMATION_MESSAGE));
        menu.add(aboutItem);
        context.addMainMenu(menu);

        // ② 向主窗口底部注入组件
        JLabel injectedLabel = new JLabel("由示例插件注入");
        injectedLabel.setForeground(new Color(180, 60, 60));
        context.registerMainComponent(injectedLabel);

        // ③ 修改主窗口中央显示文本（如需体验可取消下面注释）
        // context.setMainDisplayText("示例插件已加载！点击“开始抽取”体验新功能。");

        // ④ 高级篡改入口：可直接拿到主窗口实例自由改造 UI
        // NamePickerApp app = context.getMainApp();
    }

    @Override
    public void onUnload() {
        // 示例插件无需释放资源
    }
}

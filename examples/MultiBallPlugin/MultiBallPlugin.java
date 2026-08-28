import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.security.SecureRandom;
import java.util.List;
import java.util.Properties;

/**
 * 多悬浮球插件（示例插件）。
 * 当“单次抽取数量”大于 1 时接管悬浮球的名字抽取：
 * 主球滚动后，中奖者小球围绕主球弹出、旋转、浮动（渐隐尾迹、多环防重叠），
 * 多球透明度可在设置窗口的插件面板中调节。
 * 构建后放入程序目录 extensions/ 即可自动加载，无需修改主程序。
 */
public class MultiBallPlugin implements Plugin {

    private static final String CONFIG_PATH = "data/multi_ball_plugin.properties";

    private PluginContext context;
    private MultiPickBallWindow currentWindow;
    private int ballOpacity = 200;
    private JSlider opacitySlider;
    private JLabel opacityValueLabel;

    @Override
    public String getName() {
        return "多悬浮球插件(示例插件)";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "示例插件：多人点名时以“主球 + 中奖者小球”动画展示（弹出/旋转/浮动、渐隐尾迹、多环防重叠、透明度可调）。";
    }

    @Override
    public void onLoad(PluginContext context) {
        this.context = context;
        loadConfig();

        // 扩展点：拦截悬浮球名字抽取——数量 > 1 时接管并展示多球动画
        context.registerFloatingBallPickHandler((ball, scheme, names, count) -> {
            if (count <= 1) {
                return false;
            }
            performMultiPick(ball, scheme, names, count);
            return true;
        });

        // 扩展点：悬浮球右键菜单
        context.registerFloatingBallMenuItem("多球点名（示例插件）", this::startFromMenu);

        // 扩展点：设置窗口插件面板（多球透明度）
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panel.add(new JLabel("多球透明度："));
        opacitySlider = new JSlider(SwingConstants.HORIZONTAL, 50, 255, ballOpacity);
        opacitySlider.setMajorTickSpacing(50);
        opacitySlider.setMinorTickSpacing(25);
        opacitySlider.setPaintTicks(true);
        opacitySlider.setPaintLabels(true);
        opacityValueLabel = new JLabel();
        updateOpacityLabel();
        opacitySlider.addChangeListener(e -> {
            ballOpacity = opacitySlider.getValue();
            updateOpacityLabel();
            saveConfig();
        });
        panel.add(opacitySlider);
        panel.add(opacityValueLabel);
        context.registerSettingsPanel("多悬浮球设置（示例插件）", panel);
    }

    @Override
    public void onUnload() {
        if (currentWindow != null) {
            try {
                currentWindow.dispose();
            } catch (Exception ignore) {
                // 忽略处置异常
            }
            currentWindow = null;
        }
    }

    /** 悬浮球右键菜单入口：立即执行一次多球点名。 */
    private void startFromMenu() {
        Scheme scheme = context.getCurrentScheme();
        if (scheme == null) {
            JOptionPane.showMessageDialog(null, "请先选择一个方案！", getName(), JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> names = context.getNameManager().loadNamesForScheme(scheme.getName());
        if (names.isEmpty()) {
            JOptionPane.showMessageDialog(null, "当前方案名单为空！", getName(), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int count = Math.min(Math.max(context.getPickCount(), 1), names.size());
        FloatingBall ball = context.getMainApp().getFloatingBall();
        if (ball == null) {
            JOptionPane.showMessageDialog(null, "请先在主窗口点击“悬浮球”开启悬浮球！", getName(), JOptionPane.WARNING_MESSAGE);
            return;
        }
        performMultiPick(ball, scheme, names, count);
    }

    /** 主球滚动 → 抽取去重结果 → 弹出多球动画。 */
    private void performMultiPick(FloatingBall ball, Scheme scheme, List<String> names, int count) {
        SecureRandom random = new SecureRandom();
        final int[] counter = {0};
        final int maxIterations = 20;

        Timer rolling = new Timer(50, e -> {
            if (counter[0] < maxIterations) {
                String name = names.get(random.nextInt(names.size()));
                ball.setDisplayText(name.length() > 5 ? name.substring(0, 5) + "..." : name);
                counter[0]++;
            } else {
                ((Timer) e.getSource()).stop();
                List<String> winners = NamePickerApp.pickDistinct(names, count, random);
                LogManager.log(scheme.getName() + "-多人抽取(插件)=" + String.join("、", winners), "抽取结果");
                showAnimation(ball, winners);
            }
        });
        rolling.start();
    }

    private void showAnimation(FloatingBall ball, List<String> winners) {
        try {
            if (currentWindow != null) {
                try {
                    currentWindow.dispose();
                } catch (Exception ignore) {
                    // 忽略处置异常
                }
                currentWindow = null;
            }
            Point location = ball.getLocationOnScreen();
            int centerX = location.x + ball.getWidth() / 2;
            int centerY = location.y + ball.getHeight() / 2;
            currentWindow = new MultiPickBallWindow(ball, new Point(centerX, centerY),
                    winners, ball.getBallRadius(), ballOpacity);
            currentWindow.setVisible(true);
        } catch (Exception e) {
            LogManager.log(getName() + "动画失败: " + e, "PLUGIN_ERROR");
            ball.setDisplayText(winners.size() <= 3
                    ? String.join("、", winners)
                    : winners.size() + "位中奖者");
        }
    }

    private void loadConfig() {
        try {
            File configFile = new File(CONFIG_PATH);
            if (configFile.exists()) {
                Properties properties = new Properties();
                FileInputStream fis = new FileInputStream(configFile);
                properties.load(fis);
                fis.close();
                ballOpacity = clamp(Integer.parseInt(properties.getProperty("ballOpacity", "200")), 50, 255);
            }
        } catch (Exception e) {
            ballOpacity = 200;
        }
    }

    private void saveConfig() {
        try {
            File configFile = new File(CONFIG_PATH);
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            Properties properties = new Properties();
            properties.setProperty("ballOpacity", String.valueOf(ballOpacity));
            FileOutputStream fos = new FileOutputStream(configFile);
            properties.store(fos, "MultiBallPlugin Config");
            fos.close();
        } catch (Exception e) {
            // 配置保存失败不影响插件功能
        }
    }

    private void updateOpacityLabel() {
        String text;
        if (ballOpacity < 100) {
            text = ballOpacity + " (高透明)";
        } else if (ballOpacity < 180) {
            text = ballOpacity + " (半透明)";
        } else {
            text = ballOpacity + " (低透明)";
        }
        opacityValueLabel.setText(text);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}

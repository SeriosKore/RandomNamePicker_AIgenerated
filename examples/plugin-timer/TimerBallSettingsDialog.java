import com.randomnamepicker.core.LogManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

/**
 * “计时子球设置”对话框（正计时子球开发方案 §3.3）。
 * <p>
 * 由设置窗“插件”区“计时子球”按钮打开（owner=按钮所在 Window，模态）。
 * 控件：尺寸模式（随悬浮球自动 / 固定直径滑杆 30–90）、三态配色（色块→JColorChooser）、
 * 与悬浮球间距（4–20）、恢复默认。任何修改：更新 Config → 立即持久化 → 实时应用
 * （applyChanges 由调用方注入，用于让已显示的计时子球即时换肤/重排）。
 * </p>
 * 本类全部操作在 EDT（对话框模态运行）。
 */
public class TimerBallSettingsDialog extends JDialog {

    private final TimerBallConfig cfg;
    private final Runnable applyChanges;

    private final JRadioButton autoRadio =
            new JRadioButton("随悬浮球自动（直径×0.55，夹 36–60px）");
    private final JRadioButton fixedRadio = new JRadioButton("固定直径：");
    private final JSlider fixedSlider = new JSlider(SwingConstants.HORIZONTAL,
            TimerBallConfig.FIXED_DIAMETER_MIN, TimerBallConfig.FIXED_DIAMETER_MAX,
            TimerBallConfig.DEFAULT_FIXED_DIAMETER);
    private final JLabel fixedValueLabel = new JLabel();
    private final JSlider gapSlider = new JSlider(SwingConstants.HORIZONTAL,
            TimerBallConfig.GAP_PX_MIN, TimerBallConfig.GAP_PX_MAX,
            TimerBallConfig.DEFAULT_GAP_PX);
    private final JLabel gapValueLabel = new JLabel();
    private final JButton colorReadyBtn = new JButton();
    private final JButton colorRunningBtn = new JButton();
    private final JButton colorPausedBtn = new JButton();
    private boolean dirty = false;

    public TimerBallSettingsDialog(Window owner, TimerBallConfig cfg, Runnable applyChanges) {
        super(owner, "计时子球设置", ModalityType.APPLICATION_MODAL);
        this.cfg = cfg;
        this.applyChanges = applyChanges;
        initialize();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
    }

    private void initialize() {
        setLayout(new BorderLayout(10, 10));
        setSize(460, 520);
        setMinimumSize(new Dimension(420, 400));
        setLocationRelativeTo(getOwner());

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        main.add(createSizePanel());
        main.add(Box.createVerticalStrut(8));
        main.add(createColorPanel());
        main.add(Box.createVerticalStrut(8));
        main.add(createGapPanel());

        JScrollPane scroll = new JScrollPane(main);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        JButton resetButton = new JButton("恢复默认");
        resetButton.addActionListener(e -> onResetDefaults());
        JButton doneButton = new JButton("完成");
        doneButton.addActionListener(e -> {
            onClose();
            dispose();
        });
        bottom.add(resetButton);
        bottom.add(doneButton);
        add(bottom, BorderLayout.SOUTH);

        refreshControlsFromConfig();
    }

    // ===================== 面板构建 =====================

    private JPanel titledPanel(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new TitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 13)));
        return p;
    }

    private JPanel createSizePanel() {
        JPanel panel = titledPanel("子球尺寸");
        ButtonGroup group = new ButtonGroup();
        group.add(autoRadio);
        group.add(fixedRadio);

        autoRadio.setFont(font13());
        autoRadio.addActionListener(e -> commit());
        fixedRadio.setFont(font13());
        fixedRadio.addActionListener(e -> commit());

        JPanel fixedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fixedRow.add(fixedRadio);
        styleSlider(fixedSlider);
        fixedSlider.setPreferredSize(new Dimension(180, 50));
        fixedSlider.addChangeListener(e -> {
            fixedValueLabel.setText(fixedSlider.getValue() + " px");
            commit();
        });
        fixedValueLabel.setFont(font12());
        fixedValueLabel.setPreferredSize(new Dimension(70, 20));
        fixedRow.add(fixedSlider);
        fixedRow.add(fixedValueLabel);
        fixedRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        panel.add(autoRadio);
        panel.add(Box.createVerticalStrut(4));
        panel.add(fixedRow);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        return panel;
    }

    private JPanel createColorPanel() {
        JPanel panel = titledPanel("配色（就绪 / 运行 / 暂停）");
        panel.add(colorRow("就绪色：", colorReadyBtn, "就绪"));
        panel.add(Box.createVerticalStrut(2));
        panel.add(colorRow("运行色：", colorRunningBtn, "运行"));
        panel.add(Box.createVerticalStrut(2));
        panel.add(colorRow("暂停色：", colorPausedBtn, "暂停"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        return panel;
    }

    private JPanel colorRow(String label, JButton swatch, String stateName) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel lab = new JLabel(label);
        lab.setFont(font13());
        lab.setPreferredSize(new Dimension(90, 24));
        swatch.setFont(font12());
        swatch.setFocusPainted(false);
        swatch.setContentAreaFilled(true);
        swatch.setOpaque(true);
        swatch.setBorderPainted(true);
        swatch.setPreferredSize(new Dimension(150, 28));
        swatch.addActionListener(e -> chooseColor(stateName, swatch));
        row.add(lab);
        row.add(swatch);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return row;
    }

    private JPanel createGapPanel() {
        JPanel panel = titledPanel("与悬浮球间距");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel lab = new JLabel("间距：");
        lab.setFont(font13());
        lab.setPreferredSize(new Dimension(90, 24));
        styleSlider(gapSlider);
        gapSlider.setPreferredSize(new Dimension(180, 50));
        gapSlider.addChangeListener(e -> {
            gapValueLabel.setText(gapSlider.getValue() + " px");
            commit();
        });
        gapValueLabel.setFont(font12());
        gapValueLabel.setPreferredSize(new Dimension(70, 20));
        row.add(lab);
        row.add(gapSlider);
        row.add(gapValueLabel);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        panel.add(row);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        return panel;
    }

    private Font font13() {
        return new Font("微软雅黑", Font.PLAIN, 13);
    }

    private Font font12() {
        return new Font("微软雅黑", Font.PLAIN, 12);
    }

    private void styleSlider(JSlider slider) {
        slider.setMajorTickSpacing(10);
        slider.setMinorTickSpacing(5);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setFont(font11());
    }

    private Font font11() {
        return new Font("微软雅黑", Font.PLAIN, 11);
    }

    // ===================== 交互 =====================

    /** 任意变更：写回 Config → 持久化 → 实时应用。 */
    private void commit() {
        applyControlsToConfig();
        cfg.save();
        dirty = true;
        if (applyChanges != null) {
            applyChanges.run();
        }
    }

    private void applyControlsToConfig() {
        cfg.setSizeMode(autoRadio.isSelected() ? TimerBallConfig.SizeMode.AUTO
                : TimerBallConfig.SizeMode.FIXED);
        cfg.setFixedDiameter(fixedSlider.getValue());
        cfg.setGapPx(gapSlider.getValue());
    }

    private void chooseColor(String stateName, JButton swatch) {
        Color current = colorOf(stateName);
        Color picked = JColorChooser.showDialog(this, "选择" + stateName + "色", current);
        if (picked == null) {
            return;
        }
        switch (stateName) {
            case "就绪":
                cfg.setColorReady(picked);
                break;
            case "运行":
                cfg.setColorRunning(picked);
                break;
            default:
                cfg.setColorPaused(picked);
                break;
        }
        cfg.save();
        dirty = true;
        if (applyChanges != null) {
            applyChanges.run();
        }
        updateSwatch(stateName, picked);
    }

    private Color colorOf(String stateName) {
        switch (stateName) {
            case "就绪":
                return cfg.getColorReady();
            case "运行":
                return cfg.getColorRunning();
            default:
                return cfg.getColorPaused();
        }
    }

    private void onResetDefaults() {
        cfg.resetToDefaults();
        cfg.save();
        dirty = true;
        if (applyChanges != null) {
            applyChanges.run();
        }
        refreshControlsFromConfig();
    }

    private void onClose() {
        if (dirty) {
            LogManager.log("正计时子球-设置已保存 " + summary(), "TIMER_CONFIG");
            dirty = false;
        }
    }

    private String summary() {
        String mode = cfg.isFixed() ? "固定" + cfg.getFixedDiameter() + "px"
                : "自动(×0.55)";
        return "尺寸=" + mode + " 间距=" + cfg.getGapPx() + "px"
                + " 色=" + TimerBallConfig.toHex(cfg.getColorReady()) + "/"
                + TimerBallConfig.toHex(cfg.getColorRunning()) + "/"
                + TimerBallConfig.toHex(cfg.getColorPaused());
    }

    // ===================== 控件同步 =====================

    private void refreshControlsFromConfig() {
        autoRadio.setSelected(!cfg.isFixed());
        fixedRadio.setSelected(cfg.isFixed());
        fixedSlider.setValue(cfg.getFixedDiameter());
        fixedValueLabel.setText(cfg.getFixedDiameter() + " px");
        gapSlider.setValue(cfg.getGapPx());
        gapValueLabel.setText(cfg.getGapPx() + " px");
        updateSwatch("就绪", cfg.getColorReady());
        updateSwatch("运行", cfg.getColorRunning());
        updateSwatch("暂停", cfg.getColorPaused());
    }

    private void updateSwatch(String stateName, Color c) {
        JButton btn;
        switch (stateName) {
            case "就绪":
                btn = colorReadyBtn;
                break;
            case "运行":
                btn = colorRunningBtn;
                break;
            default:
                btn = colorPausedBtn;
                break;
        }
        btn.setBackground(c);
        btn.setForeground(contrast(c));
        btn.setText(TimerBallConfig.toHex(c));
        btn.setToolTipText("点击选择颜色（当前 " + TimerBallConfig.toHex(c) + "）");
    }

    /** 根据亮度选择前景黑/白，保证色块文字可读。 */
    private Color contrast(Color c) {
        double lum = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
        return lum > 150 ? Color.BLACK : Color.WHITE;
    }

    @Override
    public void dispose() {
        onClose();
        super.dispose();
    }
}

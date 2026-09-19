package com.randomnamepicker.ui;

import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.mode.NumberModeHandler;
import com.randomnamepicker.model.NumberRange;
import com.randomnamepicker.plugin.UiZone;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

/**
 * 数字抽取设置（P0 修复批次调整后）。
 * <p>
 * 相对旧版的行为变化（均属已批准口径）：
 * <ul>
 *   <li><b>T1</b>：构造参数由 {@code Frame} 改为 {@code Window}，且父窗必须是
 *       {@link NamePickerApp}（宿主 {@code getHostWindow()}）——旧写法把
 *       {@code ModeHost.getOwner()}（顶级主窗恒为 null）转型存为 mainApp，会在构造期
 *       对 null 调 {@code getSchemeManager()} 抛 NPE，导致主窗“设置数字范围”完全打不开；</li>
 *   <li><b>B1</b>：点“停止”不再把结果标签重置为提示语——定格数字保留可见；</li>
 *   <li><b>T2.4</b>：对话框内抽取定格后按主窗口同口径补记 {@code 抽取结果} 日志；</li>
 *   <li><b>Q-e</b>：允许负数与单值范围（{@code min == max}），只禁止 {@code min > max}；</li>
 *   <li><b>跨度上限</b>：保存/抽取时校验 {@code max-min+1 ≤ 2^31-1}，超限提示；</li>
 *   <li><b>Q-f</b>：显式保存语义——只有“保存设置/应用到方案”才落盘；关窗有未保存修改时二次确认。</li>
 * </ul>
 * </p>
 */
public class NumberPicker extends JDialog {

    /** 取值跨度上限（含端点）：max - min + 1 ≤ 2^31-1，保证 nextInt(bound) 不溢出。 */
    private static final long MAX_SPAN = Integer.MAX_VALUE;

    private JTextField minField;
    private JTextField maxField;
    private JLabel resultLabel;
    private JButton pickButton;
    private JButton saveButton;
    private JButton applyButton;
    private java.util.Random random;
    private Timer timer;
    private boolean isPicking = false;
    private NamePickerApp mainApp;
    private String schemeName;
    /** 最近一次滚显的候选（定格日志用；与标签文本解耦）。 */
    private String lastResult;
    /** 上次成功落盘（或载入）的范围文本：未保存修改判断与回显用。 */
    private String committedMinText = "";
    private String committedMaxText = "";

    public NumberPicker(Window parent, String schemeName) {
        // Window-owner 版 JDialog 没有 (Window,String,boolean) 构造；APPLICATION_MODAL 与原
        // (Frame,String,true) 的模态语义一致（阻塞本应用全部顶层窗）
        super(parent, "数字抽取设置", Dialog.ModalityType.APPLICATION_MODAL);
        // T1：父窗必须是宿主主窗；fail-fast 取代“半构造对象 + 延迟 NPE”
        if (!(parent instanceof NamePickerApp)) {
            throw new IllegalArgumentException("NumberPicker 需要 NamePickerApp 宿主窗口，实际收到: " + parent);
        }
        this.mainApp = (NamePickerApp) parent;
        this.schemeName = schemeName;
        random = new java.util.Random();
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setupCloseBehavior();
        loadCurrentRange();
    }

    private void initializeComponents() {
        setSize(400, 250);
        setLocationRelativeTo(getParent());
        setResizable(false);

        minField = new JTextField(10);
        maxField = new JTextField(10);
        resultLabel = new JLabel("设置数字范围后可进行抽取", SwingConstants.CENTER);
        resultLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        resultLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        pickButton = new JButton("开始抽取");
        saveButton = new JButton("保存设置");
        applyButton = new JButton("应用到方案");
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("数字范围设置"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("最小值:"), gbc);
        gbc.gridx = 1;
        inputPanel.add(minField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        inputPanel.add(new JLabel("最大值:"), gbc);
        gbc.gridx = 1;
        inputPanel.add(maxField, gbc);

        add(inputPanel, BorderLayout.NORTH);
        add(resultLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(pickButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(applyButton);

        // 插件 UI 槽二期：数字设置对话框插件按钮区（按钮行下方；无动作则不渲染）
        JPanel pluginZonePanel = PluginUiSupport.createZonePanel(UiZone.NUMBER_PICKER);
        if (pluginZonePanel == null) {
            add(buttonPanel, BorderLayout.SOUTH);
        } else {
            JPanel southWrap = new JPanel();
            southWrap.setLayout(new BoxLayout(southWrap, BoxLayout.Y_AXIS));
            southWrap.add(buttonPanel);
            southWrap.add(pluginZonePanel);
            add(southWrap, BorderLayout.SOUTH);
        }
    }

    private void setupEventHandlers() {
        pickButton.addActionListener(e -> togglePick());
        saveButton.addActionListener(e -> saveSettings());
        applyButton.addActionListener(e -> applyToScheme());
    }

    /** Q-f：显式保存语义下的关窗保护（有未保存修改先确认）。 */
    private void setupCloseBehavior() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (hasUnsavedChanges()) {
                    int r = JOptionPane.showConfirmDialog(NumberPicker.this,
                            "当前数字范围修改尚未保存，确定放弃并关闭？", "提示",
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (r != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                dispose();
            }
        });
    }

    private void loadCurrentRange() {
        NumberRange range = mainApp.getSchemeManager().getNumberRange(schemeName);
        if (range != null) {
            minField.setText(String.valueOf(range.getMin()));
            maxField.setText(String.valueOf(range.getMax()));
        } else {
            minField.setText("1");
            maxField.setText("100");
        }
        committedMinText = minField.getText().trim();
        committedMaxText = maxField.getText().trim();
    }

    private boolean hasUnsavedChanges() {
        return !minField.getText().trim().equals(committedMinText)
                || !maxField.getText().trim().equals(committedMaxText);
    }

    private void togglePick() {
        if (isPicking) {
            stopPicking();
        } else {
            startPicking();
        }
    }

    private void startPicking() {
        int min, max;
        try {
            min = Integer.parseInt(minField.getText().trim());
            max = Integer.parseInt(maxField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "请输入有效的数字！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String invalid = validateRange(min, max);
        if (invalid != null) {
            JOptionPane.showMessageDialog(this, invalid, "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        isPicking = true;
        pickButton.setText("停止");
        lastResult = null;

        final int fMin = min;
        final int fMax = max;
        timer = new Timer(50, e -> {
            int randomNum = nextInRange(fMin, fMax);
            lastResult = String.valueOf(randomNum);
            resultLabel.setText("抽取结果: " + randomNum);
        });
        timer.start();
    }

    private void stopPicking() {
        isPicking = false;
        pickButton.setText("开始抽取");
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        // B1：定格结果保留在标签上（不再重置为提示语）；T2.4：按主窗口同口径记日志
        if (lastResult != null) {
            LogManager.log(schemeName + "-" + NumberModeHandler.DISPLAY_NAME + "=" + lastResult, "抽取结果");
        }
    }

    /**
     * Q-e/T4：{@code min > max} 才非法（{@code min == max} 合法、负数合法）；
     * 跨度须 ≤ 2^31-1，避免 nextInt 溢出。返回 null 表示合法。
     */
    private String validateRange(int min, int max) {
        if (min > max) {
            return "最小值不能大于最大值！";
        }
        long span = (long) max - (long) min + 1L;
        if (span > MAX_SPAN) {
            return "范围过大：取值跨度需 ≤ " + (MAX_SPAN - 1L) + "（当前 " + span + "）";
        }
        return null;
    }

    /** 区间内随机整数；跨度以 long 计算，单值/负数均安全（T4）。 */
    private int nextInRange(int min, int max) {
        long span = (long) max - (long) min + 1L;
        if (span <= 1L) {
            return min;
        }
        if (span <= Integer.MAX_VALUE) {
            return random.nextInt((int) span) + min;
        }
        return (int) (min + Math.floorMod(random.nextLong(), span));
    }

    private void saveSettings() {
        saveSettings(true);
    }

    /** 保存范围；notify=false 时不弹“设置已保存”（供“应用到方案”合并提示用）。成功返回 true。 */
    private boolean saveSettings(boolean notify) {
        int min, max;
        try {
            min = Integer.parseInt(minField.getText().trim());
            max = Integer.parseInt(maxField.getText().trim());
        } catch (NumberFormatException e) {
            LogManager.log("数字范围设置失败-" + e.getMessage(), "错误");
            JOptionPane.showMessageDialog(this, "请输入有效的数字！", "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        String invalid = validateRange(min, max);
        if (invalid != null) {
            JOptionPane.showMessageDialog(this, invalid, "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        NumberRange range = new NumberRange(min, max);
        mainApp.getSchemeManager().saveNumberRange(schemeName, range);
        LogManager.log(schemeName + "-[" + min + "," + max + "]", "保存数字范围");
        committedMinText = minField.getText().trim();
        committedMaxText = maxField.getText().trim();
        if (notify) {
            JOptionPane.showMessageDialog(this, "设置已保存！", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
        mainApp.updateDisplayText();
        return true;
    }

    private void applyToScheme() {
        // 先真保存；失败（含非法范围）直接返回，不再提示“已应用”
        if (!saveSettings(false)) {
            return;
        }
        LogManager.log(schemeName, "应用数字范围到方案");
        JOptionPane.showMessageDialog(this, "设置已应用到方案：" + schemeName + "（已保存）", "提示",
                JOptionPane.INFORMATION_MESSAGE);
    }
}

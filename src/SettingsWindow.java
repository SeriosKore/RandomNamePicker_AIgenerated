import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class SettingsWindow extends JDialog {
    private NamePickerApp mainApp;
    private JCheckBox autoStartCheckBox;
    private JCheckBox minimizeToTrayCheckBox;
    private JButton exportLogButton;
    private JButton viewModificationLogButton;
    private JSlider radiusSlider;
    private JSlider opacitySlider;
    private JLabel radiusValueLabel;
    private JLabel opacityValueLabel;
    private JButton lockButton;
    private JButton changePasswordButton;
    private JTextField pickCountField;
    private JButton applyPickCountButton;
    private JLabel pickCountHintLabel;

    public SettingsWindow(NamePickerApp parent) {
        super(parent, "系统设置", true);
        this.mainApp = parent;
        initializeComponents();
        setupLayout();
        loadSettings();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void initializeComponents() {
        setSize(600, 550);
        setLocationRelativeTo(getParent());
        setMinimumSize(new Dimension(500, 450));

        autoStartCheckBox = new JCheckBox("启用后程序将在系统启动时自动运行");
        autoStartCheckBox.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        autoStartCheckBox.addActionListener(e -> {
            boolean enabled = autoStartCheckBox.isSelected();
            ConfigManager.setAutoStart(enabled);
            
            if (enabled) {
                Main.registerAutoStart();
            } else {
                Main.unregisterAutoStart();
            }
        });

        minimizeToTrayCheckBox = new JCheckBox("关闭窗口时最小化到系统托盘（悬浮球继续运行）");
        minimizeToTrayCheckBox.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        minimizeToTrayCheckBox.addActionListener(e -> {
            ConfigManager.setMinimizeToTray(minimizeToTrayCheckBox.isSelected());
        });

        exportLogButton = new JButton("导出日志文件");
        exportLogButton.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        exportLogButton.addActionListener(e -> exportLog());

        viewModificationLogButton = new JButton("查看名单修改日志");
        viewModificationLogButton.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        viewModificationLogButton.addActionListener(e -> showModificationLog());

        radiusSlider = new JSlider(SwingConstants.HORIZONTAL, 30, 100, 50);
        radiusSlider.setMajorTickSpacing(20);
        radiusSlider.setMinorTickSpacing(10);
        radiusSlider.setPaintTicks(true);
        radiusSlider.setPaintLabels(true);
        radiusSlider.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        radiusValueLabel = new JLabel("50 像素");
        radiusValueLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        opacitySlider = new JSlider(SwingConstants.HORIZONTAL, 50, 255, 200);
        opacitySlider.setMajorTickSpacing(50);
        opacitySlider.setMinorTickSpacing(25);
        opacitySlider.setPaintTicks(true);
        opacitySlider.setPaintLabels(true);
        opacitySlider.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        opacityValueLabel = new JLabel("200 (半透明)");
        opacityValueLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        lockButton = new JButton("锁定/解锁配置");
        lockButton.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        lockButton.addActionListener(e -> toggleLock());

        changePasswordButton = new JButton("修改密码");
        changePasswordButton.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        changePasswordButton.addActionListener(e -> changePassword());

        pickCountField = new JTextField(5);
        pickCountField.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        applyPickCountButton = new JButton("应用");
        applyPickCountButton.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        applyPickCountButton.addActionListener(e -> applyPickCount());

        pickCountHintLabel = new JLabel("范围：1 ~ 当前名单总人数");
        pickCountHintLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        pickCountHintLabel.setForeground(Color.GRAY);

    }

    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel startupPanel = createStartupPanel();
        startupPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        mainPanel.add(startupPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel appearancePanel = createAppearancePanel();
        appearancePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        mainPanel.add(appearancePanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel pickPanel = createPickPanel();
        pickPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        mainPanel.add(pickPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel logPanel = createLogPanel();
        logPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        mainPanel.add(logPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel securityPanel = createSecurityPanel();
        securityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        mainPanel.add(securityPanel);

        // 插件扩展点：追加插件注册的设置面板
        if (mainApp.getPluginManager() != null) {
            for (PluginManager.SettingsPanelSpec spec : mainApp.getPluginManager().getSettingsPanelSpecs()) {
                mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
                JPanel pluginPanel = new JPanel(new BorderLayout());
                pluginPanel.setBorder(new TitledBorder(
                        BorderFactory.createLineBorder(Color.GRAY, 1),
                        spec.title,
                        TitledBorder.LEFT,
                        TitledBorder.TOP,
                        new Font("微软雅黑", Font.BOLD, 13)
                ));
                pluginPanel.add(spec.panel, BorderLayout.CENTER);
                pluginPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
                mainPanel.add(pluginPanel);
            }
        }

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        radiusSlider.addChangeListener(e -> {
            int radius = radiusSlider.getValue();
            radiusValueLabel.setText(radius + " 像素");
            saveFloatingBallSettings();
        });

        opacitySlider.addChangeListener(e -> {
            int opacity = opacitySlider.getValue();
            String opacityText;
            if (opacity < 100) {
                opacityText = opacity + " (高透明)";
            } else if (opacity < 180) {
                opacityText = opacity + " (半透明)";
            } else {
                opacityText = opacity + " (低透明)";
            }
            opacityValueLabel.setText(opacityText);
            saveFloatingBallSettings();
        });
    }

    private JPanel createStartupPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                "启动与关闭设置",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 13)
        ));

        JPanel autoStartPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        autoStartPanel.add(autoStartCheckBox);
        autoStartPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JPanel minimizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        minimizePanel.add(minimizeToTrayCheckBox);
        minimizePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        panel.add(autoStartPanel);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));
        panel.add(minimizePanel);

        return panel;
    }

    private JPanel createAppearancePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                "悬浮球外观",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 13)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("半径："), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(radiusSlider, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(radiusValueLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("透明度："), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(opacitySlider, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(opacityValueLabel, gbc);

        return panel;
    }

    private JPanel createPickPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                "多人点名设置",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 13)
        ));

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inputPanel.add(new JLabel("单次抽取数量："));
        inputPanel.add(pickCountField);
        inputPanel.add(applyPickCountButton);
        inputPanel.add(pickCountHintLabel);
        inputPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        panel.add(inputPanel);

        JLabel tipLabel = new JLabel("提示：仅对名字列表模式生效；数量为 1 时悬浮球保持单球效果，大于 1 时以多球动画展示全部中奖者。");
        tipLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        tipLabel.setForeground(Color.GRAY);
        JPanel tipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tipPanel.add(tipLabel);
        tipPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        panel.add(tipPanel);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panel.setBorder(new TitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                "日志管理",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 13)
        ));
        panel.add(exportLogButton);
        panel.add(viewModificationLogButton);
        return panel;
    }

    private JPanel createSecurityPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panel.setBorder(new TitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                "安全设置",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 13)
        ));
        panel.add(lockButton);
        panel.add(changePasswordButton);
        return panel;
    }

    private void loadSettings() {
        // 与注册表实际状态同步（注册表被外部修改时以注册表为准）
        boolean registryEnabled = Main.checkAutoStartStatus();
        if (registryEnabled != ConfigManager.isAutoStartEnabled()) {
            ConfigManager.setAutoStart(registryEnabled);
        }
        autoStartCheckBox.setSelected(registryEnabled);

        minimizeToTrayCheckBox.setSelected(ConfigManager.isMinimizeToTray());
        int radius = ConfigManager.getFloatingBallRadius();
        radiusSlider.setValue(radius);
        radiusValueLabel.setText(radius + " 像素");

        int opacity = ConfigManager.getFloatingBallOpacity();
        opacitySlider.setValue(opacity);
        String opacityText;
        if (opacity < 100) {
            opacityText = opacity + " (高透明)";
        } else if (opacity < 180) {
            opacityText = opacity + " (半透明)";
        } else {
            opacityText = opacity + " (低透明)";
        }
        opacityValueLabel.setText(opacityText);

        // 单次抽取数量：若名单人数变化后设置值超限，自动修正为当前最大值并提示
        int pickCount = ConfigManager.getPickCount();
        if (pickCount < 1) {
            pickCount = 1;
            ConfigManager.setPickCount(pickCount);
        }
        int maxPickCount = getCurrentMaxPickCount();
        if (pickCount > maxPickCount) {
            pickCount = maxPickCount;
            ConfigManager.setPickCount(pickCount);
            // 延迟到设置窗口显示后再提示，避免在构造函数中弹出模态框
            final int corrected = pickCount;
            final int totalAtTime = getCurrentNameListTotal();
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(SettingsWindow.this,
                            "当前名单总人数为 " + totalAtTime + "，单次抽取数量已自动修正为 " + corrected + "。",
                            "提示",
                            JOptionPane.INFORMATION_MESSAGE));
        }
        pickCountField.setText(String.valueOf(pickCount));
        updatePickCountHint();

        updateLockButtonText();
    }

    /**
     * 当前方案名单总人数。
     */
    private int getCurrentNameListTotal() {
        return mainApp.getCurrentNameListSize();
    }

    /**
     * 单次抽取数量允许的最大值：当前名单总人数（至少为 1）。
     */
    private int getCurrentMaxPickCount() {
        return Math.max(1, getCurrentNameListTotal());
    }

    private void updatePickCountHint() {
        pickCountHintLabel.setText("范围：1 ~ 当前名单总人数（当前名单 " + getCurrentNameListTotal() + " 人）");
    }

    /**
     * 应用单次抽取数量设置：非数字、小于 1、大于名单总人数均拒绝并提示。
     */
    private void applyPickCount() {
        String text = pickCountField.getText().trim();
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "单次抽取数量必须为数字！",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            pickCountField.setText(String.valueOf(ConfigManager.getPickCount()));
            return;
        }

        if (value < 1) {
            JOptionPane.showMessageDialog(this,
                    "单次抽取数量不能小于 1！",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            pickCountField.setText(String.valueOf(ConfigManager.getPickCount()));
            return;
        }

        int total = getCurrentNameListTotal();
        int maxPickCount = getCurrentMaxPickCount();
        if (value > maxPickCount) {
            if (total == 0) {
                JOptionPane.showMessageDialog(this,
                        "当前方案名单为空，请先配置名单，单次抽取数量最多为 1！",
                        "提示",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "单次抽取数量不能超过当前名单总人数（" + total + "）！",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
            pickCountField.setText(String.valueOf(ConfigManager.getPickCount()));
            return;
        }

        ConfigManager.setPickCount(value);
        JOptionPane.showMessageDialog(this,
                "单次抽取数量已设置为 " + value + "。",
                "提示",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 查看名单修改日志（modification_log.txt）。
     */
    private void showModificationLog() {
        JDialog dialog = new JDialog(this, "名单修改日志", true);
        dialog.setSize(650, 450);
        dialog.setLocationRelativeTo(this);

        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        textArea.setText(ModificationLogManager.readLog());
        textArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textArea);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton closeButton = new JButton("关闭");
        closeButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void toggleLock() {
        if (PasswordManager.isLocked()) {
            PasswordDialog passwordDialog = new PasswordDialog((Frame) getParent());
            passwordDialog.setVisible(true);

            if (passwordDialog.isPasswordVerified()) {
                PasswordManager.unlock();
                JOptionPane.showMessageDialog(this,
                        "已解锁！现在可以修改配置了。",
                        "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                updateLockButtonText();
            }
        } else {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "确定要锁定配置吗？\n锁定后需要密码才能修改配置。",
                    "确认锁定",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                PasswordManager.lock();
                JOptionPane.showMessageDialog(this,
                        "已锁定！修改配置需要密码验证。",
                        "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                updateLockButtonText();
            }
        }
    }

    private void updateLockButtonText() {
        if (PasswordManager.isLocked()) {
            lockButton.setText("🔒 当前已锁定（点击解锁）");
        } else {
            lockButton.setText("🔓 当前已解锁（点击锁定）");
        }
    }

    private void changePassword() {
        if (PasswordManager.isLocked()) {
            JOptionPane.showMessageDialog(this,
                    "请先解锁后再修改密码！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        OldPasswordDialog oldDialog = new OldPasswordDialog((Frame) getParent());
        oldDialog.setVisible(true);

        if (!oldDialog.isPasswordVerified()) {
            return;
        }

        NewPasswordDialog newDialog = new NewPasswordDialog((Frame) getParent());
        newDialog.setVisible(true);
    }

    private void saveFloatingBallSettings() {
        int radius = radiusSlider.getValue();
        int opacity = opacitySlider.getValue();

        ConfigManager.setFloatingBallRadius(radius);
        ConfigManager.setFloatingBallOpacity(opacity);
    }

    private void exportLog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        fileChooser.setSelectedFile(new java.io.File("Modifylog_export.txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File destFile = fileChooser.getSelectedFile();
                if (!destFile.getName().toLowerCase().endsWith(".txt")) {
                    destFile = new java.io.File(destFile.getAbsolutePath() + ".txt");
                }

                java.nio.file.Files.copy(
                        new java.io.File(LogManager.LOG_FILE_PATH).toPath(),
                        destFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );

                JOptionPane.showMessageDialog(this, "日志已成功导出到：\n" + destFile.getAbsolutePath(),
                        "导出成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "导出失败：" + ex.getMessage(), "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

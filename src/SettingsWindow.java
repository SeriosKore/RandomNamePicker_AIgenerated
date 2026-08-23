import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;

public class SettingsWindow extends JDialog {
    private JCheckBox autoStartCheckBox;
    private JCheckBox minimizeToTrayCheckBox;
    private JButton exportLogButton;
    private JSlider radiusSlider;
    private JSlider opacitySlider;
    private JLabel radiusValueLabel;
    private JLabel opacityValueLabel;
    private JButton lockButton;
    private JButton changePasswordButton;

    public SettingsWindow(JFrame parent) {
        super(parent, "系统设置", true);
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

        JPanel logPanel = createLogPanel();
        logPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        mainPanel.add(logPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel securityPanel = createSecurityPanel();
        securityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        mainPanel.add(securityPanel);

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
        autoStartCheckBox.setSelected(ConfigManager.isAutoStartEnabled());
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

        updateLockButtonText();
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

    private boolean isAutoStartEnabled() {
        return Main.checkAutoStartStatus();
    }
}

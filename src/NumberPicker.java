import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class NumberPicker extends JDialog {
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

    public NumberPicker(Frame parent, String schemeName) {
        super(parent, "数字抽取设置", true);
        this.mainApp = (NamePickerApp) parent;
        this.schemeName = schemeName;
        random = new java.util.Random();
        initializeComponents();
        setupLayout();
        setupEventHandlers();
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
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        pickButton.addActionListener(e -> togglePick());
        saveButton.addActionListener(e -> saveSettings());
        applyButton.addActionListener(e -> applyToScheme());
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

            if (min >= max) {
                JOptionPane.showMessageDialog(this, "最小值必须小于最大值！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "请输入有效的数字！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        isPicking = true;
        pickButton.setText("停止");

        timer = new Timer(50, e -> {
            int randomNum = random.nextInt(max - min + 1) + min;
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
        resultLabel.setText("设置数字范围后可进行抽取");
    }

    private void saveSettings() {
        try {
            int min = Integer.parseInt(minField.getText().trim());
            int max = Integer.parseInt(maxField.getText().trim());

            if (min >= max) {
                JOptionPane.showMessageDialog(this, "最小值必须小于最大值！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            NumberRange range = new NumberRange(min, max);
            mainApp.getSchemeManager().saveNumberRange(schemeName, range);
            LogManager.log(schemeName + "-[" + min + "," + max + "]", "保存数字范围");
            JOptionPane.showMessageDialog(this, "设置已保存！", "提示", JOptionPane.INFORMATION_MESSAGE);
            mainApp.updateDisplayText();
        } catch (NumberFormatException e) {
            LogManager.log("数字范围设置失败-" + e.getMessage(), "错误");
            JOptionPane.showMessageDialog(this, "请输入有效的数字！", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyToScheme() {
        saveSettings();
        LogManager.log(schemeName, "应用数字范围到方案");
        JOptionPane.showMessageDialog(this, "设置已应用到方案：" + schemeName, "提示", JOptionPane.INFORMATION_MESSAGE);
    }
}

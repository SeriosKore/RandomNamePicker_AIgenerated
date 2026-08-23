import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.SecureRandom;
import java.util.List;

public class NamePickerApp extends JFrame {
    private NameManager nameManager;
    private SchemeManager schemeManager;
    private JLabel displayLabel;
    private JButton pickButton;
    private JButton configButton;
    private JButton floatingButton;
    private JButton schemeManageButton;
    private JButton settingsButton;
    private JButton modeButton1;
    private JButton modeButton2;
    private JComboBox<Scheme> schemeComboBox;
    private JComboBox<String> modeComboBox;
    private Timer timer;
    private SecureRandom random;
    private boolean isPicking = false;
    private SeatPicker seatPicker;
    private FloatingBall floatingBall;
    private boolean isFloatingBallVisible = false;
    private ModeHandler currentModeHandler;


    public NamePickerApp() {
        nameManager = new NameManager();
        schemeManager = new SchemeManager();
        random = new SecureRandom();
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadSchemes();
        updateModeSpecificButtons();
        restoreLastScheme();
        setupWindowCloseBehavior();
    }

    private void restoreLastScheme() {
        String lastSchemeName = ConfigManager.getLastScheme();
        LogManager.log("尝试恢复方案: [" + lastSchemeName + "]", "DEBUG_RESTORE");
        
        if (lastSchemeName != null && !lastSchemeName.isEmpty()) {
            for (int i = 0; i < schemeComboBox.getItemCount(); i++) {
                Scheme scheme = schemeComboBox.getItemAt(i);
                LogManager.log("检查方案[" + i + "]: " + scheme.getName(), "DEBUG_CHECK");
                if (scheme.getName().equals(lastSchemeName)) {
                    schemeComboBox.setSelectedIndex(i);
                    LogManager.log("成功恢复方案: " + lastSchemeName + " 索引:" + i, "SCHEME_RESTORED");
                    return;
                }
            }
            LogManager.log("未找到方案: [" + lastSchemeName + "]", "SCHEME_NOT_FOUND");
        } else {
            LogManager.log("没有上次保存的方案", "NO_LAST_SCHEME");
        }
    }

    private void setupWindowCloseBehavior() {
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (ConfigManager.isMinimizeToTray()) {
                    setVisible(false);
                } else {
                    Main.cleanupAndExit();
                }
            }
        });
    }

    private void initializeComponents() {
        setTitle("多功能随机抽取器");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);
        setResizable(true);

        displayLabel = new JLabel("请选择方案和模式后开始抽取", SwingConstants.CENTER);
        displayLabel.setFont(new Font("微软雅黑", Font.BOLD, 20));
        displayLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        pickButton = new JButton("开始抽取");
        configButton = new JButton("配置名单");
        floatingButton = new JButton("悬浮球");
        schemeManageButton = new JButton("方案管理");
        settingsButton = new JButton("设置");

        modeButton1 = new JButton();
        modeButton2 = new JButton();

        schemeComboBox = new JComboBox<>();
        modeComboBox = new JComboBox<>(new String[]{"名字列表模式", "数字模式", "座位模式"});
        modeComboBox.setToolTipText("选择抽取模式");
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // 顶部方案选择
        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.add(new JLabel("预设方案:"));
        topPanel.add(schemeComboBox);
        topPanel.add(new JLabel("抽取模式:"));
        topPanel.add(modeComboBox);
        topPanel.add(schemeManageButton);
        topPanel.add(settingsButton);
        add(topPanel, BorderLayout.NORTH);

        add(displayLabel, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buttonPanel.add(pickButton);
        buttonPanel.add(configButton);
        buttonPanel.add(floatingButton);
        buttonPanel.add(modeButton1);
        buttonPanel.add(new JPanel());
        buttonPanel.add(modeButton2);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        pickButton.addActionListener(e -> togglePick());
        configButton.addActionListener(e -> showConfigWindow());
        floatingButton.addActionListener(e -> showFloatingBall());
        schemeManageButton.addActionListener(e -> showSchemeManager());
        settingsButton.addActionListener(e -> showSettingsWindow());
        modeComboBox.addActionListener(e -> {
            onModeChanged();
            updateModeSpecificButtons();
        });
        schemeComboBox.addActionListener(e -> {
            onSchemeChanged();
            updateModeSpecificButtons();
        });
    }

    private void loadSchemes() {
        schemeComboBox.removeAllItems();
        schemeComboBox.addItem(new Scheme("默认方案", "name_list"));
        for (Scheme scheme : schemeManager.getAllSchemes()) {
            schemeComboBox.addItem(scheme);
        }
    }

    private void onSchemeChanged() {
        Scheme selectedScheme = (Scheme) schemeComboBox.getSelectedItem();
        LogManager.log("方案改变事件触发: " + (selectedScheme != null ? selectedScheme.getName() : "null"), "DEBUG_SCHEME_CHANGE");
        
        if (selectedScheme != null) {
            ConfigManager.setLastScheme(selectedScheme.getName());
            LogManager.log("已保存方案: " + selectedScheme.getName(), "DEBUG_SCHEME_SAVED");
            
            switch (selectedScheme.getType()) {
                case "name_list":
                    modeComboBox.setSelectedItem("名字列表模式");
                    break;
                case "number":
                    modeComboBox.setSelectedItem("数字模式");
                    break;
                case "seat":
                    modeComboBox.setSelectedItem("座位模式");
                    break;
            }
        }
    }

    private void onModeChanged() {
        String selectedMode = (String) modeComboBox.getSelectedItem();
        if (selectedMode != null) {
            updateDisplayText();
        }
    }

    private void updateModeSpecificButtons() {
        String selectedMode = (String) modeComboBox.getSelectedItem();

        if (selectedMode == null) {
            modeButton1.setVisible(false);
            modeButton2.setVisible(false);
            return;
        }

        switch (selectedMode) {
            case "名字列表模式":
                currentModeHandler = new NameListModeHandler(this);
                break;
            case "数字模式":
                currentModeHandler = new NumberModeHandler(this);
                break;
            case "座位模式":
                currentModeHandler = new SeatModeHandler(this);
                break;
            default:
                currentModeHandler = null;
        }

        if (currentModeHandler != null) {
            modeButton1.setVisible(true);
            modeButton2.setVisible(true);

            modeButton1.setText(currentModeHandler.getButton1Text());
            modeButton2.setText(currentModeHandler.getButton2Text());

            // 移除旧的监听器
            for (ActionListener al : modeButton1.getActionListeners()) {
                modeButton1.removeActionListener(al);
            }
            for (ActionListener al : modeButton2.getActionListeners()) {
                modeButton2.removeActionListener(al);
            }

            // 添加新的监听器
            modeButton1.addActionListener(e -> currentModeHandler.handleButton1Click());
            modeButton2.addActionListener(e -> currentModeHandler.handleButton2Click());
        } else {
            modeButton1.setVisible(false);
            modeButton2.setVisible(false);
        }

        revalidate();
        repaint();
    }

    public void updateDisplayText() {
        String selectedMode = (String) modeComboBox.getSelectedItem();
        if (selectedMode != null) {
            switch (selectedMode) {
                case "名字列表模式":
                    displayLabel.setText("名字列表模式: 点击开始抽取");
                    break;
                case "数字模式":
                    displayLabel.setText("数字模式: 点击开始抽取");
                    break;
                case "座位模式":
                    displayLabel.setText("座位模式: 点击开始抽取");
                    break;
                default:
                    displayLabel.setText("请选择方案和模式后开始抽取");
            }
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
        String selectedMode = (String) modeComboBox.getSelectedItem();
        Scheme selectedScheme = (Scheme) schemeComboBox.getSelectedItem();

        if (selectedScheme == null) {
            JOptionPane.showMessageDialog(this, "请选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (selectedMode == null) {
            JOptionPane.showMessageDialog(this, "请选择抽取模式！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        isPicking = true;
        pickButton.setText("停止");

        switch (selectedMode) {
            case "名字列表模式":
                startNamePicking(selectedScheme);
                break;
            case "数字模式":
                startNumberPicking(selectedScheme);
                break;
            case "座位模式":
                startSeatPicking(selectedScheme);
                break;
        }
        //抽取日志记录（被废除）
        //LogManager.log(selectedScheme.getName() + "-" + selectedMode, "开始抽取");
    }

    private void startNamePicking(Scheme scheme) {
        List<String> names = nameManager.loadNamesForScheme(scheme.getName());
        
        if (names.isEmpty()) {
            JOptionPane.showMessageDialog(this, "名单已损坏，请重新导入", "错误", JOptionPane.ERROR_MESSAGE);
            stopPicking();
            return;
        }

        timer = new Timer(50, e -> {
            if (!names.isEmpty()) {
                String randomName = names.get(random.nextInt(names.size()));
                displayLabel.setText(randomName);
            }
        });
        timer.start();
    }

    private void startNumberPicking(Scheme scheme) {
        NumberRange range = schemeManager.getNumberRange(scheme.getName());
        if (range == null) {
            JOptionPane.showMessageDialog(this, "请先设置数字范围！", "提示", JOptionPane.WARNING_MESSAGE);
            stopPicking();
            return;
        }

        timer = new Timer(50, e -> {
            int randomNum = random.nextInt(range.getMax() - range.getMin() + 1) + range.getMin();
            displayLabel.setText(String.valueOf(randomNum));
        });
        timer.start();
    }

    private void startSeatPicking(Scheme scheme) {
        SeatConfig config = schemeManager.getSeatConfig(scheme.getName());
        if (config == null || config.getSelectedSeats().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先设置座位并选择座位！", "提示", JOptionPane.WARNING_MESSAGE);
            stopPicking();
            return;
        }

        timer = new Timer(50, e -> {
            Point randomSeat = config.getSelectedSeats().get(random.nextInt(config.getSelectedSeats().size()));
            displayLabel.setText("(" + randomSeat.x + ", " + randomSeat.y + ")");
        });
        timer.start();
    }

    private void stopPicking() {
        isPicking = false;
        pickButton.setText("开始抽取");
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        String result = displayLabel.getText();
        String selectedMode = (String) modeComboBox.getSelectedItem();
        Scheme currentScheme = getCurrentScheme();
        
        if (currentScheme != null && selectedMode != null) {
            LogManager.log(currentScheme.getName() + "-" + selectedMode + "=" + result, "抽取结果");
        }

    }

    public void showConfigWindow() {
        ConfigWindow configWindow = new ConfigWindow(this, nameManager, schemeManager);
        configWindow.setVisible(true);
        LogManager.log("名单管理", "打开配置窗口");
    }

    private void showFloatingBall() {
        // 不管什么状态，先强制清理旧实例
        if (floatingBall != null) {
            try {
                floatingBall.dispose();
            } catch (Exception e) {
                // 忽略处置异常
            }
            floatingBall = null;
        }

        if (isFloatingBallVisible) {
            // 已经可见，现在要隐藏
            isFloatingBallVisible = false;
            floatingButton.setText("悬浮球");
        } else {
            // 需要显示新实例
            try {
                floatingBall = new FloatingBall(this);
                floatingBall.setVisible(true);
                isFloatingBallVisible = true;
                floatingButton.setText("隐藏悬浮球");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "无法创建悬浮球: " + e.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showSchemeManager() {
        SchemeManagerDialog dialog = new SchemeManagerDialog(this, schemeManager);
        dialog.setVisible(true);
        loadSchemes();
    }

    public void showSettingsWindow() {
        SettingsWindow settingsWindow = new SettingsWindow(this);
        settingsWindow.setVisible(true);
    }

    public NameManager getNameManager() {
        return nameManager;
    }

    public SchemeManager getSchemeManager() {
        return schemeManager;
    }

    public Scheme getCurrentScheme() {
        return (Scheme) schemeComboBox.getSelectedItem();
    }

    public String getCurrentMode() {
        return (String) modeComboBox.getSelectedItem();
    }
    public void hideFloatingBall() {
        if (floatingBall != null) {
            floatingBall.dispose();
            floatingBall = null;
        }
        isFloatingBallVisible = false;
        if (floatingButton != null) {
            floatingButton.setText("悬浮球");
        }
    }
    
    public void toggleFloatingBall() {
        showFloatingBall();
    }

}

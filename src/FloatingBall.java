import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.security.SecureRandom;
import java.util.List;

public class FloatingBall extends JWindow {
    private NamePickerApp mainApp;
    private JLabel displayLabel;
    private SecureRandom random;
    private Timer timer;
    private Point initialClick;
    private Timer keepTopTimer;
    private BallPanel ballPanel;
    private int ballRadius;
    private int ballOpacity;

    public FloatingBall(NamePickerApp mainApp) {
        super();
        this.mainApp = mainApp;
        this.random = new SecureRandom();
        this.ballRadius = ConfigManager.getFloatingBallRadius();
        this.ballOpacity = ConfigManager.getFloatingBallOpacity();
        initializeComponents();
        setupEventHandlers();
        startKeepTopTimer();
    }

    private void initializeComponents() {
        setBackground(new Color(0, 0, 0, 0));
        
        setAlwaysOnTop(true);
        setLayout(new BorderLayout());
        
        int fontSize = calculateFontSize(ballRadius);
        displayLabel = new JLabel("抽取", SwingConstants.CENTER);
        displayLabel.setFont(new Font("微软雅黑", Font.BOLD, fontSize));
        displayLabel.setForeground(Color.WHITE);
        displayLabel.setPreferredSize(new Dimension(ballRadius * 2, ballRadius * 2));
        displayLabel.setOpaque(false);
        
        ballPanel = new BallPanel();
        ballPanel.setLayout(new BorderLayout());
        ballPanel.add(displayLabel, BorderLayout.CENTER);
        ballPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        ballPanel.setPreferredSize(new Dimension(ballRadius * 2, ballRadius * 2));
        
        add(ballPanel);
        pack();
        
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(screenSize.width - getWidth() - 50, screenSize.height - getHeight() - 50);
    }
    
    private int calculateFontSize(int radius) {
        int fontSize = (int)(radius * 0.4);
        fontSize = Math.max(12, Math.min(36, fontSize));
        return fontSize;
    }

    private class BallPanel extends JPanel {
        public BallPanel() {
            setOpaque(false);
            setBackground(new Color(0, 0, 0, 0));
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            g2d.setColor(new Color(70, 130, 180, ballOpacity));
            g2d.fillOval(0, 0, getWidth(), getHeight());
            
            g2d.dispose();
        }
    }

    private void setupEventHandlers() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                initialClick = e.getPoint();
                if (e.getClickCount() == 2) {
                    performRandomPick();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point currentLocation = getLocation();
                setLocation(currentLocation.x + e.getX() - initialClick.x,
                        currentLocation.y + e.getY() - initialClick.y);
            }
        };

        displayLabel.addMouseListener(mouseHandler);
        displayLabel.addMouseMotionListener(mouseHandler);

        JPopupMenu popupMenu = createDynamicPopupMenu();
        displayLabel.setComponentPopupMenu(popupMenu);
    }
    
    private JPopupMenu createDynamicPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        
        JMenuItem pickItem = new JMenuItem("随机抽取");
        pickItem.addActionListener(e -> performRandomPick());
        popupMenu.add(pickItem);
        
        String currentMode = mainApp.getCurrentMode();
        if (currentMode != null) {
            switch (currentMode) {
                case "名字列表模式":
                    JMenuItem configItem = new JMenuItem("配置名单");
                    configItem.addActionListener(e -> mainApp.showConfigWindow());
                    popupMenu.add(configItem);
                    break;
                    
                case "数字模式":
                    JMenuItem numberItem = new JMenuItem("数字设置");
                    numberItem.addActionListener(e -> showNumberPicker());
                    popupMenu.add(numberItem);
                    break;
                    
                case "座位模式":
                    JMenuItem seatItem = new JMenuItem("座位设置");
                    seatItem.addActionListener(e -> showSeatPicker());
                    popupMenu.add(seatItem);
                    break;
            }
        }
        
        popupMenu.addSeparator();
        
        JMenuItem settingsItem = new JMenuItem("悬浮球设置");
        settingsItem.addActionListener(e -> mainApp.showSettingsWindow());
        popupMenu.add(settingsItem);

        // 插件扩展点：悬浮球右键菜单注入插件注册的菜单项
        if (mainApp.getPluginManager() != null) {
            java.util.List<PluginManager.MenuItemSpec> pluginItems =
                    mainApp.getPluginManager().getFloatingBallMenuItemSpecs();
            if (!pluginItems.isEmpty()) {
                popupMenu.addSeparator();
                for (PluginManager.MenuItemSpec spec : pluginItems) {
                    JMenuItem item = new JMenuItem(spec.text);
                    item.addActionListener(e -> spec.action.run());
                    popupMenu.add(item);
                }
            }
        }

        // 内置入口：插件管理
        popupMenu.addSeparator();
        JMenuItem pluginManagerItem = new JMenuItem("插件管理");
        pluginManagerItem.addActionListener(e -> mainApp.showPluginManagerDialog());
        popupMenu.add(pluginManagerItem);

        popupMenu.addSeparator();
        
        JMenuItem closeItem = new JMenuItem("关闭");
        closeItem.addActionListener(e -> {
            mainApp.hideFloatingBall();
        });
        popupMenu.add(closeItem);
        
        return popupMenu;
    }

    private void showNumberPicker() {
        Scheme currentScheme = mainApp.getCurrentScheme();
        if (currentScheme != null) {
            NumberPicker numberPicker = new NumberPicker((Frame) mainApp, currentScheme.getName());
            numberPicker.setVisible(true);
        }
    }

    private void showSeatPicker() {
        Scheme currentScheme = mainApp.getCurrentScheme();
        if (currentScheme != null) {
            SeatPicker seatPicker = new SeatPicker((Frame) mainApp, currentScheme.getName());
            seatPicker.setVisible(true);
        }
    }

    private void startKeepTopTimer() {
        keepTopTimer = new Timer(100, e -> {
            toFront();
            repaint();
        });
        keepTopTimer.start();
    }

    private void stopKeepTopTimer() {
        if (keepTopTimer != null && keepTopTimer.isRunning()) {
            keepTopTimer.stop();
        }
    }

    @Override
    public void dispose() {
        stopKeepTopTimer();
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        super.dispose();
    }

    /**
     * 修改悬浮球显示文本（插件接口）。
     */
    public void setDisplayText(String text) {
        displayLabel.setText(text);
        ballPanel.repaint();
    }

    /**
     * 重绘悬浮球（插件接口）。
     */
    public void repaintBall() {
        ballPanel.repaint();
    }

    /**
     * 获取悬浮球半径（插件接口，如多悬浮球插件用于布局）。
     */
    public int getBallRadius() {
        return ballRadius;
    }

    private void performRandomPick() {
        Scheme currentScheme = mainApp.getCurrentScheme();
        String currentMode = mainApp.getCurrentMode();

        if (currentScheme == null || currentMode == null) {
            displayLabel.setText("无方案");
            return;
        }

        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        final int[] counter = {0};
        final int maxIterations = 20;

        switch (currentMode) {
            case "名字列表模式":
                performNamePick(currentScheme, counter, maxIterations);
                break;
            case "数字模式":
                performNumberPick(currentScheme, counter, maxIterations);
                break;
            case "座位模式":
                performSeatPick(currentScheme, counter, maxIterations);
                break;
            default:
                displayLabel.setText("无效模式");
        }
    }

    private void performNamePick(Scheme scheme, int[] counter, int maxIterations) {
        NameManager nameManager = mainApp.getNameManager();
        List<String> names = nameManager.loadNamesForScheme(scheme.getName());

        if (names.isEmpty()) {
            displayLabel.setText("损坏");
            return;
        }

        int count = mainApp.getEffectivePickCount();

        // 插件拦截：允许插件接管本次抽取（如“多悬浮球插件”在数量>1 时展示多球动画）
        if (mainApp.getPluginManager() != null) {
            for (FloatingBallPickHandler handler : mainApp.getPluginManager().getFloatingBallPickHandlers()) {
                try {
                    if (handler.onNamePick(this, scheme, names, count)) {
                        return;
                    }
                } catch (Throwable t) {
                    LogManager.log("插件悬浮球抽取拦截器异常: " + t, "PLUGIN_PICK_ERROR");
                }
            }
        }

        // 默认单人滚动抽取（原有逻辑不变）
        timer = new Timer(50, e -> {
            if (counter[0] < maxIterations) {
                String randomName = names.get(random.nextInt(names.size()));
                displayLabel.setText(randomName.length() > 5 ?
                        randomName.substring(0, 5) + "..." :
                        randomName);
                counter[0]++;
                ballPanel.repaint();
            } else {
                timer.stop();
            }
        });
        timer.start();
    }

    private void performNumberPick(Scheme scheme, int[] counter, int maxIterations) {
        NumberRange range = mainApp.getSchemeManager().getNumberRange(scheme.getName());

        if (range == null) {
            displayLabel.setText("无范围");
            return;
        }

        timer = new Timer(50, e -> {
            if (counter[0] < maxIterations) {
                int randomNum = random.nextInt(range.getMax() - range.getMin() + 1) + range.getMin();
                displayLabel.setText(String.valueOf(randomNum));
                counter[0]++;
                ballPanel.repaint();
            } else {
                timer.stop();
            }
        });

        timer.start();
    }

    private void performSeatPick(Scheme scheme, int[] counter, int maxIterations) {
        SeatConfig config = mainApp.getSchemeManager().getSeatConfig(scheme.getName());

        if (config == null || config.getSelectedSeats().isEmpty()) {
            displayLabel.setText("无座位");
            return;
        }

        timer = new Timer(50, e -> {
            if (counter[0] < maxIterations) {
                Point randomSeat = config.getSelectedSeats().get(random.nextInt(config.getSelectedSeats().size()));
                displayLabel.setText("(" + randomSeat.x + "," + randomSeat.y + ")");
                counter[0]++;
                ballPanel.repaint();
            } else {
                timer.stop();
            }
        });

        timer.start();
    }
}


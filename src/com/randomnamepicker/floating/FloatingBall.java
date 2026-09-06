package com.randomnamepicker.floating;

import com.randomnamepicker.core.ConfigManager;
import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.mode.ModeHandler;
import com.randomnamepicker.model.Scheme;
import com.randomnamepicker.plugin.PluginManager;
import com.randomnamepicker.ui.NamePickerApp;
import com.randomnamepicker.ui.NumberPicker;
import com.randomnamepicker.ui.RollingPicker;
import com.randomnamepicker.ui.SeatPicker;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;
import javax.swing.*;

public class FloatingBall extends JWindow {
    private NamePickerApp mainApp;
    private JLabel displayLabel;
    private RollingPicker rollingPicker;
    private Point initialClick;
    private Timer keepTopTimer;
    private BallPanel ballPanel;
    private int ballRadius;
    private int ballOpacity;

    public FloatingBall(NamePickerApp mainApp) {
        super();
        this.mainApp = mainApp;
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

        // Stage3：悬浮球右键菜单插件项——每次新建悬浮球实例时读取当前已提交插件
        java.util.List<PluginManager.MenuAction> pluginActions =
                PluginManager.getInstance().getFloatingBallMenuActions();
        if (!pluginActions.isEmpty()) {
            popupMenu.addSeparator();
            for (PluginManager.MenuAction act : pluginActions) {
                JMenuItem item = new JMenuItem(act.getTitle());
                PluginManager.MenuAction captured = act;
                item.addActionListener(e -> PluginManager.getInstance()
                        .runMenuActionSafely(captured.getPluginName(), captured.getAction(), e));
                popupMenu.add(item);
            }
        }

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
        if (rollingPicker != null) {
            rollingPicker.stop();
            rollingPicker = null;
        }
        super.dispose();
    }

    /**
     * 双击抽取（Stage2 重构后）：与主窗共用当前模式的 ModeHandler 与取样逻辑；
     * canPick() 非 null 时不弹窗，按 modeId 显示现状短文本；
     * 否则以 RollingPicker（maxTicks=20、间隔 50ms）固定滚动 20 次后自动定格。
     */
    private void performRandomPick() {
        Scheme currentScheme = mainApp.getCurrentScheme();
        if (currentScheme == null) {
            displayLabel.setText("无方案");
            return;
        }

        ModeHandler handler = mainApp.getCurrentModeHandler();
        if (handler == null) {
            displayLabel.setText("无效模式");
            return;
        }

        String blockReason = safeCanPick(handler);
        if (blockReason != null) {
            displayLabel.setText(shortTextFor(safeModeId(handler), blockReason));
            return;
        }

        final String modeId = safeModeId(handler);
        final Supplier<String> safeSupplier = buildSafeCandidateSupplier(handler);
        rollingPicker = new RollingPicker(safeSupplier,
                value -> {
                    displayLabel.setText(formatForBall(modeId, value));
                    ballPanel.repaint();
                },
                RollingPicker.DEFAULT_INTERVAL_MS, 20);
        rollingPicker.start();
    }

    /** 防御（D0.5）：canPick 抛异常时按“插件模式校验异常”处理。 */
    private String safeCanPick(ModeHandler handler) {
        try {
            return handler.canPick();
        } catch (Throwable t) {
            LogManager.log("插件模式校验异常 - " + safeDisplayName(handler) + ": " + t, "PLUGIN_LOAD_ERROR");
            return "插件模式校验异常";
        }
    }

    private String safeModeId(ModeHandler handler) {
        try {
            String id = handler.getModeId();
            return id != null ? id : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private String safeDisplayName(ModeHandler handler) {
        try {
            String name = handler.getDisplayName();
            return name != null ? name : "?";
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 防御（D0.5）：候选提供 try/catch，异常回退占位文本且每轮只记一次日志。 */
    private Supplier<String> buildSafeCandidateSupplier(ModeHandler handler) {
        final boolean[] logged = {false};
        final Supplier<String> inner = handler.nextCandidate();
        return () -> {
            try {
                return inner.get();
            } catch (Throwable t) {
                if (!logged[0]) {
                    logged[0] = true;
                    LogManager.log("插件模式候选异常 - " + safeDisplayName(handler) + ": " + t, "PLUGIN_LOAD_ERROR");
                }
                return "插件模式异常";
            }
        };
    }

    /**
     * 数据缺失短文本映射（严格保真）：与迁移前悬浮球显示逐字一致。
     * 未来插件模式无映射时，允许回退显示 canPick() 返回值（Stage3 再定规则）。
     */
    private String shortTextFor(String modeId, String blockReason) {
        switch (modeId) {
            case "name_list":
                return "损坏";
            case "number":
                return "无范围";
            case "seat":
                return "无座位";
            default:
                return blockReason;
        }
    }

    /**
     * 悬浮球历史显示口径（严格保真）：
     * - 名字列表模式：超过 5 个字符截断显示为 "xxx..."；
     * - 座位模式：还原为无空格 "(x,y)"（候选为主窗格式 "(x, y)"，两处本就不同，须各自保持）；
     * - 数字及其它候选：原样显示（旧实现不截断数字/座位）。
     */
    private String formatForBall(String modeId, String value) {
        if (value == null) {
            return "";
        }
        if ("name_list".equals(modeId) && value.length() > 5) {
            return value.substring(0, 5) + "...";
        }
        if ("seat".equals(modeId)) {
            return value.replace(", ", ",");
        }
        return value;
    }
}

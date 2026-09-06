package com.randomnamepicker.ui;

import com.randomnamepicker.core.ConfigManager;
import com.randomnamepicker.core.DataManager;
import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.core.NameManager;
import com.randomnamepicker.core.SchemeManager;
import com.randomnamepicker.floating.FloatingBall;
import com.randomnamepicker.main.Main;
import com.randomnamepicker.mode.ModeHandler;
import com.randomnamepicker.mode.ModeHost;
import com.randomnamepicker.mode.ModeRegistry;
import com.randomnamepicker.model.Scheme;
import com.randomnamepicker.plugin.HostEvent;
import com.randomnamepicker.plugin.PluginManager;
import com.randomnamepicker.plugin.UiZone;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;

public class NamePickerApp extends JFrame implements ModeHost {
    private NameManager nameManager;
    private SchemeManager schemeManager;
    private DataManager dataManager;
    private LogManager logManager;
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
    private RollingPicker rollingPicker;
    private boolean isPicking = false;
    private SeatPicker seatPicker;
    private FloatingBall floatingBall;
    private boolean isFloatingBallVisible = false;
    private ModeHandler currentModeHandler;
    private JMenu pluginMenu;
    private JPanel pluginButtonArea;   // 插件 UI 槽二期：主窗插件按钮区（按钮阵下方，两列向下）
    private JPanel pluginHeaderPanel;  // 插件区标识行：左“插件”标识 + 右侧分隔线（仅插件按钮存在时显示）
    private JLabel pluginCaptionLabel;
    private JSeparator pluginSeparator;
    private boolean suppressModeEvent = false;
    /** Q3：方案下拉的程序化刷新（构造恢复/方案管理后重载等）屏蔽 SCHEME_CHANGED 派发。 */
    private boolean suppressSchemeEvent = false;


    public NamePickerApp() {
        nameManager = new NameManager();
        schemeManager = new SchemeManager();
        dataManager = new DataManager();
        logManager = new LogManager();
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        // Q3：构造期的方案填充与恢复属程序化刷新——行为（联动/保存）照常，但不派发 SCHEME/MODE 事件
        suppressSchemeEvent = true;
        suppressModeEvent = true;
        try {
            loadSchemes();
            updateModeSpecificButtons();
            restoreLastScheme();
        } finally {
            suppressSchemeEvent = false;
            suppressModeEvent = false;
        }
        setupWindowCloseBehavior();
        // 插件提交成功后（EDT）刷新“插件”菜单与模式下拉框
        PluginManager.getInstance().addUIListener(this::onPluginSetChanged);

        // 宿主内建主题：注册全局窗口侦测（窗口打开即自动装配背景；默认关闭 → 零影响）
        com.randomnamepicker.theme.BackdropManager.getInstance().registerAutoAttach();
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

        // Stage3：主窗“插件”菜单（无插件时为空菜单，D0.2）
        JMenuBar menuBar = new JMenuBar();
        pluginMenu = new JMenu("插件");
        menuBar.add(pluginMenu);
        setJMenuBar(menuBar);

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
        modeComboBox = new JComboBox<>(ModeRegistry.getDisplayNames().toArray(new String[0]));
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

        // 插件 UI 槽二期：主窗插件按钮区——位于按钮阵下方，两列向下扩展，仅非空时可见；
        // 与原有功能之间用“插件”标识行（左侧文字 + 右侧水平分隔线）做视觉隔断，同样仅在有插件按钮时可见
        pluginCaptionLabel = new JLabel("插件");
        pluginCaptionLabel.setFont(pluginCaptionLabel.getFont().deriveFont(Font.BOLD, 12f));

        pluginSeparator = new JSeparator(SwingConstants.HORIZONTAL);

        pluginHeaderPanel = new JPanel(new BorderLayout(8, 0));
        pluginHeaderPanel.setBorder(BorderFactory.createEmptyBorder(2, 10, 0, 10));
        pluginHeaderPanel.add(pluginCaptionLabel, BorderLayout.WEST);
        pluginHeaderPanel.add(pluginSeparator, BorderLayout.CENTER);
        pluginHeaderPanel.setVisible(false);

        pluginButtonArea = new JPanel(new GridLayout(0, 2, 10, 10));
        pluginButtonArea.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));
        pluginButtonArea.setVisible(false);

        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.add(buttonPanel);
        southPanel.add(pluginHeaderPanel);
        southPanel.add(pluginButtonArea);
        add(southPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        pickButton.addActionListener(e -> togglePick());
        configButton.addActionListener(e -> showConfigWindow());
        floatingButton.addActionListener(e -> showFloatingBall());
        schemeManageButton.addActionListener(e -> showSchemeManager());
        settingsButton.addActionListener(e -> showSettingsWindow());
        modeComboBox.addActionListener(e -> {
            if (suppressModeEvent) {
                return;
            }
            onModeChanged();
            updateModeSpecificButtons();
            // Q3/F3：未被 suppress = 用户驱动（含用户切方案触发的方案→模式自动联动），照发 MODE_CHANGED
            dispatchModeChangedEvent();
        });
        schemeComboBox.addActionListener(e -> {
            boolean userDriven = !suppressSchemeEvent;
            if (userDriven) {
                // 先发 SCHEME_CHANGED（保持“先方案后模式”的因果顺序；联动产生的 MODE_CHANGED 由上方 mode 监听随后派发）
                Scheme sel = (Scheme) schemeComboBox.getSelectedItem();
                if (sel != null) {
                    PluginManager.getInstance().dispatchHostEvent(
                            HostEvent.schemeChanged(sel.getName(), safeSchemeType(sel)));
                }
            }
            // 联动/保存等行为在 suppress 期间也照常执行（与现状一致），仅事件派发被屏蔽
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
            
            // 方案类型 → 内置模式联动（经注册表；插件模式无 schemeType 映射，不自动拨动）
            String targetModeId = ModeRegistry.getModeIdForSchemeType(selectedScheme.getType());
            if (targetModeId != null) {
                ModeRegistry.ModeDefinition definition = ModeRegistry.getByModeId(targetModeId);
                if (definition != null) {
                    modeComboBox.setSelectedItem(definition.getDisplayName());
                }
            }
        }
    }

    private void onModeChanged() {
        String selectedMode = (String) modeComboBox.getSelectedItem();
        if (selectedMode != null) {
            updateDisplayText();
        }
    }

    /** Q3/F3：派发 MODE_CHANGED（displayName + 解析出的 modeId；解析失败时 modeId 为 null）。 */
    private void dispatchModeChangedEvent() {
        String selectedMode = (String) modeComboBox.getSelectedItem();
        if (selectedMode == null) {
            return;
        }
        PluginManager.getInstance().dispatchHostEvent(
                HostEvent.modeChanged(modeIdOf(selectedMode), selectedMode));
    }

    /** 显示名 → modeId（内置经 ModeRegistry、插件经 PluginManager；失败回退 null）。 */
    private String modeIdOf(String displayName) {
        if (displayName == null) {
            return null;
        }
        try {
            ModeRegistry.ModeDefinition d = ModeRegistry.getByDisplayName(displayName);
            if (d != null) {
                return d.getModeId();
            }
        } catch (Throwable ignore) {
            // 回退到插件查询
        }
        try {
            ModeHandler h = PluginManager.getInstance().getPluginModeHandler(displayName);
            if (h != null) {
                return h.getModeId();
            }
        } catch (Throwable ignore) {
            // 保持 null
        }
        return null;
    }

    private String safeSchemeType(Scheme scheme) {
        try {
            String t = scheme.getType();
            return t != null ? t : "";
        } catch (Throwable ignore) {
            return "";
        }
    }

    private void updateModeSpecificButtons() {
        String selectedDisplayName = (String) modeComboBox.getSelectedItem();

        if (selectedDisplayName == null) {
            modeButton1.setVisible(false);
            modeButton2.setVisible(false);
            currentModeHandler = null;
            return;
        }

        currentModeHandler = resolveModeHandler(selectedDisplayName);

        if (currentModeHandler != null) {
            applyModeButton(modeButton1, currentModeHandler.getButton1Text());
            applyModeButton(modeButton2, currentModeHandler.getButton2Text());

            // 移除旧的监听器
            for (ActionListener al : modeButton1.getActionListeners()) {
                modeButton1.removeActionListener(al);
            }
            for (ActionListener al : modeButton2.getActionListeners()) {
                modeButton2.removeActionListener(al);
            }

            // 添加新的监听器
            modeButton1.addActionListener(e -> handleModeButtonClickSafely(1));
            modeButton2.addActionListener(e -> handleModeButtonClickSafely(2));
        } else {
            modeButton1.setVisible(false);
            modeButton2.setVisible(false);
            currentModeHandler = null;
        }

        revalidate();
        repaint();
    }

    /** 解析当前模式 Handler：内置走 ModeRegistry（工厂），插件走 PluginManager（已提交实例）。 */
    private ModeHandler resolveModeHandler(String displayName) {
        ModeRegistry.ModeDefinition definition = ModeRegistry.getByDisplayName(displayName);
        if (definition != null) {
            return definition.create(this);
        }
        return PluginManager.getInstance().getPluginModeHandler(displayName);
    }

    /** D0.12：按钮文本为 null/空 → 隐藏对应按钮（格子留空）；否则显示并设置文本。 */
    private void applyModeButton(JButton button, String text) {
        if (text == null || text.trim().isEmpty()) {
            button.setVisible(false);
        } else {
            button.setVisible(true);
            button.setText(text);
        }
    }

    /** 防御：模式按钮动作 try/catch（D0.5）。 */
    private void handleModeButtonClickSafely(int which) {
        ModeHandler handler = currentModeHandler;
        if (handler == null) {
            return;
        }
        try {
            if (which == 1) {
                handler.handleButton1Click();
            } else {
                handler.handleButton2Click();
            }
        } catch (Throwable t) {
            LogManager.log("模式按钮动作异常 - " + safeDisplayName(handler) + ": " + t, "PLUGIN_LOAD_ERROR");
            JOptionPane.showMessageDialog(this, "模式按钮操作异常：" + t.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
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

    public void updateDisplayText() {
        String selectedDisplayName = (String) modeComboBox.getSelectedItem();
        if (selectedDisplayName == null) {
            return;
        }
        if (isKnownMode(selectedDisplayName)) {
            displayLabel.setText(selectedDisplayName + ": 点击开始抽取");
        } else {
            displayLabel.setText("请选择方案和模式后开始抽取");
        }
    }

    /** 是否当前已知模式（内置或已提交插件）。 */
    private boolean isKnownMode(String displayName) {
        if (ModeRegistry.getByDisplayName(displayName) != null) {
            return true;
        }
        return PluginManager.getInstance().getPluginModeHandler(displayName) != null;
    }

    /** 插件集合变化后（EDT）：重建“插件”菜单、模式下拉框与主窗插件按钮区。 */
    private void onPluginSetChanged() {
        refreshPluginMenu();
        refreshModeCombo();
        refreshPluginButtons();
    }

    /** 重建主窗插件按钮区（MAIN_WINDOW）：两列向下扩展；空则连标识行一起隐藏。 */
    private void refreshPluginButtons() {
        pluginButtonArea.removeAll();
        List<PluginManager.MenuAction> actions = PluginManager.getInstance().getUiActions(UiZone.MAIN_WINDOW);
        if (actions.isEmpty()) {
            pluginButtonArea.setVisible(false);
            pluginHeaderPanel.setVisible(false);
        } else {
            for (PluginManager.MenuAction act : actions) {
                JButton button = new JButton(act.getTitle());
                PluginManager.MenuAction captured = act;
                button.addActionListener(e -> PluginManager.getInstance()
                        .runMenuActionSafely(captured.getPluginName(), captured.getAction(), e));
                pluginButtonArea.add(button);
            }
            pluginButtonArea.setVisible(true);
            pluginHeaderPanel.setVisible(true);
        }
        pluginHeaderPanel.revalidate();
        pluginHeaderPanel.repaint();
        pluginButtonArea.revalidate();
        pluginButtonArea.repaint();
        revalidate();
        repaint();
    }

    private void refreshPluginMenu() {
        pluginMenu.removeAll();
        List<PluginManager.MenuAction> actions = PluginManager.getInstance().getMainMenuActions();
        List<PluginManager.PluginInfo> infos = PluginManager.getInstance().getPluginInfos();
        for (PluginManager.MenuAction act : actions) {
            JMenuItem item = new JMenuItem(act.getTitle());
            PluginManager.MenuAction captured = act;
            item.addActionListener(e -> PluginManager.getInstance()
                    .runMenuActionSafely(captured.getPluginName(), captured.getAction(), e));
            pluginMenu.add(item);
        }
        if (!infos.isEmpty()) {
            if (!actions.isEmpty()) {
                pluginMenu.addSeparator();
            }
            for (PluginManager.PluginInfo info : infos) {
                JMenuItem infoItem = new JMenuItem(info.getName() + " " + info.getVersion());
                infoItem.setEnabled(false);
                pluginMenu.add(infoItem);
            }
        }
        // D10：加载历史非空（含被拒 jar）时，菜单尾提供“插件状态…”只读入口；无任何 jar 时零差异
        List<PluginManager.LoadOutcome> history = PluginManager.getInstance().getLoadHistory();
        if (!history.isEmpty()) {
            if (!actions.isEmpty() || !infos.isEmpty()) {
                pluginMenu.addSeparator();
            }
            JMenuItem statusItem = new JMenuItem("插件状态…");
            statusItem.addActionListener(e -> showPluginStatusDialog());
            pluginMenu.add(statusItem);
        }
        pluginMenu.revalidate();
    }

    /** D10：插件加载状态只读对话框（成功清单 / 拒载清单 + 原因码与建议）。 */
    private void showPluginStatusDialog() {
        JDialog dialog = new JDialog(this, "插件状态", true);
        dialog.setSize(560, 380);
        dialog.setLocationRelativeTo(this);
        StringBuilder sb = new StringBuilder();
        List<PluginManager.LoadOutcome> history = PluginManager.getInstance().getLoadHistory();
        for (PluginManager.LoadOutcome o : history) {
            if (o.isLoaded()) {
                sb.append("[加载成功] ").append(o.getJarName()).append("\n");
                for (String info : o.getPluginInfos()) {
                    sb.append("    - ").append(info).append("\n");
                }
            } else {
                sb.append("[加载被拒] ").append(o.getJarName()).append("\n");
                sb.append("    原因码: ").append(o.getReason() != null ? o.getReason().name() : "UNKNOWN").append("\n");
                sb.append("    原因: ").append(o.getDetail() == null ? "" : o.getDetail()).append("\n");
                if (o.getReason() != null) {
                    sb.append("    建议: ").append(o.getReason().getSuggestion()).append("\n");
                }
            }
            sb.append("    时间: ").append(java.text.SimpleDateFormat.getDateTimeInstance()
                    .format(new java.util.Date(o.getTimestamp()))).append("\n\n");
        }
        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(area);
        dialog.add(scroll, BorderLayout.CENTER);
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dialog.dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.add(close);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    /** 重建模式下拉框：内置（ModeRegistry）+ 已提交插件模式；尽量保持当前选中。程序化刷新整体 suppress（Q3 不发 MODE_CHANGED）。 */
    private void refreshModeCombo() {
        String current = (String) modeComboBox.getSelectedItem();
        suppressModeEvent = true;
        try {
            modeComboBox.removeAllItems();
            for (String name : ModeRegistry.getDisplayNames()) {
                modeComboBox.addItem(name);
            }
            for (String name : PluginManager.getInstance().getPluginModeDisplayNames()) {
                if (ModeRegistry.getByDisplayName(name) == null) {
                    modeComboBox.addItem(name);
                }
            }
            if (current != null && modeContainsItem(current)) {
                modeComboBox.setSelectedItem(current);
            } else if (modeComboBox.getItemCount() > 0) {
                modeComboBox.setSelectedIndex(0);
            }
        } finally {
            suppressModeEvent = false;
        }
        updateModeSpecificButtons();
        updateDisplayText();
    }

    private boolean modeContainsItem(String value) {
        for (int i = 0; i < modeComboBox.getItemCount(); i++) {
            if (modeComboBox.getItemAt(i).equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void togglePick() {
        if (isPicking) {
            stopPicking();
        } else {
            startPicking();
        }
    }

    private void startPicking() {
        Scheme selectedScheme = (Scheme) schemeComboBox.getSelectedItem();

        if (selectedScheme == null) {
            JOptionPane.showMessageDialog(this, "请选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ModeHandler handler = currentModeHandler;
        if (handler == null) {
            JOptionPane.showMessageDialog(this, "请选择抽取模式！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String blockReason = safeCanPick(handler);
        if (blockReason != null) {
            // 语义与现状等价：以 canPick() 返回值作为弹窗文案并复位
            showBlockedMessage(handler, blockReason);
            // D1 严格保真：与迁移前一致，数据缺失点击后仍按旧格式补记一行“抽取结果”（值=当时标签文本）
            logResult();
            return;
        }

        isPicking = true;
        pickButton.setText("停止");

        // Q2：真实抽取（canPick 通过并启动滚动）才派发 PICK_STARTED
        String modeDisplayName = (String) modeComboBox.getSelectedItem();
        PluginManager.getInstance().dispatchHostEvent(HostEvent.pickStarted(HostEvent.Source.MAIN,
                selectedScheme.getName(), safeSchemeType(selectedScheme),
                modeIdOf(modeDisplayName), modeDisplayName));
        rollingPicker = new RollingPicker(buildSafeCandidateSupplier(handler), value -> displayLabel.setText(value));
        rollingPicker.start();
        //抽取日志记录（被废除）
        //LogManager.log(selectedScheme.getName() + "-" + handler.getDisplayName(), "开始抽取");
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

    private void stopPicking() {
        isPicking = false;
        pickButton.setText("开始抽取");
        if (rollingPicker != null) {
            rollingPicker.stop();
            rollingPicker = null;
        }
        logResult();
        // Q2：真实抽取结束定格 → PICK_FINISHED（结果取定格标签文本，与 logResult 同口径）
        Scheme currentScheme = getCurrentScheme();
        String modeDisplayName = (String) modeComboBox.getSelectedItem();
        if (currentScheme != null && modeDisplayName != null) {
            PluginManager.getInstance().dispatchHostEvent(HostEvent.pickFinished(HostEvent.Source.MAIN,
                    currentScheme.getName(), safeSchemeType(currentScheme),
                    modeIdOf(modeDisplayName), modeDisplayName, displayLabel.getText()));
        }
    }

    /**
     * 停止后按原格式记日志：方案名-模式下拉框文本=定格结果（操作码“抽取结果”）。
     * 结果取当前标签文本：正常定格时标签即最后一次滚显候选（与 RollingPicker.getLastValue()
     * 一致）；零刻度立即停止/数据缺失复位等边界与迁移前 stopPicking 语义一致。
     */
    private void logResult() {
        String result = displayLabel.getText();
        String selectedMode = (String) modeComboBox.getSelectedItem();
        Scheme currentScheme = getCurrentScheme();
        
        if (currentScheme != null && selectedMode != null) {
            LogManager.log(currentScheme.getName() + "-" + selectedMode + "=" + result, "抽取结果");
        }
    }

    /**
     * 弹窗外观严格保真：内置“名字列表”沿用旧样式（标题“错误”、ERROR 图标）；
     * 其余模式沿用旧样式（标题“提示”、WARNING 图标）。文案一律取 canPick() 返回值。
     */
    private void showBlockedMessage(ModeHandler handler, String reason) {
        String modeId;
        try {
            modeId = handler.getModeId();
        } catch (Throwable t) {
            modeId = "";
        }
        if ("name_list".equals(modeId)) {
            JOptionPane.showMessageDialog(this, reason, "错误", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, reason, "提示", JOptionPane.WARNING_MESSAGE);
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
            // 已经可见，现在要隐藏（dispose 在 FloatingBall 内派发 BALL_HIDDEN）
            isFloatingBallVisible = false;
            floatingButton.setText("悬浮球");
        } else {
            // 需要显示新实例
            try {
                floatingBall = new FloatingBall(this);
                floatingBall.setVisible(true);
                isFloatingBallVisible = true;
                floatingButton.setText("隐藏悬浮球");
                // G2：悬浮球显示 → BALL_SHOWN（含初始 bounds）
                PluginManager.getInstance().dispatchHostEvent(
                        HostEvent.ballShown(floatingBall.getBounds()));
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

    @Override
    public DataManager getDataManager() {
        return dataManager;
    }

    @Override
    public LogManager getLogManager() {
        return logManager;
    }

    // ModeHost.getOwner()（返回 java.awt.Window）由继承自 java.awt.Window 的同名方法实现，
    // 本类不得重写 getOwner()：若以 Frame 协变重写并返回自身会破坏 AWT 模态 owner 链，
    // 导致所有模态子窗口死锁（白屏、无法关闭）。

    public Scheme getCurrentScheme() {
        return (Scheme) schemeComboBox.getSelectedItem();
    }

    @Override
    public boolean isFloatingBallVisible() {
        return isFloatingBallVisible;
    }

    @Override
    public java.awt.Rectangle getFloatingBallBounds() {
        FloatingBall b = floatingBall;
        return (b != null && b.isDisplayable() && b.isVisible()) ? b.getBounds() : null;
    }

    @Override
    public java.awt.Rectangle getMainWindowBounds() {
        return getBounds();
    }

    public String getCurrentMode() {
        return (String) modeComboBox.getSelectedItem();
    }

    /** 当前模式对应的 ModeHandler（经注册表解析），供悬浮球双击抽取使用。 */
    public ModeHandler getCurrentModeHandler() {
        return currentModeHandler;
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

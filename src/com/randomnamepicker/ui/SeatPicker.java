package com.randomnamepicker.ui;

import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.mode.SeatModeHandler;
import com.randomnamepicker.model.SeatConfig;
import com.randomnamepicker.plugin.UiZone;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.swing.*;

/**
 * 座位布局设置（P0 修复批次调整后）。
 * <p>
 * 相对旧版的行为变化（均属已批准口径）：
 * <ul>
 *   <li><b>T1</b>：构造参数由 {@code Frame} 改为 {@code Window}，父窗必须是 {@link NamePickerApp}
 *       （旧写法把 {@code ModeHost.getOwner()}＝null 转型存为 mainApp → 构造期 NPE，
 *       主窗“设置座位布局”完全打不开）；</li>
 *   <li><b>B2</b>：结果标签不再与按钮行争 {@code BorderLayout.SOUTH}（后者覆盖前者导致结果永远
 *       不可见）——改为纵向叠放：结果标签 → 按钮行 → 插件 Zone 面板；停止不再清空结果；</li>
 *   <li><b>T2.4</b>：对话框内抽取定格后按主窗口同口径补记 {@code 抽取结果} 日志；</li>
 *   <li><b>Q-f</b>：显式保存语义——点选座位只改内存，“保存设置/应用到方案”才落盘；
 *       关窗有未保存修改时二次确认；</li>
 *   <li><b>Q-b/T3</b>：行列与已选座位冲突时弹三项确认（移除越界座位／取消并恢复原行列／
 *       自动扩展行列容纳），任何分支都保持“座位 ⊆ 行列范围”；</li>
 *   <li><b>T3.6</b>：行列上限 30×30（旧版可输入 999 → 近百万 JButton 卡死）。</li>
 * </ul>
 * </p>
 */
public class SeatPicker extends JDialog {

    /** 行列上限（防大网格 JButton 爆炸）。 */
    public static final int MAX_ROWS = 30;
    public static final int MAX_COLS = 30;

    private JTextField rowsField;
    private JTextField colsField;
    private JButton updateButton;
    private JButton pickButton;
    private JButton saveButton;
    private JButton applyButton;
    private JPanel seatPanel;
    private JLabel resultLabel;
    private List<Point> selectedSeats;
    private java.util.Random random;
    private Timer timer;
    private boolean isPicking = false;
    private NamePickerApp mainApp;
    private String schemeName;
    /** 最近一次滚显的候选（定格日志用；与标签文本解耦）。 */
    private String lastResult;
    /** 上次成功落盘（或载入）的行列/座位：未保存判断、取消回滚用。 */
    private String committedRowsText = "";
    private String committedColsText = "";
    private List<Point> committedSeats = new ArrayList<>();

    public SeatPicker(Window parent, String schemeName) {
        // Window-owner 版 JDialog 没有 (Window,String,boolean) 构造；APPLICATION_MODAL 与原
        // (Frame,String,true) 的模态语义一致（阻塞本应用全部顶层窗）
        super(parent, "座位抽取设置", Dialog.ModalityType.APPLICATION_MODAL);
        // T1：父窗必须是宿主主窗；fail-fast 取代“半构造对象 + 延迟 NPE”
        if (!(parent instanceof NamePickerApp)) {
            throw new IllegalArgumentException("SeatPicker 需要 NamePickerApp 宿主窗口，实际收到: " + parent);
        }
        this.mainApp = (NamePickerApp) parent;
        this.schemeName = schemeName;
        selectedSeats = new ArrayList<>();
        random = new java.util.Random();
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setupCloseBehavior();
        loadCurrentConfig();
    }

    private void initializeComponents() {
        setSize(700, 600);
        setLocationRelativeTo(getParent());
        setResizable(true);

        rowsField = new JTextField(5);
        colsField = new JTextField(5);
        updateButton = new JButton("更新布局");
        pickButton = new JButton("开始抽取");
        saveButton = new JButton("保存设置");
        applyButton = new JButton("应用到方案");
        resultLabel = new JLabel("设置座位后可进行抽取", SwingConstants.CENTER);
        resultLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        resultLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        resultLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        seatPanel = new JPanel();
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("座位布局设置"));
        topPanel.add(new JLabel("行数:"));
        topPanel.add(rowsField);
        topPanel.add(new JLabel("列数:"));
        topPanel.add(colsField);
        topPanel.add(updateButton);
        add(topPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(seatPanel);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        scrollPane.setBorder(BorderFactory.createTitledBorder("座位图"));
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(pickButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(applyButton);

        // B2 修复：结果标签与按钮行纵向叠放（旧版两者都 add 到 SOUTH，按钮行覆盖结果标签 →
        // 座位抽取预览结果永远不可见；与 README §6-14 记录的 ConfigWindow 历史坑同一修法）
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.add(resultLabel);
        southPanel.add(buttonPanel);

        // 插件 UI 槽二期：座位设置对话框插件按钮区（按钮行下方；无动作则不渲染）
        JPanel pluginZonePanel = PluginUiSupport.createZonePanel(UiZone.SEAT_PICKER);
        if (pluginZonePanel != null) {
            southPanel.add(pluginZonePanel);
        }
        add(southPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        updateButton.addActionListener(e -> onUpdateLayout());
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
                    int r = JOptionPane.showConfirmDialog(SeatPicker.this,
                            "当前座位设置尚未保存，确定放弃并关闭？", "提示",
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (r != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                dispose();
            }
        });
    }

    private void loadCurrentConfig() {
        SeatConfig config = mainApp.getSchemeManager().getSeatConfig(schemeName);
        if (config != null) {
            rowsField.setText(String.valueOf(config.getRows()));
            colsField.setText(String.valueOf(config.getCols()));
            selectedSeats = new ArrayList<>(config.getSelectedSeats());
        } else {
            rowsField.setText("5");
            colsField.setText("5");
        }
        markCommitted();
        createSeatGrid();
    }

    private void markCommitted() {
        committedRowsText = rowsField.getText().trim();
        committedColsText = colsField.getText().trim();
        committedSeats = new ArrayList<>(selectedSeats);
    }

    /** 行列或座位选择相对上次保存有变化（含文本未生效的情况）。 */
    private boolean hasUnsavedChanges() {
        if (!rowsField.getText().trim().equals(committedRowsText)) {
            return true;
        }
        if (!colsField.getText().trim().equals(committedColsText)) {
            return true;
        }
        return !new HashSet<>(selectedSeats).equals(new HashSet<>(committedSeats));
    }

    /** 解析并校验行列；notify=true 时弹错误提示。返回 {rows, cols}，失败返回 null。 */
    private int[] parseDimensions(boolean notify) {
        int rows, cols;
        try {
            rows = Integer.parseInt(rowsField.getText().trim());
            cols = Integer.parseInt(colsField.getText().trim());
        } catch (NumberFormatException e) {
            if (notify) {
                JOptionPane.showMessageDialog(this, "请输入有效的数字！", "错误", JOptionPane.ERROR_MESSAGE);
            }
            return null;
        }
        if (rows <= 0 || cols <= 0) {
            if (notify) {
                JOptionPane.showMessageDialog(this, "行数和列数必须大于0！", "错误", JOptionPane.ERROR_MESSAGE);
            }
            return null;
        }
        if (rows > MAX_ROWS || cols > MAX_COLS) {
            if (notify) {
                JOptionPane.showMessageDialog(this,
                        "行数/列数过大：上限 " + MAX_ROWS + " × " + MAX_COLS + "！", "错误", JOptionPane.ERROR_MESSAGE);
            }
            return null;
        }
        return new int[]{rows, cols};
    }

    private List<Point> outOfRangeSeats(int rows, int cols) {
        List<Point> out = new ArrayList<>();
        for (Point p : selectedSeats) {
            if (p.x < 1 || p.x > rows || p.y < 1 || p.y > cols) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Q-b：行列与已选座位冲突时弹三项确认。
     * <p>
     * ①移除越界座位并保存 ②取消并恢复原行列 ③自动扩展行列以容纳。
     * 处理后 {@code dims[0]/dims[1]} 可能被“自动扩展”改写；返回 false 表示用户取消。
     * </p>
     */
    private boolean resolveOutOfRange(int[] dims) {
        List<Point> out = outOfRangeSeats(dims[0], dims[1]);
        if (out.isEmpty()) {
            return true;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("当前行列 ").append(dims[0]).append(" × ").append(dims[1])
                .append(" 下有 ").append(out.size()).append(" 个已选座位越界：");
        for (int i = 0; i < out.size() && i < 10; i++) {
            sb.append("(").append(out.get(i).x).append(",").append(out.get(i).y).append(") ");
        }
        if (out.size() > 10) {
            sb.append("…");
        }
        Object[] options = {"移除越界座位", "取消并恢复原行列", "自动扩展行列容纳"};
        int choice = JOptionPane.showOptionDialog(this, sb.toString(), "座位越界确认",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            selectedSeats.removeAll(out);
            return true;
        }
        if (choice == 2) {
            int maxRow = dims[0];
            int maxCol = dims[1];
            for (Point p : selectedSeats) {
                maxRow = Math.max(maxRow, p.x);
                maxCol = Math.max(maxCol, p.y);
            }
            if (maxRow > MAX_ROWS || maxCol > MAX_COLS) {
                JOptionPane.showMessageDialog(this,
                        "自动扩展会超出行列上限（" + MAX_ROWS + " × " + MAX_COLS + "），请改用“移除越界座位”。",
                        "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            dims[0] = maxRow;
            dims[1] = maxCol;
            rowsField.setText(String.valueOf(maxRow));
            colsField.setText(String.valueOf(maxCol));
            return true;
        }
        return false;   // 取消（含直接关掉确认框）
    }

    /** 取消本次变更：行列文本回滚到上次保存值并重画网格。 */
    private void restoreCommittedDimensions() {
        rowsField.setText(committedRowsText);
        colsField.setText(committedColsText);
        createSeatGrid();
        LogManager.log(schemeName + "-已取消行列变更，恢复为 " + committedRowsText + "×" + committedColsText,
                "取消座位布局变更");
    }

    private void createSeatGrid() {
        int[] dims = parseDimensions(false);
        if (dims == null) {
            return;
        }
        int rows = dims[0];
        int cols = dims[1];

        seatPanel.removeAll();
        seatPanel.setLayout(new GridLayout(rows, cols, 2, 2));
        seatPanel.setPreferredSize(new Dimension(cols * 60, rows * 60));

        for (int row = 1; row <= rows; row++) {
            for (int col = 1; col <= cols; col++) {
                final int currentRow = row;
                final int currentCol = col;
                JButton seatButton = new JButton("(" + row + "," + col + ")");
                seatButton.setPreferredSize(new Dimension(55, 55));
                seatButton.setFont(new Font("微软雅黑", Font.PLAIN, 10));
                seatButton.setMargin(new Insets(2, 2, 2, 2));

                Point seat = new Point(currentRow, currentCol);
                if (selectedSeats.contains(seat)) {
                    seatButton.setBackground(Color.YELLOW);
                }

                seatButton.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        Point seat = new Point(currentRow, currentCol);
                        if (selectedSeats.contains(seat)) {
                            selectedSeats.remove(seat);
                            seatButton.setBackground(null);
                        } else {
                            selectedSeats.add(seat);
                            seatButton.setBackground(Color.YELLOW);
                        }
                        // Q-f：点选只改内存，不再静默写盘（旧版每次点击都做 3 份 AES 落盘）
                    }
                });

                seatPanel.add(seatButton);
            }
        }

        seatPanel.revalidate();
        seatPanel.repaint();
    }

    /** 「更新布局」：只重建网格、不落盘；行列缩小致座位越界时先弹三项确认。 */
    private void onUpdateLayout() {
        int[] dims = parseDimensions(true);
        if (dims == null) {
            return;
        }
        if (!resolveOutOfRange(dims)) {
            restoreCommittedDimensions();
            return;
        }
        createSeatGrid();
        LogManager.log(schemeName + "-行数=" + dims[0] + ",列数=" + dims[1], "更新座位布局");
        // 旧版此处谎报“已更新并保存”（实际不落盘）——显式保存语义下改为如实提示
        JOptionPane.showMessageDialog(this, "座位布局已更新（尚未保存，点“保存设置”生效）", "提示",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void togglePick() {
        if (isPicking) {
            stopPicking();
        } else {
            startPicking();
        }
    }

    private void startPicking() {
        if (selectedSeats.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先选择座位！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        isPicking = true;
        pickButton.setText("停止");
        lastResult = null;

        final List<Point> seats = new ArrayList<>(selectedSeats);
        timer = new Timer(50, e -> {
            Point randomSeat = seats.get(random.nextInt(seats.size()));
            lastResult = "(" + randomSeat.x + ", " + randomSeat.y + ")";
            resultLabel.setText("抽取结果: " + lastResult);
        });
        timer.start();
    }

    private void stopPicking() {
        isPicking = false;
        pickButton.setText("开始抽取");
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        // B2：定格结果保留在标签上（旧版立刻改回提示语，且标签本身还被按钮行覆盖）；
        // T2.4：按主窗口同口径记日志
        if (lastResult != null) {
            LogManager.log(schemeName + "-" + SeatModeHandler.DISPLAY_NAME + "=" + lastResult, "抽取结果");
        }
    }

    private void saveSettings() {
        saveSettings(true);
    }

    /** 保存行列与已选座位；保存前再校验一次越界（覆盖“改了行列没点更新布局”路径）。 */
    private boolean saveSettings(boolean notify) {
        int[] dims = parseDimensions(true);
        if (dims == null) {
            return false;
        }
        if (!resolveOutOfRange(dims)) {
            restoreCommittedDimensions();
            return false;
        }

        SeatConfig config = new SeatConfig(dims[0], dims[1], new ArrayList<>(selectedSeats));
        mainApp.getSchemeManager().saveSeatConfig(schemeName, config);
        markCommitted();
        createSeatGrid();
        LogManager.log(schemeName + "-行数=" + dims[0] + ",列数=" + dims[1] + ",已选座位数=" + selectedSeats.size(),
                "保存座位设置");
        if (notify) {
            JOptionPane.showMessageDialog(this, "设置已保存！", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
        return true;
    }

    private void applyToScheme() {
        // 先真保存；失败（非法行列/用户取消越界确认）直接返回，不再提示“已应用”
        if (!saveSettings(false)) {
            return;
        }
        LogManager.log(schemeName, "应用座位设置到方案");
        JOptionPane.showMessageDialog(this, "设置已应用到方案：" + schemeName + "（已保存）", "提示",
                JOptionPane.INFORMATION_MESSAGE);
    }
}

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class SeatPicker extends JDialog {
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

    public SeatPicker(Frame parent, String schemeName) {
        super(parent, "座位抽取设置", true);
        this.mainApp = (NamePickerApp) parent;
        this.schemeName = schemeName;
        selectedSeats = new ArrayList<>();
        random = new java.util.Random();
        initializeComponents();
        setupLayout();
        setupEventHandlers();
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

        add(resultLabel, BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(pickButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(applyButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        updateButton.addActionListener(e -> {
            createSeatGrid();
            LogManager.log(schemeName + "-行数=" + rowsField.getText() + ",列数=" + colsField.getText(), "更新座位布局");
            JOptionPane.showMessageDialog(this, "座位布局已更新并保存！", "提示", JOptionPane.INFORMATION_MESSAGE);
        });
        pickButton.addActionListener(e -> togglePick());
        saveButton.addActionListener(e -> saveSettings());
        applyButton.addActionListener(e -> applyToScheme());
    }

    private void loadCurrentConfig() {
        SeatConfig config = mainApp.getSchemeManager().getSeatConfig(schemeName);
        if (config != null) {
            rowsField.setText(String.valueOf(config.getRows()));
            colsField.setText(String.valueOf(config.getCols()));
            selectedSeats = new ArrayList<>(config.getSelectedSeats());
            createSeatGrid();
        } else {
            rowsField.setText("5");
            colsField.setText("5");
            createSeatGrid();
        }
    }

    private void createSeatGrid() {
        try {
            int rows = Integer.parseInt(rowsField.getText().trim());
            int cols = Integer.parseInt(colsField.getText().trim());

            if (rows <= 0 || cols <= 0) {
                JOptionPane.showMessageDialog(this, "行数和列数必须大于0！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

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
                            saveSeatConfig();
                        }
                    });

                    seatPanel.add(seatButton);
                }
            }

            seatPanel.revalidate();
            seatPanel.repaint();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "请输入有效的数字！", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveSeatConfig() {
        try {
            int rows = Integer.parseInt(rowsField.getText().trim());
            int cols = Integer.parseInt(colsField.getText().trim());

            if (rows > 0 && cols > 0) {
                SeatConfig config = new SeatConfig(rows, cols, selectedSeats);
                mainApp.getSchemeManager().saveSeatConfig(schemeName, config);
            }
        } catch (NumberFormatException e) {
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
        if (selectedSeats.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先选择座位！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        isPicking = true;
        pickButton.setText("停止");

        timer = new Timer(50, e -> {
            Point randomSeat = selectedSeats.get(random.nextInt(selectedSeats.size()));
            resultLabel.setText("抽取结果: (" + randomSeat.x + ", " + randomSeat.y + ")");
        });
        timer.start();
    }

    private void stopPicking() {
        isPicking = false;
        pickButton.setText("开始抽取");
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        resultLabel.setText("设置座位后可进行抽取");
    }

    private void saveSettings() {
        try {
            int rows = Integer.parseInt(rowsField.getText().trim());
            int cols = Integer.parseInt(colsField.getText().trim());

            if (rows <= 0 || cols <= 0) {
                JOptionPane.showMessageDialog(this, "行数和列数必须大于 0！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            SeatConfig config = new SeatConfig(rows, cols, selectedSeats);
            mainApp.getSchemeManager().saveSeatConfig(schemeName, config);
            LogManager.log(schemeName + "-行数=" + rows + ",列数=" + cols + ",已选座位数=" + selectedSeats.size(), "保存座位设置");
            JOptionPane.showMessageDialog(this, "设置已保存！", "提示", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException e) {
            LogManager.log("座位设置失败-" + e.getMessage(), "错误");
            JOptionPane.showMessageDialog(this, "请输入有效的数字！", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyToScheme() {
        saveSettings();
        LogManager.log(schemeName, "应用座位设置到方案");
        JOptionPane.showMessageDialog(this, "设置已应用到方案：" + schemeName, "提示", JOptionPane.INFORMATION_MESSAGE);
    }
}

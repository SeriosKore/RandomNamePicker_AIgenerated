import javax.swing.*;
import java.awt.*;

public class SeatModeHandler extends ModeHandler {
    private JButton button1;
    private JButton button2;

    public SeatModeHandler(NamePickerApp app) {
        super(app);
        initializeButtons();
    }

    private void initializeButtons() {
        button1 = new JButton(getButton1Text());
        button1.addActionListener(e -> handleButton1Click());

        button2 = new JButton(getButton2Text());
        button2.addActionListener(e -> handleButton2Click());
    }

    @Override
    public JButton getModeButton1() {
        return button1;
    }

    @Override
    public JButton getModeButton2() {
        return button2;
    }

    @Override
    public void handleButton1Click() {
        showSeatSettings();
    }

    @Override
    public void handleButton2Click() {
        saveSeatConfig();
    }

    @Override
    public String getButton1Text() {
        return "设置座位布局";
    }

    @Override
    public String getButton2Text() {
        return "保存座位设置";
    }

    private void showSeatSettings() {
        Scheme currentScheme = app.getCurrentScheme();
        if (currentScheme != null) {
            SeatPicker seatPicker = new SeatPicker((Frame) app, currentScheme.getName());
            seatPicker.setVisible(true);
            LogManager.log(currentScheme.getName(), "设置座位布局");
        } else {
            JOptionPane.showMessageDialog(app, "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void saveSeatConfig() {
        Scheme currentScheme = app.getCurrentScheme();
        if (currentScheme != null) {
            SeatConfig config = schemeManager.getSeatConfig(currentScheme.getName());
            if (config != null) {
                LogManager.log(currentScheme.getName() + "-行数=" + config.getRows() + ",列数=" + config.getCols() + ",已选座位数=" + config.getSelectedSeats().size(), "保存座位设置");
                JOptionPane.showMessageDialog(app, "座位设置已保存：行数=" + config.getRows() + "，列数=" + config.getCols() +
                                "，已选座位数=" + config.getSelectedSeats().size(),
                        "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(app, "当前方案尚未设置座位！", "提示", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(app, "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}

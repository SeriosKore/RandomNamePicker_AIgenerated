import javax.swing.*;
import java.awt.Frame;

public class NumberModeHandler extends ModeHandler {
    private JButton button1;
    private JButton button2;

    public NumberModeHandler(NamePickerApp app) {
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
        showNumberSettings();
    }

    @Override
    public void handleButton2Click() {
        saveNumberRange();
    }

    @Override
    public String getButton1Text() {
        return "设置数字范围";
    }

    @Override
    public String getButton2Text() {
        return "保存数字范围";
    }

    private void showNumberSettings() {
        Scheme currentScheme = app.getCurrentScheme();
        if (currentScheme != null) {
            NumberPicker numberPicker = new NumberPicker((Frame) app, currentScheme.getName());
            numberPicker.setVisible(true);
            LogManager.log(currentScheme.getName(), "设置数字范围");
        } else {
            JOptionPane.showMessageDialog(app, "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void saveNumberRange() {
        Scheme currentScheme = app.getCurrentScheme();
        if (currentScheme != null) {
            NumberRange range = schemeManager.getNumberRange(currentScheme.getName());
            if (range != null) {
                LogManager.log(currentScheme.getName() + "-[" + range.getMin() + "," + range.getMax() + "]", "保存数字范围");
                JOptionPane.showMessageDialog(app, "当前数字范围已保存：[" + range.getMin() + ", " + range.getMax() + "]",
                        "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(app, "当前方案尚未设置数字范围！", "提示", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(app, "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}

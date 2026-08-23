import javax.swing.*;
import java.io.File;

public class NameListModeHandler extends ModeHandler {
    private JButton button1;
    private JButton button2;

    public NameListModeHandler(NamePickerApp app) {
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
        importNameList();
    }

    @Override
    public void handleButton2Click() {
        exportNameList();
    }

    @Override
    public String getButton1Text() {
        return "导入名单";
    }

    @Override
    public String getButton2Text() {
        return "导出名单";
    }

    private void importNameList() {
        if (PasswordManager.isLocked()) {
            JOptionPane.showMessageDialog(app, "当前为锁定模式，请先解锁", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        Scheme currentScheme = app.getCurrentScheme();
        if (currentScheme != null) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));

            if (fileChooser.showOpenDialog(app) == JFileChooser.APPROVE_OPTION) {
                try {
                    File file = fileChooser.getSelectedFile();
                    nameManager.importFromFile(currentScheme.getName(), file);
                    JOptionPane.showMessageDialog(app, "名单导入成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(app, "导入失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        } else {
            JOptionPane.showMessageDialog(app, "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void exportNameList() {
        if (PasswordManager.isLocked()) {
            JOptionPane.showMessageDialog(app, "当前为锁定模式，请先解锁", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        Scheme currentScheme = app.getCurrentScheme();
        if (currentScheme != null) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));

            if (fileChooser.showSaveDialog(app) == JFileChooser.APPROVE_OPTION) {
                try {
                    File file = fileChooser.getSelectedFile();
                    if (!file.getName().toLowerCase().endsWith(".txt")) {
                        file = new File(file.getAbsolutePath() + ".txt");
                    }
                    nameManager.exportToFile(currentScheme.getName(), file);
                    JOptionPane.showMessageDialog(app, "名单导出成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(app, "导出失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        } else {
            JOptionPane.showMessageDialog(app, "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}

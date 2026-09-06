package com.randomnamepicker.mode;

import com.randomnamepicker.core.PasswordManager;
import com.randomnamepicker.model.Scheme;
import java.io.File;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * 内置“名字列表”模式（Stage2 重构后）。
 * <p>
 * 行为与迁移前一致：附加按钮为 导入名单 / 导出名单（受锁定拦截，JFileChooser 父窗口用宿主
 * getOwner()）；取样逻辑从原 NamePickerApp.startNamePicking 原样提取——
 * 每次抽取开始时经 canPick() 对当前方案名单做一次快照，滚动时从快照随机取。
 * </p>
 */
public class NameListModeHandler extends ModeHandler {

    public static final String MODE_ID = "name_list";
    public static final String DISPLAY_NAME = "名字列表模式";

    private final SecureRandom random = new SecureRandom();
    private List<String> cachedNames;

    public NameListModeHandler(ModeHost host) {
        super(host);
    }

    @Override
    public String getModeId() {
        return MODE_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public String canPick() {
        Scheme currentScheme = host.getCurrentScheme();
        if (currentScheme == null) {
            return "名单已损坏，请重新导入";
        }
        List<String> names = nameManager.loadNamesForScheme(currentScheme.getName());
        if (names.isEmpty()) {
            cachedNames = null;
            return "名单已损坏，请重新导入";
        }
        cachedNames = names;
        return null;
    }

    @Override
    public Supplier<String> nextCandidate() {
        return () -> {
            List<String> names = cachedNames;
            if (names == null || names.isEmpty()) {
                // 兜底：未经 canPick() 直接取候选时重新载入（正常流程总是先调 canPick()）
                Scheme currentScheme = host.getCurrentScheme();
                if (currentScheme != null) {
                    names = nameManager.loadNamesForScheme(currentScheme.getName());
                    cachedNames = names;
                }
            }
            if (names == null || names.isEmpty()) {
                return "";
            }
            return names.get(random.nextInt(names.size()));
        };
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
            JOptionPane.showMessageDialog(host.getOwner(), "当前为锁定模式，请先解锁", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Scheme currentScheme = host.getCurrentScheme();
        if (currentScheme != null) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("文本文件 (*.txt)", "txt"));

            if (fileChooser.showOpenDialog(host.getOwner()) == JFileChooser.APPROVE_OPTION) {
                try {
                    File file = fileChooser.getSelectedFile();
                    nameManager.importFromFile(currentScheme.getName(), file);
                    JOptionPane.showMessageDialog(host.getOwner(), "名单导入成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(host.getOwner(), "导入失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        } else {
            JOptionPane.showMessageDialog(host.getOwner(), "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void exportNameList() {
        if (PasswordManager.isLocked()) {
            JOptionPane.showMessageDialog(host.getOwner(), "当前为锁定模式，请先解锁", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Scheme currentScheme = host.getCurrentScheme();
        if (currentScheme != null) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("文本文件 (*.txt)", "txt"));

            if (fileChooser.showSaveDialog(host.getOwner()) == JFileChooser.APPROVE_OPTION) {
                try {
                    File file = fileChooser.getSelectedFile();
                    if (!file.getName().toLowerCase().endsWith(".txt")) {
                        file = new File(file.getAbsolutePath() + ".txt");
                    }
                    nameManager.exportToFile(currentScheme.getName(), file);
                    JOptionPane.showMessageDialog(host.getOwner(), "名单导出成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(host.getOwner(), "导出失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        } else {
            JOptionPane.showMessageDialog(host.getOwner(), "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}

package com.randomnamepicker.mode;

import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.model.NumberRange;
import com.randomnamepicker.model.Scheme;
import com.randomnamepicker.ui.NumberPicker;
import java.awt.Frame;
import java.security.SecureRandom;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

/**
 * 内置“数字”模式（Stage2 重构后）。
 * <p>
 * 行为与迁移前一致：附加按钮为 设置数字范围（弹 NumberPicker）/ 保存数字范围；
 * 数字取样公式原样保留：nextInt(max - min + 1) + min。范围在每次抽取开始时
 * 经 canPick() 做一次快照，滚动期间与旧实现一致使用快照。
 * </p>
 */
public class NumberModeHandler extends ModeHandler {

    public static final String MODE_ID = "number";
    public static final String DISPLAY_NAME = "数字模式";

    private final SecureRandom random = new SecureRandom();
    private NumberRange cachedRange;

    public NumberModeHandler(ModeHost host) {
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
            return "请先设置数字范围！";
        }
        NumberRange range = schemeManager.getNumberRange(currentScheme.getName());
        if (range == null) {
            cachedRange = null;
            return "请先设置数字范围！";
        }
        cachedRange = range;
        return null;
    }

    @Override
    public Supplier<String> nextCandidate() {
        return () -> {
            NumberRange range = cachedRange;
            if (range == null) {
                // 兜底：未经 canPick() 直接取候选时重新读取（正常流程总是先调 canPick()）
                Scheme currentScheme = host.getCurrentScheme();
                if (currentScheme != null) {
                    range = schemeManager.getNumberRange(currentScheme.getName());
                    cachedRange = range;
                }
            }
            if (range == null) {
                return "";
            }
            int randomNum = random.nextInt(range.getMax() - range.getMin() + 1) + range.getMin();
            return String.valueOf(randomNum);
        };
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
        Scheme currentScheme = host.getCurrentScheme();
        if (currentScheme != null) {
            NumberPicker numberPicker = new NumberPicker((Frame) host.getOwner(), currentScheme.getName());
            numberPicker.setVisible(true);
            LogManager.log(currentScheme.getName(), "设置数字范围");
        } else {
            JOptionPane.showMessageDialog(host.getOwner(), "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void saveNumberRange() {
        Scheme currentScheme = host.getCurrentScheme();
        if (currentScheme != null) {
            NumberRange range = schemeManager.getNumberRange(currentScheme.getName());
            if (range != null) {
                LogManager.log(currentScheme.getName() + "-[" + range.getMin() + "," + range.getMax() + "]", "保存数字范围");
                JOptionPane.showMessageDialog(host.getOwner(), "当前数字范围已保存：[" + range.getMin() + ", " + range.getMax() + "]",
                        "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(host.getOwner(), "当前方案尚未设置数字范围！", "提示", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(host.getOwner(), "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}

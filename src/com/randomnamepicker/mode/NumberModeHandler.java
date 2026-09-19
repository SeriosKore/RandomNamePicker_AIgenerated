package com.randomnamepicker.mode;

import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.model.NumberRange;
import com.randomnamepicker.model.Scheme;
import com.randomnamepicker.ui.NumberPicker;
import java.security.SecureRandom;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

/**
 * 内置“数字”模式（Stage2 重构后；P0 修复批次调整）。
 * <p>
 * 行为：附加按钮为 设置数字范围（弹 NumberPicker）/ 查看当前设置。
 * </p>
 * <p>
 * P0 修复批次改动：
 * <ul>
 *   <li>T1：弹设置对话框改用 {@code host.getHostWindow()}——{@code host.getOwner()} 对
 *       顶级主窗返回 null，原写法会让对话框把父窗当成 null 后在构造期 NPE；</li>
 *   <li>T4：取样跨度以 long 计算，避免 {@code max - min + 1} 在 int 下溢出成非正数导致
 *       {@code nextInt(bound)} 抛 IllegalArgumentException（透支被误报为“插件模式异常”）；</li>
 *   <li>Q-e：支持负数与单值范围（{@code min == max}），仅禁止 {@code min > max}；</li>
 *   <li>D2：{@code nextCandidate()} 返回的 Supplier 在创建时冻结快照，不再读可变字段，
 *       避免另一宿主（悬浮球）的 canPick 覆盖进行中滚动的数据源。</li>
 * </ul>
 * 范围在每次抽取开始时经 canPick() 做一次快照；滚动期间与旧实现一致使用快照。
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
            cachedRange = null;
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
        // D2：创建 Supplier 时冻结快照（滚动期间不被其它宿主的 canPick 改写）
        final NumberRange snapshot = cachedRange;
        return () -> {
            NumberRange range = snapshot;
            if (range == null) {
                // 兜底：未经 canPick() 直接取候选时重新读取（正常流程总是先调 canPick()）
                Scheme currentScheme = host.getCurrentScheme();
                if (currentScheme != null) {
                    range = schemeManager.getNumberRange(currentScheme.getName());
                }
            }
            if (range == null) {
                return "";
            }
            return String.valueOf(nextInRange(range.getMin(), range.getMax()));
        };
    }

    /**
     * 区间内随机整数（Q-e：允许负数与单值范围；T4：跨度以 long 计算防溢出）。
     * <p>
     * 合法范围（{@code min <= max} 且跨度 ≤ 2^31-1）走 {@code nextInt(span) + min}，无偏差；
     * 存量超大跨度数据兜底走 {@code floorMod(nextLong(), span)}，不抛异常、不返回越界值。
     * </p>
     */
    private int nextInRange(int min, int max) {
        long span = (long) max - (long) min + 1L;
        if (span <= 1L) {
            return min;
        }
        if (span <= Integer.MAX_VALUE) {
            return random.nextInt((int) span) + min;
        }
        return (int) (min + Math.floorMod(random.nextLong(), span));
    }

    @Override
    public void handleButton1Click() {
        showNumberSettings();
    }

    @Override
    public void handleButton2Click() {
        showCurrentRange();
    }

    @Override
    public String getButton1Text() {
        return "设置数字范围";
    }

    @Override
    public String getButton2Text() {
        return "查看当前设置";
    }

    private void showNumberSettings() {
        Scheme currentScheme = host.getCurrentScheme();
        if (currentScheme != null) {
            // T1：必须用 getHostWindow()（getOwner() 对顶级主窗为 null）
            NumberPicker numberPicker = new NumberPicker(host.getHostWindow(), currentScheme.getName());
            numberPicker.setVisible(true);
            LogManager.log(currentScheme.getName(), "设置数字范围");
        } else {
            JOptionPane.showMessageDialog(host.getHostWindow(), "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 按钮2：只读回显当前已存范围（Q-f 显式保存语义下，保存只发生在 NumberPicker 内）。 */
    private void showCurrentRange() {
        Scheme currentScheme = host.getCurrentScheme();
        if (currentScheme != null) {
            NumberRange range = schemeManager.getNumberRange(currentScheme.getName());
            if (range != null) {
                LogManager.log(currentScheme.getName() + "-[" + range.getMin() + "," + range.getMax() + "]", "查看数字范围");
                JOptionPane.showMessageDialog(host.getHostWindow(),
                        "当前数字范围：[" + range.getMin() + ", " + range.getMax() + "]\n"
                                + "（修改请在“设置数字范围”中保存）",
                        "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(host.getHostWindow(), "当前方案尚未设置数字范围！", "提示", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(host.getHostWindow(), "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}

package com.randomnamepicker.mode;

import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.model.Scheme;
import com.randomnamepicker.model.SeatConfig;
import com.randomnamepicker.ui.SeatPicker;
import java.awt.Point;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

/**
 * 内置“座位”模式（Stage2 重构后；P0 修复批次调整）。
 * <p>
 * 行为：附加按钮为 设置座位布局（弹 SeatPicker）/ 查看当前设置；
 * 座位候选格式与主窗旧显示一致："(" + x + ", " + y + ")"。座位配置在每次
 * 抽取开始时经 canPick() 做一次快照，滚动期间与旧实现一致使用快照。
 * </p>
 * <p>
 * P0 修复批次改动：
 * <ul>
 *   <li>T1：弹设置对话框改用 {@code host.getHostWindow()}（getOwner() 对顶级主窗为 null，
 *       原写法致 SeatPicker 内 mainApp 为 null 并在构造期 NPE）；</li>
 *   <li>T3.5：越界座位（坐标超出 rows/cols）由 SchemeManager.getSeatConfig 读取时过滤，
 *       本模式无需再判，候选集恒为合法座位；</li>
 *   <li>D2：{@code nextCandidate()} 返回的 Supplier 在创建时冻结快照。</li>
 * </ul>
 * </p>
 */
public class SeatModeHandler extends ModeHandler {

    public static final String MODE_ID = "seat";
    public static final String DISPLAY_NAME = "座位模式";

    private final SecureRandom random = new SecureRandom();
    private SeatConfig cachedConfig;

    public SeatModeHandler(ModeHost host) {
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
            cachedConfig = null;
            return "请先设置座位并选择座位！";
        }
        SeatConfig config = schemeManager.getSeatConfig(currentScheme.getName());
        if (config == null || config.getSelectedSeats().isEmpty()) {
            cachedConfig = null;
            return "请先设置座位并选择座位！";
        }
        cachedConfig = config;
        return null;
    }

    @Override
    public Supplier<String> nextCandidate() {
        // D2：创建 Supplier 时冻结快照（滚动期间不被其它宿主的 canPick 改写）
        final SeatConfig snapshot = cachedConfig;
        return () -> {
            SeatConfig config = snapshot;
            if (config == null) {
                // 兜底：未经 canPick() 直接取候选时重新读取（正常流程总是先调 canPick()）
                Scheme currentScheme = host.getCurrentScheme();
                if (currentScheme != null) {
                    config = schemeManager.getSeatConfig(currentScheme.getName());
                }
            }
            if (config == null || config.getSelectedSeats().isEmpty()) {
                return "";
            }
            List<Point> seats = config.getSelectedSeats();
            Point randomSeat = seats.get(random.nextInt(seats.size()));
            return "(" + randomSeat.x + ", " + randomSeat.y + ")";
        };
    }

    @Override
    public void handleButton1Click() {
        showSeatSettings();
    }

    @Override
    public void handleButton2Click() {
        showCurrentSeatConfig();
    }

    @Override
    public String getButton1Text() {
        return "设置座位布局";
    }

    @Override
    public String getButton2Text() {
        return "查看当前设置";
    }

    private void showSeatSettings() {
        Scheme currentScheme = host.getCurrentScheme();
        if (currentScheme != null) {
            // T1：必须用 getHostWindow()（getOwner() 对顶级主窗为 null）
            SeatPicker seatPicker = new SeatPicker(host.getHostWindow(), currentScheme.getName());
            seatPicker.setVisible(true);
            LogManager.log(currentScheme.getName(), "设置座位布局");
        } else {
            JOptionPane.showMessageDialog(host.getHostWindow(), "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 按钮2：只读回显当前已存座位配置（Q-f 显式保存语义下，保存只发生在 SeatPicker 内）。 */
    private void showCurrentSeatConfig() {
        Scheme currentScheme = host.getCurrentScheme();
        if (currentScheme != null) {
            SeatConfig config = schemeManager.getSeatConfig(currentScheme.getName());
            if (config != null) {
                LogManager.log(currentScheme.getName() + "-行数=" + config.getRows() + ",列数=" + config.getCols()
                        + ",已选座位数=" + config.getSelectedSeats().size(), "查看座位设置");
                JOptionPane.showMessageDialog(host.getHostWindow(),
                        "当前座位设置：行数=" + config.getRows() + "，列数=" + config.getCols()
                                + "，已选座位数=" + config.getSelectedSeats().size() + "\n"
                                + "（修改请在“设置座位布局”中保存）",
                        "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(host.getHostWindow(), "当前方案尚未设置座位！", "提示", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(host.getHostWindow(), "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}

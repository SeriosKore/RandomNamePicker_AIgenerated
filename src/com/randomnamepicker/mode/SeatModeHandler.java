package com.randomnamepicker.mode;

import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.model.Scheme;
import com.randomnamepicker.model.SeatConfig;
import com.randomnamepicker.ui.SeatPicker;
import java.awt.Frame;
import java.awt.Point;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

/**
 * 内置“座位”模式（Stage2 重构后）。
 * <p>
 * 行为与迁移前一致：附加按钮为 设置座位布局（弹 SeatPicker）/ 保存座位设置；
 * 座位候选格式与主窗旧显示一致："(" + x + ", " + y + ")"。座位配置在每次
 * 抽取开始时经 canPick() 做一次快照，滚动期间与旧实现一致使用快照。
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
        return () -> {
            SeatConfig config = cachedConfig;
            if (config == null) {
                // 兜底：未经 canPick() 直接取候选时重新读取（正常流程总是先调 canPick()）
                Scheme currentScheme = host.getCurrentScheme();
                if (currentScheme != null) {
                    config = schemeManager.getSeatConfig(currentScheme.getName());
                    cachedConfig = config;
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
        Scheme currentScheme = host.getCurrentScheme();
        if (currentScheme != null) {
            SeatPicker seatPicker = new SeatPicker((Frame) host.getOwner(), currentScheme.getName());
            seatPicker.setVisible(true);
            LogManager.log(currentScheme.getName(), "设置座位布局");
        } else {
            JOptionPane.showMessageDialog(host.getOwner(), "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void saveSeatConfig() {
        Scheme currentScheme = host.getCurrentScheme();
        if (currentScheme != null) {
            SeatConfig config = schemeManager.getSeatConfig(currentScheme.getName());
            if (config != null) {
                LogManager.log(currentScheme.getName() + "-行数=" + config.getRows() + ",列数=" + config.getCols() + ",已选座位数=" + config.getSelectedSeats().size(), "保存座位设置");
                JOptionPane.showMessageDialog(host.getOwner(), "座位设置已保存：行数=" + config.getRows() + "，列数=" + config.getCols() +
                                "，已选座位数=" + config.getSelectedSeats().size(),
                        "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(host.getOwner(), "当前方案尚未设置座位！", "提示", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(host.getOwner(), "请先选择一个方案！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}

package com.randomnamepicker.ui;

import com.randomnamepicker.plugin.PluginManager;
import com.randomnamepicker.plugin.UiZone;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * 宿主 UI 扩展帮助类（插件 UI 槽一期）。
 * <p>
 * 按 Zone 从 PluginManager 取插件动作并生成渲染面板；空 Zone 返回 null（调用方不渲染，
 * 保证无插件时界面零变化）。仅供宿主在 EDT 调用，本类不做线程切换。
 * </p>
 */
public final class PluginUiSupport {

    private PluginUiSupport() {
    }

    /**
     * 生成指定 Zone 的插件动作面板：带 "插件" 标题的按钮行（每按钮=一个动作，点击经
     * {@link PluginManager#runMenuActionSafely} 防御执行）；无动作返回 null。
     */
    public static JPanel createZonePanel(UiZone zone) {
        List<PluginManager.MenuAction> actions = PluginManager.getInstance().getUiActions(zone);
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        panel.setBorder(BorderFactory.createTitledBorder("插件"));
        for (PluginManager.MenuAction act : actions) {
            JButton button = new JButton(act.getTitle());
            PluginManager.MenuAction captured = act;
            button.addActionListener(e -> PluginManager.getInstance()
                    .runMenuActionSafely(captured.getPluginName(), captured.getAction(), e));
            panel.add(button);
        }
        return panel;
    }
}

package com.randomnamepicker.theme;

import com.randomnamepicker.core.LogManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * “背景主题”设置分节（宿主内建主题；内嵌于 SettingsWindow，报告 2/3）。
 * 图库导入、全局背景（图片/模式/自由裁切/遮罩）、按窗口类别指定（继承全局）、
 * 表格浅透三档（T2，默认关，渲染灰度中仅持久化）、恢复默认。任何修改 → 持久化 + 实时重排所有已开窗口。
 */
public class ThemeSettingsPanel extends JPanel {

    private static final String[] CATEGORY_LABELS = {
            "主窗口", "配置名单", "方案管理", "数字设置", "座位设置", "系统设置", "其它对话框"
    };
    private static final String[] MODE_LABELS = {
            "居中", "适应（完整显示）", "填充（裁切填满）", "拉伸", "自由裁切"
    };
    private static final BackdropMode[] MODES = {
            BackdropMode.CENTER, BackdropMode.FIT, BackdropMode.FILL,
            BackdropMode.STRETCH, BackdropMode.CROP
    };
    private static final String NONE_ITEM = "（无）";
    private static final String INHERIT_ITEM = "（继承全局）";

    private final JCheckBox enabledCheck = new JCheckBox("启用照片背景");
    private final JSlider maskSlider = new JSlider(SwingConstants.HORIZONTAL,
            ThemeStore.MASK_MIN, ThemeStore.MASK_MAX, ThemeStore.MASK_DEFAULT);
    private final JLabel maskLabel = new JLabel();
    private final JComboBox<String> tierCombo = new JComboBox<>(new String[]{"表格区原生", "表格浅透（浅）", "表格浅透（中）"});
    private final JButton importButton = new JButton("导入照片…");
    private final JButton resetButton = new JButton("恢复默认");

    private final JComboBox<String> globalImageCombo = new JComboBox<>();
    private final JComboBox<String> globalModeCombo = new JComboBox<>(MODE_LABELS);
    private final JButton globalCropButton = new JButton("裁剪…");

    private final JComboBox<String>[] catImageCombos;
    private final JComboBox<String>[] catModeCombos;
    private final JButton[] catCropButtons;
    private boolean syncing = false;

    @SuppressWarnings("unchecked")
    public ThemeSettingsPanel() {
        catImageCombos = new JComboBox[ThemeStore.CATEGORY_IDS.length];
        catModeCombos = new JComboBox[ThemeStore.CATEGORY_IDS.length];
        catCropButtons = new JButton[ThemeStore.CATEGORY_IDS.length];

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new TitledBorder(
                BorderFactory.createLineBorder(new java.awt.Color(0x99, 0x99, 0x99), 1),
                "背景主题",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 13)));

        // ---- 顶部行：总开关 / 表格浅透 / 恢复默认 / 导入 ----
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        enabledCheck.setFont(font13());
        importButton.setFont(font12());
        resetButton.setFont(font12());
        JLabel tierLabel = new JLabel("表格浅透：");
        tierLabel.setFont(font12());
        tierCombo.setFont(font11());
        tierCombo.setMaximumRowCount(3);
        top.add(enabledCheck);
        top.add(tierLabel);
        top.add(tierCombo);
        top.add(importButton);
        top.add(resetButton);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        add(top);

        JLabel hint = new JLabel("说明：未导入图片或未启用时窗口零变化；卸载/恢复默认后全部还原。");
        hint.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        add(hint);

        add(Box.createVerticalStrut(4));

        // ---- 全局背景 ----
        JPanel globalPanel = sectionPanel("全局背景（所有窗口默认）");
        JPanel globalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        globalRow.add(label("图片：", 60));
        globalImageCombo.setFont(font11());
        globalImageCombo.setPreferredSize(new Dimension(190, 26));
        globalRow.add(globalImageCombo);
        globalRow.add(label("适配：", 46));
        globalModeCombo.setFont(font11());
        globalModeCombo.setPreferredSize(new Dimension(150, 26));
        globalRow.add(globalModeCombo);
        globalCropButton.setFont(font11());
        globalRow.add(globalCropButton);
        globalRow.add(Box.createHorizontalGlue());
        globalRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        globalPanel.add(globalRow);

        JPanel maskRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        maskRow.add(label("遮罩：", 60));
        maskSlider.setPreferredSize(new Dimension(220, 44));
        maskSlider.setMajorTickSpacing(60);
        maskSlider.setMinorTickSpacing(15);
        maskSlider.setPaintTicks(true);
        maskLabel.setFont(font12());
        maskRow.add(maskSlider);
        maskRow.add(maskLabel);
        maskRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        globalPanel.add(maskRow);
        add(globalPanel);

        add(Box.createVerticalStrut(4));

        // ---- 按窗口类别 ----
        JPanel catPanel = sectionPanel("按窗口类别指定（未指定=继承全局）");
        for (int i = 0; i < ThemeStore.CATEGORY_IDS.length; i++) {
            JComboBox<String> imgCombo = new JComboBox<>();
            imgCombo.setFont(font11());
            imgCombo.setPreferredSize(new Dimension(190, 24));
            catImageCombos[i] = imgCombo;
            JComboBox<String> modeCombo = new JComboBox<>(MODE_LABELS);
            modeCombo.setFont(font11());
            modeCombo.setPreferredSize(new Dimension(150, 24));
            catModeCombos[i] = modeCombo;
            JButton cropBtn = new JButton("裁");
            cropBtn.setFont(font10());
            cropBtn.setPreferredSize(new Dimension(46, 24));
            cropBtn.setToolTipText("自由裁切（仅“自由裁切”模式生效）");
            catCropButtons[i] = cropBtn;

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 1));
            row.add(label(CATEGORY_LABELS[i], 90));
            row.add(imgCombo);
            row.add(modeCombo);
            row.add(cropBtn);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            catPanel.add(row);
        }
        add(catPanel);

        add(Box.createVerticalStrut(2));

        wireEvents();
        refreshFromStore();
    }

    // ===================== 组装辅助 =====================

    private JLabel label(String text, int width) {
        JLabel l = new JLabel(text);
        l.setFont(font12());
        l.setPreferredSize(new Dimension(width, 24));
        return l;
    }

    private JPanel sectionPanel(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new TitledBorder(
                BorderFactory.createLineBorder(new java.awt.Color(0xBB, 0xBB, 0xBB), 1),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 12)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return p;
    }

    private Font font13() {
        return new Font("微软雅黑", Font.PLAIN, 13);
    }

    private Font font12() {
        return new Font("微软雅黑", Font.PLAIN, 12);
    }

    private Font font11() {
        return new Font("微软雅黑", Font.PLAIN, 11);
    }

    private Font font10() {
        return new Font("微软雅黑", Font.PLAIN, 10);
    }

    private Window ownerWindow() {
        return SwingUtilities.getWindowAncestor(this);
    }

    // ===================== 事件 =====================

    private void wireEvents() {
        enabledCheck.addActionListener(e -> {
            if (syncing) {
                return;
            }
            ThemeStore.setEnabled(enabledCheck.isSelected());
            applyAll();
        });

        importButton.addActionListener(e -> importPhotos());

        maskSlider.addChangeListener(e -> {
            if (syncing) {
                return;
            }
            int v = maskSlider.getValue();
            maskLabel.setText(v + (v == 0 ? "（关闭）" : ""));
            ThemeStore.setMask(v);
            applyAll();
        });

        resetButton.addActionListener(e -> {
            ThemeStore.resetToDefaults();
            applyAll();
            refreshFromStore();
        });

        tierCombo.addActionListener(e -> {
            if (syncing) {
                return;
            }
            ThemeStore.setTableTier(tierCombo.getSelectedIndex());
            applyAll();
        });

        globalImageCombo.addActionListener(e -> {
            if (syncing) {
                return;
            }
            ThemeStore.setGlobalImage(selectedId(globalImageCombo, NONE_ITEM));
            applyAll();
            refreshCropEnabled();
        });

        globalModeCombo.addActionListener(e -> {
            if (syncing) {
                return;
            }
            ThemeStore.setGlobalMode(selectedMode());
            applyAll();
            refreshCropEnabled();
        });

        globalCropButton.addActionListener(e -> chooseCrop(null));

        for (int i = 0; i < ThemeStore.CATEGORY_IDS.length; i++) {
            final int idx = i;
            catImageCombos[i].addActionListener(e -> {
                if (syncing) {
                    return;
                }
                ThemeStore.setCategoryImage(ThemeStore.CATEGORY_IDS[idx],
                        selectedId(catImageCombos[idx], INHERIT_ITEM));
                applyAll();
            });
            catModeCombos[i].addActionListener(e -> {
                if (syncing) {
                    return;
                }
                ThemeStore.setCategoryMode(ThemeStore.CATEGORY_IDS[idx],
                        MODES[catModeCombos[idx].getSelectedIndex()]);
                applyAll();
            });
            catCropButtons[i].addActionListener(e -> chooseCrop(ThemeStore.CATEGORY_IDS[idx]));
        }
    }

    private BackdropMode selectedMode() {
        int i = Math.max(0, globalModeCombo.getSelectedIndex());
        return MODES[Math.min(i, MODES.length - 1)];
    }

    private String selectedId(JComboBox<String> combo, String emptyItem) {
        Object sel = combo.getSelectedItem();
        if (sel == null || emptyItem.equals(sel)) {
            return "";
        }
        return sel.toString();
    }

    private void importPhotos() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("图片文件 (jpg/png/bmp/gif/webp)",
                "jpg", "jpeg", "png", "bmp", "gif", "webp"));
        if (chooser.showOpenDialog(ownerWindow()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        ThemeStore.ensureDirs();
        List<String> added = new ArrayList<>();
        for (File f : chooser.getSelectedFiles()) {
            String id = ThemeStore.importImage(f);
            if (id != null) {
                added.add(id);
            }
        }
        if (added.isEmpty()) {
            return;
        }
        LogManager.log("主题-导入图片 " + added.size() + " 张", "THEME_IMPORT");
        // 全局尚未选图时，自动把首张设为全局背景，便于快速看到效果
        if (ThemeStore.getGlobalImage().isEmpty() && !added.isEmpty()) {
            ThemeStore.setGlobalImage(added.get(0));
        }
        refreshFromStore();
        applyAll();
    }

    private void chooseCrop(String category) {
        String imageId = category == null ? ThemeStore.getGlobalImage()
                : ThemeStore.categoryResolvedImageId(category);
        File file = ThemeStore.imageFile(imageId);
        Rectangle2D.Double initial = category == null
                ? ThemeStore.parseCrop(ThemeStore.configCropString(null))
                : ThemeStore.parseCrop(ThemeStore.configCropString(category));
        Rectangle2D.Double crop = CropSelectorDialog.showDialog(ownerWindow(), file, initial);
        if (crop == null) {
            return;
        }
        if (category == null) {
            ThemeStore.setGlobalCrop(crop);
        } else {
            ThemeStore.setCategoryCrop(category, crop);
        }
        applyAll();
    }

    // ===================== 刷新 / 应用 =====================

    private void refreshFromStore() {
        syncing = true;
        try {
            enabledCheck.setSelected(ThemeStore.isEnabled());
            int mask = ThemeStore.getMask();
            maskSlider.setValue(mask);
            maskLabel.setText(mask == 0 ? "0（关闭）" : mask + "");
            tierCombo.setSelectedIndex(ThemeStore.getTableTier());

            // 图库
            List<String> images = ThemeStore.listImages();
            fillImageCombo(globalImageCombo, images, NONE_ITEM, ThemeStore.getGlobalImage());
            selectMode(globalModeCombo, ThemeStore.globalModeNow());
            for (int i = 0; i < ThemeStore.CATEGORY_IDS.length; i++) {
                String cid = ThemeStore.CATEGORY_IDS[i];
                fillImageCombo(catImageCombos[i], images, INHERIT_ITEM,
                        ThemeStore.categoryImageIdNow(cid));
                selectMode(catModeCombos[i], ThemeStore.categoryModeNow(cid));
            }
            refreshCropEnabled();
        } finally {
            syncing = false;
        }
    }

    private void fillImageCombo(JComboBox<String> combo, List<String> images,
                                String emptyItem, String current) {
        combo.removeAllItems();
        combo.addItem(emptyItem);
        int sel = 0;
        for (int i = 0; i < images.size(); i++) {
            String id = images.get(i);
            combo.addItem(id);
            if (id.equals(current)) {
                sel = i + 1;
            }
        }
        combo.setSelectedIndex(sel);
    }

    private void selectMode(JComboBox<String> combo, BackdropMode mode) {
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i] == mode) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.setSelectedIndex(0);
    }

    private void refreshCropEnabled() {
        boolean globalHasImage = ThemeStore.imageFile(ThemeStore.getGlobalImage()) != null;
        globalCropButton.setEnabled(globalHasImage
                && selectedMode() == BackdropMode.CROP);
        for (int i = 0; i < ThemeStore.CATEGORY_IDS.length; i++) {
            catCropButtons[i].setEnabled(ThemeStore.imageFile(
                    ThemeStore.categoryResolvedImageId(ThemeStore.CATEGORY_IDS[i])) != null
                    && MODES[Math.max(0, catModeCombos[i].getSelectedIndex())] == BackdropMode.CROP);
        }
    }

    private void applyAll() {
        BackdropManager.getInstance().reapplyAll();
    }
}

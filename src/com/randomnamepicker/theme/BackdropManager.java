package com.randomnamepicker.theme;

import com.randomnamepicker.core.LogManager;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 背景引擎（宿主内建主题，报告 2 §1/§4）：每窗口装配一次背景层，负责
 * 内容容器透明化、resize/shown/hidden 时背景层同步与重绘、主题开关/类别切换时的整体重排，
 * 以及窗口关闭/宿主退出时的还原清理。仅 EDT 使用。
 * <p>
 * 透明化策略（T1 口径，T2 表格浅透在接线阶段实现）：仅翻转普通 JPanel/Box 类纯容器，
 * 表格/文本/按钮/滑块等一律不翻转（保样式与可读）；每窗口记录翻转清单以便还原。
 * </p>
 */
public final class BackdropManager {

    private static final BackdropManager INSTANCE = new BackdropManager();

    private final Map<Window, Session> sessions = new IdentityHashMap<>();
    private AWTEventListener autoAttachListener;

    private BackdropManager() {
    }

    public static BackdropManager getInstance() {
        return INSTANCE;
    }

    // ===================== 集中自动装配（宿主启动注册一次） =====================

    /**
     * 注册全局窗口侦测：宿主窗口 WINDOW_OPENED 时按类名自动装配（无需逐窗接线）。
     * 仅识别已知宿主窗口类；密码类/悬浮球/插件自有窗口等不在映射表内 → 自动豁免。
     */
    public void registerAutoAttach() {
        if (autoAttachListener != null) {
            return;
        }
        autoAttachListener = event -> {
            if (!(event instanceof WindowEvent)) {
                return;
            }
            WindowEvent we = (WindowEvent) event;
            if (we.getID() != WindowEvent.WINDOW_OPENED) {
                return;
            }
            Window w = we.getWindow();
            String category = categoryOf(w);
            if (category != null) {
                install(w, category);
            }
        };
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(autoAttachListener,
                    AWTEvent.WINDOW_EVENT_MASK);
        } catch (Throwable t) {
            autoAttachListener = null;
            LogManager.log("主题-窗口侦测注册失败: " + t, "PLUGIN_LOAD_ERROR");
        }
    }

    public void unregisterAutoAttach() {
        if (autoAttachListener != null) {
            try {
                Toolkit.getDefaultToolkit().removeAWTEventListener(autoAttachListener);
            } catch (Throwable ignore) {
                // 忽略
            }
            autoAttachListener = null;
        }
    }

    /** 宿主窗口类名 → 类别；不在映射内返回 null（豁免）。 */
    private String categoryOf(Window w) {
        if (w == null) {
            return null;
        }
        String n = w.getClass().getSimpleName();
        if ("NamePickerApp".equals(n)) {
            return ThemeStore.CATEGORY_MAIN;
        }
        if ("ConfigWindow".equals(n)) {
            return ThemeStore.CATEGORY_CONFIG;
        }
        if ("SchemeManagerDialog".equals(n)) {
            return ThemeStore.CATEGORY_SCHEME;
        }
        if ("NumberPicker".equals(n)) {
            return ThemeStore.CATEGORY_NUMBER;
        }
        if ("SeatPicker".equals(n)) {
            return ThemeStore.CATEGORY_SEAT;
        }
        if ("SettingsWindow".equals(n)) {
            return ThemeStore.CATEGORY_SETTINGS;
        }
        return null;
    }

    // ===================== 装配 / 拆卸 =====================

    /** 窗口装配（宿主各窗口构造尾部调用一次；仅支持 JFrame/JDialog）。 */
    public void install(Window window, String category) {
        if (window == null || !(window instanceof JFrame || window instanceof JDialog)) {
            return;
        }
        if (sessions.containsKey(window)) {
            return;
        }
        Session session = new Session(window, category);
        session.attach();
        sessions.put(window, session);
        LogManager.log("主题-窗口装配: " + category + " / " + window.getClass().getSimpleName(),
                "THEME_DEBUG");
    }

    /** 卸载某窗口并还原（窗口关闭时自动调用；卸载插件/退出亦可手动调用）。 */
    public void uninstall(Window window) {
        if (window == null) {
            return;
        }
        Session s = sessions.remove(window);
        if (s != null) {
            s.detach();
        }
    }

    /** 全部还原（宿主退出清理用）。 */
    public void shutdown() {
        unregisterAutoAttach();
        List<Window> all = new ArrayList<>(sessions.keySet());
        for (Window w : all) {
            uninstall(w);
        }
    }

    /** 某类别（或全部）重新解析并应用当前主题（开关/图片/模式/遮罩变化后调用）。 */
    public void reapply(String category) {
        boolean all = category == null;
        for (Session s : sessions.values()) {
            if (all || category.equals(s.category)) {
                s.apply();
            }
        }
    }

    public void reapplyAll() {
        reapply(null);
    }

    // ===================== 单窗口会话 =====================

    private final class Session {
        final Window window;
        final String category;
        final WindowBackdrop backdrop = new WindowBackdrop();
        final List<JComponent> flattened = new ArrayList<>();
        final List<Boolean> flattenedOpaque = new ArrayList<>();
        final List<TableSnapshot> tables = new ArrayList<>();
        final ComponentAdapter sizeAdapter = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                syncBackdropBounds();
            }

            @Override
            public void componentShown(ComponentEvent e) {
                syncBackdropBounds();
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                syncBackdropBounds();
            }
        };
        final WindowAdapter closeAdapter = new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                uninstall(window);
            }
        };

        Session(Window window, String category) {
            this.window = window;
            this.category = category != null ? category : ThemeStore.CATEGORY_OTHER;
        }

        void attach() {
            JRootPane root = rootPane();
            if (root == null) {
                return;
            }
            JLayeredPane layered = root.getLayeredPane();
            Integer layer = Integer.valueOf(JLayeredPane.FRAME_CONTENT_LAYER - 1);
            layered.add(backdrop, layer);
            apply();
            window.addComponentListener(sizeAdapter);
            window.addWindowListener(closeAdapter);
            // 首次尺寸可能在 realize 后才准确，EDT 后补一次
            SwingUtilities.invokeLater(this::syncBackdropBounds);
        }

        void detach() {
            window.removeComponentListener(sizeAdapter);
            window.removeWindowListener(closeAdapter);
            restoreTables();
            restoreFlatten();
            JRootPane root = rootPane();
            if (root != null && root.getLayeredPane() != null) {
                root.getLayeredPane().remove(backdrop);
                root.getLayeredPane().revalidate();
                root.getLayeredPane().repaint();
            }
        }

        void apply() {
            ThemeStyle style = ThemeStore.resolve(category);
            backdrop.setStyle(style);
            if (style.isActive()) {
                flattenContent();
                applyTableTier();
            } else {
                restoreTables();
                restoreFlatten();
            }
            syncBackdropBounds();
        }

        void syncBackdropBounds() {
            if (!backdrop.isDisplayable()) {
                return;
            }
            JLayeredPane layered = backdrop.getParent() instanceof JLayeredPane
                    ? (JLayeredPane) backdrop.getParent() : null;
            if (layered == null) {
                JRootPane root = rootPane();
                if (root != null) {
                    layered = root.getLayeredPane();
                }
            }
            if (layered == null) {
                return;
            }
            int w = layered.getWidth();
            int h = layered.getHeight();
            if (w > 0 && h > 0 && (backdrop.getWidth() != w || backdrop.getHeight() != h)) {
                backdrop.setBounds(0, 0, w, h);
            }
            backdrop.repaint();
        }

        JRootPane rootPane() {
            if (window instanceof JFrame) {
                return ((JFrame) window).getRootPane();
            }
            if (window instanceof JDialog) {
                return ((JDialog) window).getRootPane();
            }
            return null;
        }

        // ---- 内容透明化 + 表格融合（T1 全窗背景；T2 按档位“浅透”表格） ----

        void flattenContent() {
            if (!flattened.isEmpty()) {
                return; // 已翻转
            }
            JRootPane root = rootPane();
            if (root == null) {
                return;
            }
            Component content = root.getContentPane();
            if (content != null) {
                flattenTree(content);
            }
        }

        boolean isSensitiveLeaf(Component c) {
            return c instanceof javax.swing.text.JTextComponent
                    || c instanceof javax.swing.AbstractButton
                    || c instanceof javax.swing.table.JTableHeader
                    || c instanceof javax.swing.JComboBox
                    || c instanceof javax.swing.JSlider
                    || c instanceof javax.swing.JSpinner
                    || c instanceof javax.swing.JScrollBar
                    || c instanceof javax.swing.JTabbedPane
                    || c instanceof javax.swing.JTree
                    || c instanceof javax.swing.JList
                    || c instanceof javax.swing.JMenu
                    || c instanceof javax.swing.JMenuItem
                    || c instanceof javax.swing.JPopupMenu;
        }

        /** 递归翻转：contentPane/普通面板/JScrollPane 及其视口翻透明（表格另行“浅透”处理）。 */
        void flattenTree(Component c) {
            if (c instanceof JTable) {
                rememberTable((JTable) c);
                return;
            }
            if (isSensitiveLeaf(c)) {
                return;
            }
            if (c instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane) c;
                flip(sp);
                JViewport vp = sp.getViewport();
                if (vp != null) {
                    flip(vp);
                }
                Component view = vp == null ? null : vp.getView();
                if (view instanceof JTable) {
                    rememberTable((JTable) view); // 表格视口：翻透明但表格本体走浅透渲染
                } else if (view != null && !(view instanceof javax.swing.text.JTextComponent)
                        && !(view instanceof javax.swing.JTree)
                        && !(view instanceof javax.swing.JList)) {
                    flattenTree(view);
                }
                return; // 滚动条/角落保持原生
            }
            if (c instanceof JComponent && c instanceof Container) {
                flip((JComponent) c);
                for (Component child : ((Container) c).getComponents()) {
                    flattenTree(child);
                }
            } else if (c instanceof Container) {
                for (Component child : ((Container) c).getComponents()) {
                    flattenTree(child);
                }
            }
        }

        void flip(JComponent jc) {
            if (jc != null && jc.isOpaque() && jc != backdrop) {
                flattened.add(jc);
                flattenedOpaque.add(Boolean.TRUE);
                jc.setOpaque(false);
            }
        }

        void rememberTable(JTable t) {
            for (TableSnapshot s : tables) {
                if (s.table == t) {
                    return;
                }
            }
            TableSnapshot s = new TableSnapshot();
            s.table = t;
            s.capture();
            tables.add(s);
        }

        /** T2：按档位应用“单元格级浅透明白底”渲染（0=原生 / 1=浅(默认) / 2=中）。 */
        void applyTableTier() {
            int tier = ThemeStore.getTableTier();
            if (tier == 0) {
                restoreTables();
                return;
            }
            if (tables.isEmpty()) {
                rescanTables(); // 此前曾被 restore（档位切换），重扫收集（翻透明幂等）
            }
            int alpha = tier == 1 ? 232 : 210;
            for (TableSnapshot s : tables) {
                s.apply(alpha);
            }
        }

        /** 重扫内容树收集表格（已翻透明面板不会重复记录）。 */
        void rescanTables() {
            JRootPane root = rootPane();
            if (root == null || root.getContentPane() == null) {
                return;
            }
            flattenTree(root.getContentPane());
        }

        void restoreTables() {
            for (TableSnapshot s : tables) {
                s.restore();
            }
            tables.clear();
        }

        void restoreFlatten() {
            for (int i = 0; i < flattened.size(); i++) {
                JComponent c = flattened.get(i);
                Boolean orig = flattenedOpaque.get(i);
                if (orig != null && orig.booleanValue()) {
                    c.setOpaque(true);
                }
            }
            flattened.clear();
            flattenedOpaque.clear();
        }
    }

    // ===================== 表格浅透（T2 候选 A，报告 3） =====================

    /** 表格装饰快照：capture 一次记录原样，restore 完整还原（含切换档位/关闭主题/卸载）。 */
    private static final class TableSnapshot {
        JTable table;
        boolean tableOpaque;
        Color tableBg;
        TableCellRenderer objRenderer;
        TableCellRenderer strRenderer;

        void capture() {
            tableOpaque = table.isOpaque();
            tableBg = table.getBackground();
            objRenderer = table.getDefaultRenderer(Object.class);
            strRenderer = table.getDefaultRenderer(String.class);
        }

        void apply(int alpha) {
            TranslucentRenderer rend = new TranslucentRenderer(alpha);
            table.setOpaque(false);
            table.setDefaultRenderer(Object.class, rend);
            table.setDefaultRenderer(String.class, rend);
        }

        void restore() {
            if (table == null) {
                return;
            }
            try {
                table.setOpaque(tableOpaque);
                if (tableBg != null) {
                    table.setBackground(tableBg);
                }
                table.setDefaultRenderer(Object.class, objRenderer);
                table.setDefaultRenderer(String.class, strRenderer);
            } catch (Exception ignore) {
                // 单表还原异常忽略，不阻断其它还原
            }
        }
    }

    /** 单元格级“浅透明白底”渲染器：非选中格以半透明白盖在照片上（alpha 238=浅 / 215=中），文字保持深色可读。 */
    private static final class TranslucentRenderer extends DefaultTableCellRenderer {
        private final int alpha;

        TranslucentRenderer(int alpha) {
            this.alpha = alpha;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (comp instanceof JComponent) {
                JComponent jc = (JComponent) comp;
                jc.setOpaque(true);
                if (isSelected) {
                    jc.setBackground(table.getSelectionBackground());
                } else {
                    jc.setBackground(new Color(255, 255, 255, alpha));
                }
            }
            return comp;
        }
    }
}

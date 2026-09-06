package com.randomnamepicker.main;

import com.randomnamepicker.core.ConfigManager;
import com.randomnamepicker.core.PasswordManager;
import com.randomnamepicker.plugin.PluginManager;
import com.randomnamepicker.ui.NamePickerApp;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import javax.swing.*;

public class Main {
    private static NamePickerApp mainApp;
    private static SystemTray systemTray;
    private static TrayIcon trayIcon;
    /** 方案A：Swing 中文托盘菜单（替代 native AWT 菜单——原生菜单用系统 Segoe UI 字体，中文会渲染成方框） */
    private static JPopupMenu trayPopupMenu;
    /** 定位用 1×1 invoker 窗口（EDT） */
    private static JWindow trayMenuInvoker;
    /** 托盘图标 action 去抖（防双击触发两次） */
    private static long lastTrayActionTime = 0;
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        initializePassword();
        applyAutoStartSetting();

        SwingUtilities.invokeLater(() -> {
            mainApp = new NamePickerApp();
            PluginManager.getInstance().init(mainApp);
            
            if (SystemTray.isSupported()) {
                setupSystemTray();
                PluginManager.getInstance().addUIListener(Main::refreshTrayMenu);
                mainApp.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(null, 
                    "系统托盘不支持，程序将正常运行。\n关闭程序时悬浮球也会关闭。", 
                    "提示", 
                    JOptionPane.WARNING_MESSAGE);
                mainApp.setVisible(true);
            }

            // Stage3：后台线程加载插件，完成后 EDT 一次性刷新主窗/托盘（不阻塞 UI）
            PluginManager.getInstance().loadAllAsync();
        });
    }

    private static void initializePassword() {
        PasswordManager.isLocked();
    }
    
    private static void setupSystemTray() {
        systemTray = SystemTray.getSystemTray();
        
        ImageIcon icon = createTrayIconImage();
        // 方案A：不再使用 native AWT PopupMenu（系统 Segoe UI 菜单字体不含中文字形 → 方框）；
        // 托盘图标单击/双击 → 屏幕右下角弹出 Swing 中文菜单（中文正常渲染）。
        trayIcon = new TrayIcon(icon.getImage(), "多功能随机抽取器");
        trayIcon.setImageAutoSize(true);
        trayIcon.setToolTip("多功能随机抽取器\n单击托盘图标打开菜单");
        
        trayIcon.addActionListener(e -> SwingUtilities.invokeLater(Main::toggleTrayMenu));
        
        try {
            systemTray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, 
                "无法创建系统托盘图标: " + e.getMessage(), 
                "错误", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 插件加载提交成功后由 EDT 调用：重建 Swing 托盘菜单内容（若正显示则先收起）。 */
    private static void refreshTrayMenu() {
        if (trayIcon == null) {
            return;
        }
        JPopupMenu old = trayPopupMenu;
        trayPopupMenu = buildSwingTrayMenu();
        if (old != null && old.isVisible()) {
            old.setVisible(false);
        }
    }

    /** 托盘图标 action：切换（显示/收起）右下角 Swing 菜单；双击去抖。 */
    private static void toggleTrayMenu() {
        long now = System.currentTimeMillis();
        if (now - lastTrayActionTime < 350L) {
            return; // 双击的第二次 action 忽略
        }
        lastTrayActionTime = now;
        JPopupMenu menu = ensureTrayMenu();
        if (menu.isVisible()) {
            menu.setVisible(false);
            return;
        }
        // 屏幕右下角（避开任务栏）弹出
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Dimension ps = menu.getPreferredSize();
        int x = usable.x + usable.width - ps.width - 8;
        int y = usable.y + usable.height - ps.height - 8;
        if (trayMenuInvoker == null) {
            trayMenuInvoker = new JWindow();
            trayMenuInvoker.setType(Window.Type.UTILITY);
            trayMenuInvoker.setFocusableWindowState(false);
            trayMenuInvoker.setSize(1, 1);
        }
        trayMenuInvoker.setLocation(x, y);
        if (!trayMenuInvoker.isVisible()) {
            trayMenuInvoker.setVisible(true);
        }
        menu.setInvoker(trayMenuInvoker);
        menu.show(trayMenuInvoker, 0, 0);
    }

    private static JPopupMenu ensureTrayMenu() {
        if (trayPopupMenu == null) {
            trayPopupMenu = buildSwingTrayMenu();
        }
        return trayPopupMenu;
    }

    private static JMenuItem trayActionItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> action.run());
        return item;
    }

    private static void showMainWindow() {
        mainApp.setVisible(true);
        mainApp.setState(Frame.NORMAL);
        mainApp.toFront();
    }

    private static void hideMainWindow() {
        mainApp.setVisible(false);
    }

    /** Swing 托盘菜单 = 内置项 ＋ 插件项 ＋ 退出（替代 native PopupMenu；中文渲染正常）。 */
    private static JPopupMenu buildSwingTrayMenu() {
        JPopupMenu menu = new JPopupMenu();

        menu.add(trayActionItem("显示主窗口", Main::showMainWindow));
        menu.add(trayActionItem("隐藏主窗口", Main::hideMainWindow));
        menu.addSeparator();
        menu.add(trayActionItem("显示/隐藏悬浮球", () -> mainApp.toggleFloatingBall()));
        menu.addSeparator();

        // 插件托盘项（标题不判重；动作防御执行）
        List<PluginManager.MenuAction> pluginActions = PluginManager.getInstance().getTrayMenuActions();
        for (PluginManager.MenuAction act : pluginActions) {
            JMenuItem item = new JMenuItem(act.getTitle());
            PluginManager.MenuAction captured = act;
            item.addActionListener(e -> PluginManager.getInstance()
                    .runMenuActionSafely(captured.getPluginName(), captured.getAction(), e));
            menu.add(item);
        }
        if (!pluginActions.isEmpty()) {
            menu.addSeparator();
        }

        menu.add(trayActionItem("退出程序", Main::confirmAndExit));

        return menu;
    }

    private static void confirmAndExit() {
        int confirm = JOptionPane.showConfirmDialog(null,
            "确定要完全退出程序吗？\n退出后悬浮球也将关闭。",
            "确认退出",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            cleanupAndExit();
        }
    }
    
    private static ImageIcon createTrayIconImage() {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.setColor(new Color(70, 130, 180));
        g2d.fillOval(1, 1, size - 2, size - 2);
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("微软雅黑", Font.BOLD, 10));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "抽";
        int x = (size - fm.stringWidth(text)) / 2;
        int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
        g2d.drawString(text, x, y);
        
        g2d.dispose();
        return new ImageIcon(image);
    }
    
    public static void cleanupAndExit() {
        // 宿主内建主题：先还原全部窗口背景（卸载侦测/垫层/透明化）再退出
        com.randomnamepicker.theme.BackdropManager.getInstance().shutdown();
        // Stage3：先卸载插件（onUnload + 关闭 ClassLoader）再退出
        PluginManager.getInstance().shutdown();
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
        }
        System.exit(0);
    }

    private static void applyAutoStartSetting() {
        if (ConfigManager.isAutoStartEnabled()) {
            registerAutoStart();
        }
    }

    public static void registerAutoStart() {
        String currentPath = System.getProperty("user.dir");
        String exePath = currentPath + "\\RandomNamePicker.exe";
        File exeFile = new File(exePath);

        if (!exeFile.exists()) {
            JOptionPane.showMessageDialog(null,
                "未找到程序文件：RandomNamePicker.exe\n请确保程序在正确的位置。",
                "错误",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            String appName = "RandomNamePicker";
            ProcessBuilder processBuilder = new ProcessBuilder("reg", "add",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v", appName,
                    "/t", "REG_SZ",
                    "/d", "\"" + exePath + "\"",
                    "/f");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                JOptionPane.showMessageDialog(null,
                    "开机自启动已成功启用！\n\n注意：如果您的电脑有系统还原或注册表保护功能，\n此设置可能会被还原，导致自启动失效。",
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(null,
                    "注册表修改失败！\n退出代码：" + exitCode + "\n\n请以管理员身份运行程序后重试。",
                    "失败",
                    JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "设置开机自启动时发生错误：\n" + e.getMessage() + "\n\n请以管理员身份运行程序后重试。",
                "错误",
                JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    public static void unregisterAutoStart() {
        try {
            String appName = "RandomNamePicker";
            ProcessBuilder processBuilder = new ProcessBuilder("reg", "delete",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v", appName,
                    "/f");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                JOptionPane.showMessageDialog(null,
                    "开机自启动已成功禁用！",
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(null,
                    "注册表修改失败！\n退出代码：" + exitCode + "\n\n请以管理员身份运行程序后重试。",
                    "失败",
                    JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "取消开机自启动时发生错误：\n" + e.getMessage() + "\n\n请以管理员身份运行程序后重试。",
                "错误",
                JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    public static boolean checkAutoStartStatus() {
        try {
            String appName = "RandomNamePicker";
            ProcessBuilder processBuilder = new ProcessBuilder("reg", "query",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v", appName);
            Process process = processBuilder.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(appName)) {
                    return true;
                }
            }
            
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}

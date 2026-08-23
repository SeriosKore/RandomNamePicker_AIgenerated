import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class Main {
    private static NamePickerApp mainApp;
    private static SystemTray systemTray;
    private static TrayIcon trayIcon;
    
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
            
            if (SystemTray.isSupported()) {
                setupSystemTray();
                mainApp.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(null, 
                    "系统托盘不支持，程序将正常运行。\n关闭程序时悬浮球也会关闭。", 
                    "提示", 
                    JOptionPane.WARNING_MESSAGE);
                mainApp.setVisible(true);
            }
        });
    }

    private static void initializePassword() {
        PasswordManager.isLocked();
    }
    
    private static void setupSystemTray() {
        systemTray = SystemTray.getSystemTray();
        
        ImageIcon icon = createTrayIconImage();
        PopupMenu popup = new PopupMenu();
        
        MenuItem showItem = new MenuItem("显示主窗口");
        showItem.addActionListener(e -> {
            mainApp.setVisible(true);
            mainApp.setState(Frame.NORMAL);
            mainApp.toFront();
        });
        popup.add(showItem);
        
        MenuItem hideItem = new MenuItem("隐藏主窗口");
        hideItem.addActionListener(e -> {
            mainApp.setVisible(false);
        });
        popup.add(hideItem);
        
        popup.addSeparator();
        
        MenuItem toggleBallItem = new MenuItem("显示/隐藏悬浮球");
        toggleBallItem.addActionListener(e -> {
            mainApp.toggleFloatingBall();
        });
        popup.add(toggleBallItem);
        
        popup.addSeparator();
        
        MenuItem exitItem = new MenuItem("退出程序");
        exitItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(null,
                "确定要完全退出程序吗？\n退出后悬浮球也将关闭。",
                "确认退出",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            
            if (confirm == JOptionPane.YES_OPTION) {
                cleanupAndExit();
            }
        });
        popup.add(exitItem);
        
        trayIcon = new TrayIcon(icon.getImage(), "多功能随机抽取器", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.setToolTip("多功能随机抽取器\n双击显示主窗口");
        
        trayIcon.addActionListener(e -> {
            if (mainApp.isVisible()) {
                mainApp.setVisible(false);
            } else {
                mainApp.setVisible(true);
                mainApp.setState(Frame.NORMAL);
                mainApp.toFront();
            }
        });
        
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

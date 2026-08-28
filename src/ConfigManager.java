import java.io.*;
import java.util.Properties;

public class ConfigManager {
    private static final String CONFIG_FILE_PATH = "data/config.properties";
    public static Properties properties;
    
    static {
        properties = new Properties();
        loadConfig();
    }
    
    private static void loadConfig() {
        try {
            File configFile = new File(CONFIG_FILE_PATH);
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            if (configFile.exists()) {
                FileInputStream fis = new FileInputStream(configFile);
                properties.load(fis);
                fis.close();
            } else {
                createDefaultConfig(configFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static void createDefaultConfig(File configFile) throws IOException {
        properties.setProperty("autoStart", "false");
        properties.setProperty("floatingBallRadius", "50");
        properties.setProperty("floatingBallOpacity", "200");
        properties.setProperty("multiBallOpacity", "200");
        properties.setProperty("minimizeToTray", "true");
        properties.setProperty("pickCount", "1");
        
        try {
            String defaultPassword = "#include<bits/stdc++.h>usingnamespacestd;intmain(){return0;}";
            String hashedPassword = EncryptionUtil.hashPassword(defaultPassword);
            properties.setProperty("x7f9a2b1c4e8d3f6", hashedPassword);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        FileOutputStream fos = new FileOutputStream(configFile);
        properties.store(fos, "Random Name Picker Configuration");
        fos.close();
        
        System.out.println("配置文件已自动创建：" + configFile.getAbsolutePath());
    }

    private static void saveConfig() {
        try {
            File configFile = new File(CONFIG_FILE_PATH);
            Properties allProperties = new Properties();
            
            if (configFile.exists()) {
                FileInputStream fis = new FileInputStream(configFile);
                allProperties.load(fis);
                fis.close();
            }
            
            allProperties.setProperty("autoStart", properties.getProperty("autoStart", "false"));
            allProperties.setProperty("floatingBallRadius", properties.getProperty("floatingBallRadius", "50"));
            allProperties.setProperty("floatingBallOpacity", properties.getProperty("floatingBallOpacity", "200"));
            allProperties.setProperty("multiBallOpacity", properties.getProperty("multiBallOpacity", "200"));
            allProperties.setProperty("minimizeToTray", properties.getProperty("minimizeToTray", "true"));
            allProperties.setProperty("pickCount", properties.getProperty("pickCount", "1"));
            allProperties.setProperty("lastScheme", properties.getProperty("lastScheme", ""));
            
            FileOutputStream fos = new FileOutputStream(configFile);
            allProperties.store(fos, "Random Name Picker Configuration");
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static boolean isAutoStartEnabled() {
        String value = properties.getProperty("autoStart", "false");
        return Boolean.parseBoolean(value);
    }
    
    public static void setAutoStart(boolean enabled) {
        properties.setProperty("autoStart", String.valueOf(enabled));
        saveConfig();
    }
    
    public static int getFloatingBallRadius() {
        String value = properties.getProperty("floatingBallRadius", "50");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 50;
        }
    }
    
    public static void setFloatingBallRadius(int radius) {
        properties.setProperty("floatingBallRadius", String.valueOf(radius));
        saveConfig();
    }
    
    public static int getFloatingBallOpacity() {
        String value = properties.getProperty("floatingBallOpacity", "200");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 200;
        }
    }
    
    public static void setFloatingBallOpacity(int opacity) {
        properties.setProperty("floatingBallOpacity", String.valueOf(opacity));
        saveConfig();
    }

    /**
     * 多人点名动画中奖者小球透明度（0~255），默认为 200。
     */
    public static int getMultiBallOpacity() {
        String value = properties.getProperty("multiBallOpacity", "200");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 200;
        }
    }

    public static void setMultiBallOpacity(int opacity) {
        properties.setProperty("multiBallOpacity", String.valueOf(opacity));
        saveConfig();
    }
    
    public static boolean isMinimizeToTray() {
        String value = properties.getProperty("minimizeToTray", "true");
        return Boolean.parseBoolean(value);
    }
    
    public static void setMinimizeToTray(boolean minimize) {
        properties.setProperty("minimizeToTray", String.valueOf(minimize));
        saveConfig();
    }

    /**
     * 单次抽取数量（多人点名设置），默认为 1。
     */
    public static int getPickCount() {
        String value = properties.getProperty("pickCount", "1");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static void setPickCount(int count) {
        properties.setProperty("pickCount", String.valueOf(count));
        saveConfig();
    }
    
    public static String getLastScheme() {
        return properties.getProperty("lastScheme", "");
    }
    
    public static void setLastScheme(String schemeName) {
        if (schemeName == null || schemeName.isEmpty()) {
            properties.remove("lastScheme");
        } else {
            properties.setProperty("lastScheme", schemeName);
        }
        saveConfig();
    }
}

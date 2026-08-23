import java.io.*;
import java.util.Properties;

public class PasswordManager {
    private static final String CONF_KEY_A = "x7f9a2b1c4e8d3f6";
    
    private static Properties passwordProperties;
    private static boolean isUnlocked = false;
    private static int ctrlLCount = 0;
    private static long lastCtrlLTime = 0;

    static {
        loadPasswordConfig();
    }

    private static void loadPasswordConfig() {
        passwordProperties = new Properties();
        try {
            File configFile = new File("data/config.properties");
            if (configFile.exists()) {
                FileInputStream fis = new FileInputStream(configFile);
                passwordProperties.load(fis);
                fis.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void savePasswordConfig() {
        try {
            File configFile = new File("data/config.properties");
            
            if (!configFile.exists()) {
                return;
            }
            
            FileInputStream fis = new FileInputStream(configFile);
            Properties allProperties = new Properties();
            allProperties.load(fis);
            fis.close();
            
            String currentHash = allProperties.getProperty(CONF_KEY_A);
            String newHash = passwordProperties.getProperty(CONF_KEY_A);
            
            if (currentHash == null || !currentHash.equals(newHash)) {
                allProperties.setProperty(CONF_KEY_A, newHash);
                
                FileOutputStream fos = new FileOutputStream(configFile);
                allProperties.store(fos, "Random Name Picker Configuration");//new generated,potentially buggy
                fos.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void initializeDefaultPassword() {
        try {
            String defaultPassword = "#include<bits/stdc++.h>usingnamespacestd;intmain(){return0;}";
            String hashedPassword = EncryptionUtil.hashPassword(defaultPassword);
            ConfigManager.properties.setProperty("x7f9a2b1c4e8d3f6", hashedPassword);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public static boolean verifyPassword(String inputPassword) {
        try {
            sanitizeInput(inputPassword);

            String storedHash = passwordProperties.getProperty(CONF_KEY_A);
            
            if (storedHash == null || storedHash.isEmpty()) {//new generated,potentially buggy
                initializeDefaultPassword();
                loadPasswordConfig();
                storedHash = passwordProperties.getProperty(CONF_KEY_A);
            }
            
            if (storedHash == null) {
                return false;
            }
            
            String inputHash = EncryptionUtil.hashPassword(inputPassword);

            boolean verified = storedHash.equals(inputHash);
            if (verified) {
                isUnlocked = true;
            }
            return verified;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean changePassword(String oldPassword, String newPassword) {
        try {
            sanitizeInput(oldPassword);
            sanitizeInput(newPassword);

            if (!verifyPassword(oldPassword)) {
                return false;
            }

            if (newPassword.length() < 4) {
                return false;
            }

            String newHashedPassword = EncryptionUtil.hashPassword(newPassword);
            passwordProperties.setProperty(CONF_KEY_A, newHashedPassword);

            savePasswordConfig();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean changePasswordDirectly(String newPassword) {
        try {
            sanitizeInput(newPassword);

            if (newPassword.length() < 4) {
                return false;
            }

            String newHashedPassword = EncryptionUtil.hashPassword(newPassword);
            passwordProperties.setProperty(CONF_KEY_A, newHashedPassword);

            savePasswordConfig();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void sanitizeInput(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        if (input.length() > 100) {
            throw new IllegalArgumentException("密码长度不能超过100字符");
        }
    }

    public static boolean isLocked() {
        return !isUnlocked;
    }

    public static void lock() {
        isUnlocked = false;
    }

    public static void unlock() {
        isUnlocked = true;
    }

    public static boolean handleCtrlL() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCtrlLTime > 2000) {
            ctrlLCount = 0;
        }

        lastCtrlLTime = currentTime;
        ctrlLCount++;

        if (ctrlLCount >= 10) {
            isUnlocked = true;
            ctrlLCount = 0;
            return true;
        }
        return false;
    }

    public static void resetCtrlLCount() {
        ctrlLCount = 0;
    }
}

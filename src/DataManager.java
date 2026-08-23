import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class DataManager {
    private static final String DATA_DIR = "data";
    private static final String SCHEMES_DIR = DATA_DIR + "/schemes";
    private static final String BACKUP_DIR = DATA_DIR + "/backup";
    private static final String LOG_BACKUP_DIR = "log";
    
    public DataManager() {
        createDirectories();
    }
    
    private void createDirectories() {
        new File(DATA_DIR).mkdirs();
        new File(SCHEMES_DIR).mkdirs();
        new File(BACKUP_DIR).mkdirs();
        new File(LOG_BACKUP_DIR).mkdirs();
    }
    
    public void saveNamesFile(String schemeName, List<String> names) throws Exception {
        String cleanSchemeName = schemeName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = SCHEMES_DIR + "/" + cleanSchemeName + "_names.txt";
        
        StringBuilder content = new StringBuilder();
        for (String name : names) {
            content.append(name).append("\n");
        }
        
        String plainText = content.toString();
        String encrypted = EncryptionUtil.encryptData(plainText, getSalt(schemeName));
        String hash = EncryptionUtil.calculateHash(plainText);
        
        writeEncryptedFile(fileName, encrypted, hash);
        
        backupFile(schemeName, fileName, encrypted, hash);
        
        LogManager.log("保存方案[" + schemeName + "]名单数据", "SAVE_WITH_ENCRYPTION");
    }
    
    public List<String> loadNamesFile(String schemeName) throws Exception {
        String cleanSchemeName = schemeName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = SCHEMES_DIR + "/" + cleanSchemeName + "_names.txt";
        
        FileData fileData = readEncryptedFile(fileName, schemeName);
        
        if (fileData == null) {
            File file = new File(fileName);
            if (file.exists() && file.length() > 0) {
                LogManager.log("检测到方案[" + schemeName + "]数据损坏，尝试恢复", "DATA_CORRUPTED_DETECTED");
            }
            
            fileData = recoverAndSyncData(schemeName, fileName);
            
            if (fileData == null) {
                LogManager.log("方案[" + schemeName + "]所有数据均不可用", "ALL_DATA_UNAVAILABLE");
                return null;
            }
            
            String decrypted = EncryptionUtil.decryptData(fileData.encryptedContent, getSalt(schemeName));
            LogManager.log("方案[" + schemeName + "]数据已恢复并同步", "DATA_RECOVERED_AND_SYNCED");
            
            List<String> names = new java.util.ArrayList<>();
            String[] lines = decrypted.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    names.add(line);
                }
            }
            return names;
        }
        
        try {
            String decrypted = EncryptionUtil.decryptData(fileData.encryptedContent, getSalt(schemeName));
            
            String decryptedHash = EncryptionUtil.calculateHash(decrypted);
            if (!decryptedHash.equals(fileData.hash)) {
                LogManager.log("检测到方案[" + schemeName + "]数据异常，尝试恢复", "DATA_CORRUPTED");
                fileData = recoverAndSyncData(schemeName, fileName);
                
                if (fileData == null) {
                    LogManager.log("方案[" + schemeName + "]数据恢复失败", "RECOVERY_FAILED");
                    throw new RuntimeException("DATA_CORRUPTED");
                }
                
                decrypted = EncryptionUtil.decryptData(fileData.encryptedContent, getSalt(schemeName));
                LogManager.log("方案[" + schemeName + "]数据已成功恢复并同步", "DATA_RECOVERED_AND_SYNCED");
            } else {
                syncAllBackups(schemeName, fileName, fileData.encryptedContent, fileData.hash);
            }
            
            List<String> names = new java.util.ArrayList<>();
            String[] lines = decrypted.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    names.add(line);
                }
            }
            
            return names;
        } catch (RuntimeException e) {
            if ("DATA_CORRUPTED".equals(e.getMessage())) {
                throw e;
            }
            LogManager.log("解密方案[" + schemeName + "]数据失败: " + e.getMessage(), "DECRYPT_ERROR");
            fileData = recoverAndSyncData(schemeName, fileName);
            
            if (fileData == null) {
                File file = new File(fileName);
                if (file.exists() && file.length() > 0) {
                    throw new RuntimeException("DATA_CORRUPTED");
                }
                throw new RuntimeException("数据损坏且无法恢复: " + schemeName);
            }
            
            String decrypted = EncryptionUtil.decryptData(fileData.encryptedContent, getSalt(schemeName));
            
            List<String> names = new java.util.ArrayList<>();
            String[] lines = decrypted.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    names.add(line);
                }
            }
            
            return names;
        } catch (Exception e) {
            LogManager.log("解密方案[" + schemeName + "]数据失败: " + e.getMessage(), "DECRYPT_ERROR");
            fileData = recoverAndSyncData(schemeName, fileName);
            
            if (fileData == null) {
                File file = new File(fileName);
                if (file.exists() && file.length() > 0) {
                    throw new RuntimeException("DATA_CORRUPTED");
                }
                throw new RuntimeException("数据损坏且无法恢复: " + schemeName);
            }
            
            String decrypted = EncryptionUtil.decryptData(fileData.encryptedContent, getSalt(schemeName));
            
            List<String> names = new java.util.ArrayList<>();
            String[] lines = decrypted.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    names.add(line);
                }
            }
            
            return names;
        }
    }
    
    public void saveNumberRange(String schemeName, String content) throws Exception {
        String cleanSchemeName = schemeName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = SCHEMES_DIR + "/" + cleanSchemeName + "_number.txt";
        
        String encrypted = EncryptionUtil.encryptData(content, getSalt(schemeName));
        String hash = EncryptionUtil.calculateHash(content);
        
        writeEncryptedFile(fileName, encrypted, hash);
        backupFile(schemeName, fileName, encrypted, hash);
        
        LogManager.log("保存方案[" + schemeName + "]数字范围", "SAVE_NUMBER_RANGE");
    }
    
    public String loadNumberRange(String schemeName) throws Exception {
        String cleanSchemeName = schemeName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = SCHEMES_DIR + "/" + cleanSchemeName + "_number.txt";
        
        FileData fileData = readEncryptedFile(fileName, schemeName);
        if (fileData == null) {
            return null;
        }
        
        try {
            String decrypted = EncryptionUtil.decryptData(fileData.encryptedContent, getSalt(schemeName));
            String decryptedHash = EncryptionUtil.calculateHash(decrypted);
            
            if (!decryptedHash.equals(fileData.hash)) {
                LogManager.log("检测到方案[" + schemeName + "]数字范围异常", "DATA_CORRUPTED");
                fileData = recoverAndSyncData(schemeName, fileName);
                if (fileData == null) {
                    return null;
                }
            }
            
            return decrypted;
        } catch (Exception e) {
            LogManager.log("解密方案[" + schemeName + "]数字范围失败: " + e.getMessage(), "DECRYPT_NUMBER_ERROR");
            fileData = recoverAndSyncData(schemeName, fileName);
            if (fileData == null) {
                return null;
            }
            return EncryptionUtil.decryptData(fileData.encryptedContent, getSalt(schemeName));
        }
    }
    
    public void saveSeatConfig(String schemeName, String content) throws Exception {
        String cleanSchemeName = schemeName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = SCHEMES_DIR + "/" + cleanSchemeName + "_seat.txt";
        
        String encrypted = EncryptionUtil.encryptData(content, getSalt(schemeName));
        String hash = EncryptionUtil.calculateHash(content);
        
        writeEncryptedFile(fileName, encrypted, hash);
        backupFile(schemeName, fileName, encrypted, hash);
        
        LogManager.log("保存方案[" + schemeName + "]座位配置", "SAVE_SEAT_CONFIG");
    }
    
    public String loadSeatConfig(String schemeName) throws Exception {
        String cleanSchemeName = schemeName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = SCHEMES_DIR + "/" + cleanSchemeName + "_seat.txt";
        
        FileData fileData = readEncryptedFile(fileName, schemeName);
        if (fileData == null) {
            return null;
        }
        
        try {
            String decrypted = EncryptionUtil.decryptData(fileData.encryptedContent, getSalt(schemeName));
            String decryptedHash = EncryptionUtil.calculateHash(decrypted);
            
            if (!decryptedHash.equals(fileData.hash)) {
                LogManager.log("检测到方案[" + schemeName + "]座位配置异常", "DATA_CORRUPTED");
                fileData = recoverAndSyncData(schemeName, fileName);
                if (fileData == null) {
                    return null;
                }
            }
            
            return decrypted;
        } catch (Exception e) {
            LogManager.log("解密方案[" + schemeName + "]座位配置失败: " + e.getMessage(), "DECRYPT_SEAT_ERROR");
            fileData = recoverAndSyncData(schemeName, fileName);
            if (fileData == null) {
                return null;
            }
            return EncryptionUtil.decryptData(fileData.encryptedContent, getSalt(schemeName));
        }
    }
    
    private void writeEncryptedFile(String filePath, String encrypted, String hash) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write("#ENCRYPTED_DATA\n".getBytes(StandardCharsets.UTF_8));
            fos.write(encrypted.getBytes(StandardCharsets.UTF_8));
            fos.write("\n#HASH\n".getBytes(StandardCharsets.UTF_8));
            fos.write(hash.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    private FileData readEncryptedFile(String filePath, String schemeName) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }
        
        StringBuilder encryptedBuilder = new StringBuilder();
        StringBuilder hashBuilder = new StringBuilder();
        boolean readingHash = false;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    if (!"#ENCRYPTED_DATA".equals(line.trim())) {
                        LogManager.log("方案[" + schemeName + "]文件格式错误", "INVALID_FORMAT");
                        return null;
                    }
                    firstLine = false;
                    continue;
                }
                
                if ("#HASH".equals(line.trim())) {
                    readingHash = true;
                    continue;
                }
                
                if (readingHash) {
                    hashBuilder.append(line);
                } else {
                    encryptedBuilder.append(line);
                }
            }
        }
        
        if (encryptedBuilder.length() == 0 || hashBuilder.length() == 0) {
            return null;
        }
        
        return new FileData(encryptedBuilder.toString(), hashBuilder.toString());
    }
    
    private boolean verifyIntegrity(FileData fileData, String salt) throws Exception {
        try {
            String decrypted = EncryptionUtil.decryptData(fileData.encryptedContent, salt);
            String calculatedHash = EncryptionUtil.calculateHash(decrypted);
            return calculatedHash.equals(fileData.hash);
        } catch (Exception e) {
            return false;
        }
    }
    
    private void backupFile(String schemeName, String sourcePath, String encrypted, String hash) throws IOException {
        String cleanSchemeName = schemeName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = new File(sourcePath).getName();
        
        String backupPath = BACKUP_DIR + "/" + cleanSchemeName + "_" + fileName;
        try (FileOutputStream fos = new FileOutputStream(backupPath)) {
            fos.write("#ENCRYPTED_DATA\n".getBytes(StandardCharsets.UTF_8));
            fos.write(encrypted.getBytes(StandardCharsets.UTF_8));
            fos.write("\n#HASH\n".getBytes(StandardCharsets.UTF_8));
            fos.write(hash.getBytes(StandardCharsets.UTF_8));
        }
        
        String disguisedFileName = disguiseFileName(cleanSchemeName, fileName);
        String logBackupPath = LOG_BACKUP_DIR + "/" + disguisedFileName;
        try (FileOutputStream fos = new FileOutputStream(logBackupPath)) {
            fos.write("#ENCRYPTED_DATA\n".getBytes(StandardCharsets.UTF_8));
            fos.write(encrypted.getBytes(StandardCharsets.UTF_8));
            fos.write("\n#HASH\n".getBytes(StandardCharsets.UTF_8));
            fos.write(hash.getBytes(StandardCharsets.UTF_8));
        }
        
        LogManager.log("备份方案[" + schemeName + "]文件: " + fileName, "BACKUP_CREATED");
    }
    
    private FileData recoverAndSyncData(String schemeName, String originalPath) {
        String fileName = new File(originalPath).getName();
        String cleanSchemeName = schemeName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String backupFileName = cleanSchemeName + "_" + fileName;
        
        String backupPath = BACKUP_DIR + "/" + backupFileName;
        String disguisedFileName = disguiseFileName(cleanSchemeName, fileName);
        String logBackupPath = LOG_BACKUP_DIR + "/" + disguisedFileName;
        
        FileData originalData = readFileData(originalPath);
        FileData backupData = readFileData(backupPath);
        FileData logData = readFileData(logBackupPath);
        
        boolean originalValid = isValidData(originalData, schemeName);
        boolean backupValid = isValidData(backupData, schemeName);
        boolean logValid = isValidData(logData, schemeName);
        
        FileData masterData = null;
        String source = "";
        
        if (logValid) {
            masterData = logData;
            source = "log";
        } else if (backupValid) {
            masterData = backupData;
            source = "backup";
        } else if (originalValid) {
            masterData = originalData;
            source = "data";
        }
        
        if (masterData == null) {
            LogManager.log("方案[" + schemeName + "]所有备份均无效", "ALL_INVALID");
            return null;
        }
        
        try {
            boolean needSync = false;
            
            if (!logValid || !compareHash(masterData.hash, logData != null ? logData.hash : null)) {
                needSync = true;
            }
            if (!backupValid || !compareHash(masterData.hash, backupData != null ? backupData.hash : null)) {
                needSync = true;
            }
            if (!originalValid || !compareHash(masterData.hash, originalData != null ? originalData.hash : null)) {
                needSync = true;
            }
            
            if (needSync) {
                if ("log".equals(source)) {
                    copyFile(logBackupPath, backupPath);
                    copyFile(logBackupPath, originalPath);
                } else if ("backup".equals(source)) {
                    copyFile(backupPath, logBackupPath);
                    copyFile(backupPath, originalPath);
                } else if ("data".equals(source)) {
                    copyFile(originalPath, backupPath);
                    copyFile(originalPath, logBackupPath);
                }
                LogManager.log("方案[" + schemeName + "]已从" + source + "同步所有文件", "SYNCED_FROM_" + source.toUpperCase());
            } else {
                LogManager.log("方案[" + schemeName + "]三文件一致，无需同步", "ALREADY_SYNCED");
            }
            
            return masterData;
        } catch (Exception e) {
            LogManager.log("同步失败: " + e.getMessage(), "SYNC_ERROR");
        }
        
        return null;
    }
    
    private boolean isValidData(FileData data, String schemeName) {
        if (data == null || data.encryptedContent.isEmpty()) {
            return false;
        }
        
        try {
            String decrypted = EncryptionUtil.decryptData(data.encryptedContent, getSalt(schemeName));
            String calculatedHash = EncryptionUtil.calculateHash(decrypted);
            return calculatedHash.equals(data.hash);
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean compareHash(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) {
            return false;
        }
        return hash1.equals(hash2);
    }
    
    private void syncAllBackups(String schemeName, String originalPath, String encryptedContent, String hash) {
        try {
            String fileName = new File(originalPath).getName();
            String cleanSchemeName = schemeName.replaceAll("[\\\\/:*?\"<>|]", "_");
            String backupFileName = cleanSchemeName + "_" + fileName;
            
            String backupPath = BACKUP_DIR + "/" + backupFileName;
            String disguisedFileName = disguiseFileName(cleanSchemeName, fileName);
            String logBackupPath = LOG_BACKUP_DIR + "/" + disguisedFileName;
            
            FileData backupData = readFileData(backupPath);
            FileData logData = readFileData(logBackupPath);
            
            boolean needSync = false;
            
            if (!compareHash(hash, backupData != null ? backupData.hash : null)) {
                needSync = true;
            }
            if (!compareHash(hash, logData != null ? logData.hash : null)) {
                needSync = true;
            }
            
            if (needSync) {
                writeEncryptedFile(backupPath, encryptedContent, hash);
                writeEncryptedFile(logBackupPath, encryptedContent, hash);
                LogManager.log("方案[" + schemeName + "]已同步所有备份", "BACKUPS_SYNCED");
            }
        } catch (Exception e) {
            LogManager.log("同步备份失败: " + e.getMessage(), "SYNC_BACKUP_ERROR");
        }
    }
    
    private FileData readFileData(String path) {
        try {
            return readEncryptedFile(path, "recovery");
        } catch (Exception e) {
            return null;
        }
    }
    
    private void copyFile(String source, String dest) throws IOException {
        Files.copy(new File(source).toPath(), new File(dest).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    
    private String getSalt(String schemeName) {
        return "R@nd0mN@m3P!ck3r_" + schemeName + "_2026";
    }
    
    private String disguiseFileName(String schemeName, String originalFileName) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String hash = String.valueOf(schemeName.hashCode() & 0x7FFFFFFF);
        
        if (originalFileName.contains("names")) {
            return "sys_cache_" + hash + ".dat";
        } else if (originalFileName.contains("number")) {
            return "usr_config_" + hash + ".tmp";
        } else if (originalFileName.contains("seat")) {
            return "app_data_" + hash + ".log";
        }
        
        return "temp_" + timestamp + ".bak";
    }
    
    private static class FileData {
        String encryptedContent;
        String hash;
        
        FileData(String encryptedContent, String hash) {
            this.encryptedContent = encryptedContent;
            this.hash = hash;
        }
    }
}

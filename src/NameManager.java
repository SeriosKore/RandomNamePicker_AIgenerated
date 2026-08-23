import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

public class NameManager {
    private static final String DATA_DIR = "data";
    private static final String SCHEMES_DIR = DATA_DIR + "/schemes";
    private DataManager dataManager;
    private boolean isDataCorrupted = false;

    public NameManager() {
        createDataDirectories();
        dataManager = new DataManager();
    }

    private void createDataDirectories() {
        File dataDir = new File(DATA_DIR);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        File schemesDir = new File(SCHEMES_DIR);
        if (!schemesDir.exists()) {
            schemesDir.mkdirs();
        }
    }

    public List<String> getNameList() {
        return loadNamesForScheme("default");
    }

    public List<String> loadNamesForScheme(String schemeName) {
        isDataCorrupted = false;
        try {
            List<String> names = dataManager.loadNamesFile(schemeName);
            return names != null ? names : new ArrayList<>();
        } catch (RuntimeException e) {
            if ("DATA_CORRUPTED".equals(e.getMessage())) {
                isDataCorrupted = true;
                LogManager.log("方案[" + schemeName + "]名单文件损坏", "DATA_CORRUPTED_DETECTED");
                
                deleteCorruptedFiles(schemeName);
                
                JOptionPane.showMessageDialog(null,
                    "名单已损坏，请重新导入",
                    "数据损坏警告",
                    JOptionPane.ERROR_MESSAGE);
                return new ArrayList<>();
            }
            e.printStackTrace();
            LogManager.log("加载方案[" + schemeName + "]名单失败: " + e.getMessage(), "LOAD_ERROR");
            return new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            LogManager.log("加载方案[" + schemeName + "]名单失败: " + e.getMessage(), "LOAD_ERROR");
            return new ArrayList<>();
        }
    }

    private void deleteCorruptedFiles(String schemeName) {
        String cleanSchemeName = schemeName.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        String dataFile = SCHEMES_DIR + "/" + cleanSchemeName + "_names.txt";
        String backupFile = DATA_DIR + "/backup/" + cleanSchemeName + "_" + cleanSchemeName + "_names.txt";
        String logBackupFile = "log/" + cleanSchemeName + "_" + cleanSchemeName + "_names.txt";
        
        deleteFileIfExists(dataFile);
        deleteFileIfExists(backupFile);
        deleteFileIfExists(logBackupFile);
        
        LogManager.log("方案[" + schemeName + "]损坏文件已删除", "CORRUPTED_FILES_DELETED");
    }

    private void deleteFileIfExists(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            if (file.delete()) {
                LogManager.log("已删除文件: " + filePath, "FILE_DELETED");
            } else {
                LogManager.log("删除文件失败: " + filePath, "FILE_DELETE_FAILED");
            }
        }
    }

    public boolean isDataCorrupted() {
        return isDataCorrupted;
    }

    /**
     * 保存名单（成功返回 true）。保存成功后对比修改前后内容，自动归类为
     * 新增/删除/修改 并写入名单修改日志。
     */
    public boolean saveNamesForScheme(String schemeName, List<String> names) {
        List<String> before = loadNamesForScheme(schemeName);
        try {
            dataManager.saveNamesFile(schemeName, names);
            LogManager.log("方案[" + schemeName + "]名单已保存，共" + names.size() + "个", "SAVE_SUCCESS");

            // 名单修改日志：按出现次数对比修改前后内容（正确处理重复条目）
            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            diffLists(names, before, added, removed);
            if (!added.isEmpty() || !removed.isEmpty()) {
                ModificationLogManager.logChange(schemeName, added, removed, before, names);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            LogManager.log("保存方案[" + schemeName + "]名单失败: " + e.getMessage(), "SAVE_ERROR");
            return false;
        }
    }

    /**
     * 按出现次数计算 after 相对 before 的新增与删除条目（支持重复条目）。
     */
    private static void diffLists(List<String> after, List<String> before,
                                  List<String> added, List<String> removed) {
        java.util.Map<String, Integer> afterCounts = countOccurrences(after);
        java.util.Map<String, Integer> beforeCounts = countOccurrences(before);

        for (java.util.Map.Entry<String, Integer> entry : afterCounts.entrySet()) {
            int beforeCount = beforeCounts.getOrDefault(entry.getKey(), 0);
            for (int i = 0; i < entry.getValue() - beforeCount; i++) {
                added.add(entry.getKey());
            }
        }
        for (java.util.Map.Entry<String, Integer> entry : beforeCounts.entrySet()) {
            int afterCount = afterCounts.getOrDefault(entry.getKey(), 0);
            for (int i = 0; i < entry.getValue() - afterCount; i++) {
                removed.add(entry.getKey());
            }
        }
    }

    private static java.util.Map<String, Integer> countOccurrences(List<String> items) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (String item : items) {
            counts.put(item, counts.getOrDefault(item, 0) + 1);
        }
        return counts;
    }

    public void addNameToScheme(String schemeName, String name) {
        List<String> names = loadNamesForScheme(schemeName);
        if (!names.contains(name)) {
            names.add(name);
            saveNamesForScheme(schemeName, names);
        }
    }

    public void removeNameFromScheme(String schemeName, String name) {
        List<String> names = loadNamesForScheme(schemeName);
        if (names.remove(name)) {
            saveNamesForScheme(schemeName, names);
        }
    }

    public void clearNamesForScheme(String schemeName) {
        saveNamesForScheme(schemeName, new ArrayList<>());
    }

    public void importFromFile(String schemeName, File file) throws IOException {
        List<String> importedNames = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    importedNames.add(line);
                }
            }
        }

        List<String> existingNames = loadNamesForScheme(schemeName);
        existingNames.addAll(importedNames);
        // 仅在保存成功后才记录导入日志，避免日志与磁盘数据不一致
        if (saveNamesForScheme(schemeName, existingNames)) {
            ModificationLogManager.logOperation(schemeName, "导入",
                    "文件=" + file.getName() + " 导入条数=" + importedNames.size());
        }
    }

    public void exportToFile(String schemeName, File file) throws IOException {
        List<String> names = loadNamesForScheme(schemeName);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            BufferedWriter bufferedWriter = new BufferedWriter(writer);
            for (String name : names) {
                bufferedWriter.write(name);
                bufferedWriter.newLine();
            }
            bufferedWriter.flush();
        }
    }
}

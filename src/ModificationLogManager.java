import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 名单修改日志管理器
 * 当名单发生“新增 / 删除 / 修改”操作时，以追加方式记录到程序运行目录下的
 * modification_log.txt（文件不存在时自动创建，绝不覆盖历史记录）。
 */
public class ModificationLogManager {
    public static final String LOG_FILE_PATH = "modification_log.txt";

    /**
     * 记录一次名单变更。基于修改前后内容对比自动归类：
     * 仅当新增与删除条目数量一致时按“修改”成对记录（修改前/修改后），
     * 否则全部按“新增/删除”独立记录（避免把无关的增删误判为修改）。
     */
    public static void logChange(String schemeName, List<String> added, List<String> removed,
                                 List<String> before, List<String> after) {
        // 汇总行：修改前后内容对比
        writeLine(schemeName, "保存名单", "修改前=[" + join(before) + "] 修改后=[" + join(after) + "]");

        // 条目级记录：数量一致时按“修改”成对记录，否则按新增/删除记录
        int pairCount = (added.size() == removed.size()) ? added.size() : 0;
        for (int i = 0; i < pairCount; i++) {
            writeLine(schemeName, "修改", "修改前=" + removed.get(i) + " 修改后=" + added.get(i));
        }
        for (int i = pairCount; i < added.size(); i++) {
            writeLine(schemeName, "新增", "条目=" + added.get(i));
        }
        for (int i = pairCount; i < removed.size(); i++) {
            writeLine(schemeName, "删除", "条目=" + removed.get(i));
        }
    }

    /**
     * 记录一次名单相关操作（如导入等）。
     */
    public static void logOperation(String schemeName, String operationType, String detail) {
        writeLine(schemeName, operationType, detail);
    }

    private static void writeLine(String schemeName, String operationType, String detail) {
        try {
            File logFile = new File(LOG_FILE_PATH);
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(logFile, true), "UTF-8");
            BufferedWriter bw = new BufferedWriter(osw);

            // 每次调用新建实例，避免共享 SimpleDateFormat 的线程安全隐患
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            bw.write(String.format("[%s] 方案=%s 类型=%s %s", timestamp, schemeName, operationType, detail));
            bw.newLine();
            bw.flush();
            bw.close();
            osw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取全部日志内容。
     */
    public static String readLog() {
        StringBuilder content = new StringBuilder();
        File logFile = new File(LOG_FILE_PATH);

        if (!logFile.exists()) {
            return "暂无名单修改日志";
        }

        try {
            InputStreamReader isr = new InputStreamReader(new FileInputStream(logFile), "UTF-8");
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
            br.close();
            isr.close();
        } catch (IOException e) {
            e.printStackTrace();
            return "读取名单修改日志失败：" + e.getMessage();
        }

        return content.toString();
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}

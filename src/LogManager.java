//To recording Motify NOT the programme running log
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogManager {
    public static final String LOG_DIR = "log";
    public static final String LOG_FILE_PATH = LOG_DIR + "/Modifylog.txt";
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void log(String data, String operation) {
        try {
            File logFile = new File(LOG_FILE_PATH);
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(logFile, true), "UTF-8");
            BufferedWriter bw = new BufferedWriter(osw);

            String timestamp = dateFormat.format(new Date());
            bw.write(String.format("[%s] 【%s】【%s】", timestamp, data, operation));
            bw.newLine();
            bw.flush();
            bw.close();
            osw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String readLog() {
        StringBuilder content = new StringBuilder();
        File logFile = new File(LOG_FILE_PATH);

        if (!logFile.exists()) {
            return "暂无日志记录";
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
            return "读取日志失败：" + e.getMessage();
        }

        return content.toString();
    }

    public static void clearLog() {
        File logFile = new File(LOG_FILE_PATH);
        if (logFile.exists()) {
            logFile.delete();
        }
    }
}


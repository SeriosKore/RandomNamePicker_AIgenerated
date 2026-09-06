import com.randomnamepicker.core.LogManager;
import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * 计时子球外观配置（正计时子球开发方案 §3.3）。
 * <p>
 * 默认值 = 现行代码常量；持久化于插件自持文件 {@code data/timerball_plugin.properties}
 * （不触碰宿主 data/config.properties）。合并式读写、坏值回退默认。
 * 仅供 EDT 使用（设置对话框与 FloatingBallTracker 同线程），无并发要求。
 * </p>
 */
public class TimerBallConfig {

    public static final String FILE_PATH = "data" + File.separator + "timerball_plugin.properties";

    public static final String KEY_SIZE_MODE = "sizeMode";
    public static final String KEY_FIXED_DIAMETER = "fixedDiameter";
    public static final String KEY_COLOR_READY = "colorReady";
    public static final String KEY_COLOR_RUNNING = "colorRunning";
    public static final String KEY_COLOR_PAUSED = "colorPaused";
    public static final String KEY_GAP_PX = "gapPx";

    /** 尺寸模式：AUTO=随悬浮球联动（×0.55 夹 36–60）；FIXED=固定直径。 */
    public enum SizeMode { AUTO, FIXED }

    public static final SizeMode DEFAULT_SIZE_MODE = SizeMode.AUTO;
    public static final int DEFAULT_FIXED_DIAMETER = 44;
    public static final int FIXED_DIAMETER_MIN = 30;
    public static final int FIXED_DIAMETER_MAX = 90;
    public static final int DEFAULT_GAP_PX = 6;
    public static final int GAP_PX_MIN = 4;
    public static final int GAP_PX_MAX = 20;
    public static final Color DEFAULT_COLOR_READY = new Color(0x70, 0x86, 0xA6);    // 灰蓝
    public static final Color DEFAULT_COLOR_RUNNING = new Color(0x2E, 0x8B, 0x57);  // 绿
    public static final Color DEFAULT_COLOR_PAUSED = new Color(0xE0, 0x8A, 0x2E);   // 橙

    private SizeMode sizeMode = DEFAULT_SIZE_MODE;
    private int fixedDiameter = DEFAULT_FIXED_DIAMETER;
    private int gapPx = DEFAULT_GAP_PX;
    private Color colorReady = DEFAULT_COLOR_READY;
    private Color colorRunning = DEFAULT_COLOR_RUNNING;
    private Color colorPaused = DEFAULT_COLOR_PAUSED;

    // ===================== 访问器 =====================

    public SizeMode getSizeMode() {
        return sizeMode;
    }

    public boolean isFixed() {
        return sizeMode == SizeMode.FIXED;
    }

    public void setSizeMode(SizeMode mode) {
        if (mode != null) {
            sizeMode = mode;
        }
    }

    public int getFixedDiameter() {
        return fixedDiameter;
    }

    public void setFixedDiameter(int d) {
        fixedDiameter = clamp(d, FIXED_DIAMETER_MIN, FIXED_DIAMETER_MAX);
    }

    public int getGapPx() {
        return gapPx;
    }

    public void setGapPx(int g) {
        gapPx = clamp(g, GAP_PX_MIN, GAP_PX_MAX);
    }

    public Color getColorReady() {
        return colorReady;
    }

    public void setColorReady(Color c) {
        if (c != null) {
            colorReady = c;
        }
    }

    public Color getColorRunning() {
        return colorRunning;
    }

    public void setColorRunning(Color c) {
        if (c != null) {
            colorRunning = c;
        }
    }

    public Color getColorPaused() {
        return colorPaused;
    }

    public void setColorPaused(Color c) {
        if (c != null) {
            colorPaused = c;
        }
    }

    /** 恢复全部默认（内存态；持久化由调用方决定）。 */
    public void resetToDefaults() {
        sizeMode = DEFAULT_SIZE_MODE;
        fixedDiameter = DEFAULT_FIXED_DIAMETER;
        gapPx = DEFAULT_GAP_PX;
        colorReady = DEFAULT_COLOR_READY;
        colorRunning = DEFAULT_COLOR_RUNNING;
        colorPaused = DEFAULT_COLOR_PAUSED;
    }

    // ===================== 持久化 =====================

    /** 从文件加载（缺失/坏值回退默认；仅覆盖能解析的键）。 */
    public void load() {
        File f = new File(FILE_PATH);
        if (!f.exists()) {
            return;
        }
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            LogManager.log("正计时子球-配置读取失败: " + e, "PLUGIN_LOAD_ERROR");
            return;
        }
        String sm = trim(p.getProperty(KEY_SIZE_MODE, ""));
        if ("FIXED".equalsIgnoreCase(sm)) {
            sizeMode = SizeMode.FIXED;
        } else if ("AUTO".equalsIgnoreCase(sm)) {
            sizeMode = SizeMode.AUTO;
        }
        fixedDiameter = readClamped(p, KEY_FIXED_DIAMETER,
                DEFAULT_FIXED_DIAMETER, FIXED_DIAMETER_MIN, FIXED_DIAMETER_MAX);
        gapPx = readClamped(p, KEY_GAP_PX, DEFAULT_GAP_PX, GAP_PX_MIN, GAP_PX_MAX);
        colorReady = readColor(p.getProperty(KEY_COLOR_READY, ""), DEFAULT_COLOR_READY);
        colorRunning = readColor(p.getProperty(KEY_COLOR_RUNNING, ""), DEFAULT_COLOR_RUNNING);
        colorPaused = readColor(p.getProperty(KEY_COLOR_PAUSED, ""), DEFAULT_COLOR_PAUSED);
    }

    /** 整存到插件自持配置文件（失败仅记日志，不影响运行）。 */
    public void save() {
        try {
            File f = new File(FILE_PATH);
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            Properties p = new Properties();
            p.setProperty(KEY_SIZE_MODE, sizeMode.name());
            p.setProperty(KEY_FIXED_DIAMETER, String.valueOf(fixedDiameter));
            p.setProperty(KEY_GAP_PX, String.valueOf(gapPx));
            p.setProperty(KEY_COLOR_READY, toHex(colorReady));
            p.setProperty(KEY_COLOR_RUNNING, toHex(colorRunning));
            p.setProperty(KEY_COLOR_PAUSED, toHex(colorPaused));
            try (FileOutputStream out = new FileOutputStream(f)) {
                p.store(out, "CountUpTimerBall plugin config");
            }
        } catch (IOException e) {
            LogManager.log("正计时子球-配置保存失败: " + e, "PLUGIN_LOAD_ERROR");
        }
    }

    // ===================== 工具 =====================

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static int readClamped(Properties p, String key, int def, int min, int max) {
        String v = trim(p.getProperty(key, ""));
        if (v.isEmpty()) {
            return def;
        }
        try {
            return clamp(Integer.parseInt(v), min, max);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Color readColor(String v, Color def) {
        Color c = parseHexColor(v);
        return c == null ? def : c;
    }

    /** 十六进制解析（"7086A6" 或 "#7086A6"），失败返回 null。 */
    public static Color parseHexColor(String v) {
        if (v == null) {
            return null;
        }
        String s = v.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            return null;
        }
        try {
            return new Color(Integer.parseInt(s, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String toHex(Color c) {
        return String.format("%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

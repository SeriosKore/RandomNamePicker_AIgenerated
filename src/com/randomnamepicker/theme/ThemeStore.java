package com.randomnamepicker.theme;

import com.randomnamepicker.core.ConfigManager;
import com.randomnamepicker.core.LogManager;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 主题存储（宿主内建主题，报告 2）：配置存于宿主主配置（键前缀 theme.*，经 ConfigManager 合并写回），
 * 图片复制入库 {@code data/themes/images/}（不引用原路径）。仅 EDT 使用。
 * <p>
 * 全局背景 + 按窗口类别单独指定（未指定继承全局）。类别 ID 用 ASCII，显示名供设置 UI 使用。
 * </p>
 */
public final class ThemeStore {

    public static final String DATA_ROOT = "data";
    public static final String THEME_DIR = DATA_ROOT + "/themes";
    public static final String IMAGES_DIR = THEME_DIR + "/images";

    public static final String CATEGORY_MAIN = "main";
    public static final String CATEGORY_CONFIG = "config";
    public static final String CATEGORY_SCHEME = "scheme";
    public static final String CATEGORY_NUMBER = "number";
    public static final String CATEGORY_SEAT = "seat";
    public static final String CATEGORY_SETTINGS = "settings";
    public static final String CATEGORY_OTHER = "other";

    /** 类别顺序（设置 UI 展示序）。 */
    public static final String[] CATEGORY_IDS = {
            CATEGORY_MAIN, CATEGORY_CONFIG, CATEGORY_SCHEME,
            CATEGORY_NUMBER, CATEGORY_SEAT, CATEGORY_SETTINGS, CATEGORY_OTHER
    };

    public static final int MASK_DEFAULT = 40;
    public static final int MASK_MIN = 0;
    public static final int MASK_MAX = 180;
    public static final int TABLE_TIER_DEFAULT = 1; // T2：0=原生 1=浅 2=中（默认浅，人工核验后定档）

    private static final String[] ALLOWED_EXT = {"jpg", "jpeg", "png", "bmp", "gif", "webp"};

    private ThemeStore() {
    }

    // ===================== 总开关 / 全局参数 =====================

    public static boolean isEnabled() {
        return Boolean.parseBoolean(ConfigManager.getThemeProperty("enabled", "false"));
    }

    public static void setEnabled(boolean on) {
        ConfigManager.setThemeProperty("enabled", String.valueOf(on));
    }

    public static int getMask() {
        return clampInt(ConfigManager.getThemeProperty("mask", String.valueOf(MASK_DEFAULT)),
                MASK_MIN, MASK_MAX, MASK_DEFAULT);
    }

    public static void setMask(int mask) {
        ConfigManager.setThemeProperty("mask",
                String.valueOf(clampInt(String.valueOf(mask), MASK_MIN, MASK_MAX, MASK_DEFAULT)));
    }

    /** 表格浅透档（T2，报告 3）：0=原生/1=浅/2=中；默认浅（人工核验反馈后定档）。 */
    public static int getTableTier() {
        return clampInt(ConfigManager.getThemeProperty("tableTier", "1"), 0, 2, TABLE_TIER_DEFAULT);
    }

    public static void setTableTier(int tier) {
        ConfigManager.setThemeProperty("tableTier", String.valueOf(clampInt(String.valueOf(tier), 0, 2, 0)));
    }

    // ===================== 全局 / 类别样式 =====================

    private static String globalKey(String suffix) {
        return "g." + suffix;
    }

    private static String catKey(String category, String suffix) {
        return "cat." + category + "." + suffix;
    }

    public static void setGlobalImage(String imageId) {
        ConfigManager.setThemeProperty(globalKey("image"),
                (imageId == null || imageId.isEmpty()) ? null : imageId);
    }

    public static String getGlobalImage() {
        return ConfigManager.getThemeProperty(globalKey("image"), "");
    }

    public static void setGlobalMode(BackdropMode mode) {
        ConfigManager.setThemeProperty(globalKey("mode"),
                mode == null ? BackdropMode.FILL.name() : mode.name());
    }

    public static void setGlobalCrop(Rectangle2D.Double crop) {
        ConfigManager.setThemeProperty(globalKey("crop"), cropToString(crop));
    }

    public static void setCategoryImage(String category, String imageId) {
        ConfigManager.setThemeProperty(catKey(category, "image"),
                (imageId == null || imageId.isEmpty()) ? null : imageId);
    }

    public static void setCategoryMode(String category, BackdropMode mode) {
        ConfigManager.setThemeProperty(catKey(category, "mode"),
                mode == null ? BackdropMode.FILL.name() : mode.name());
    }

    public static void setCategoryCrop(String category, Rectangle2D.Double crop) {
        ConfigManager.setThemeProperty(catKey(category, "crop"), cropToString(crop));
    }

    /** 解析某类别当前生效样式：类别未指定则继承全局；未启用或无可用图 → inactive。 */
    public static ThemeStyle resolve(String category) {
        if (!isEnabled()) {
            return ThemeStyle.NONE;
        }
        String imageId = categoryImageId(category);
        File imageFile = imageFile(imageId);
        if (imageId == null || imageId.isEmpty() || imageFile == null || !imageFile.isFile()) {
            return ThemeStyle.NONE;
        }
        BackdropMode mode;
        Rectangle2D.Double crop;
        if (category != null) {
            String perMode = ConfigManager.getThemeProperty(catKey(category, "mode"), "").trim();
            String perCrop = ConfigManager.getThemeProperty(catKey(category, "crop"), "").trim();
            mode = perMode.isEmpty() ? globalMode() : BackdropMode.parse(perMode);
            crop = perCrop.isEmpty() ? globalCrop() : parseCrop(perCrop);
        } else {
            mode = globalMode();
            crop = globalCrop();
        }
        return new ThemeStyle(true, imageFile, mode, crop, getMask());
    }

    private static String categoryImageId(String category) {
        if (category != null) {
            String per = ConfigManager.getThemeProperty(catKey(category, "image"), "").trim();
            if (!per.isEmpty()) {
                return per;
            }
        }
        return getGlobalImage().trim();
    }

    private static BackdropMode globalMode() {
        return BackdropMode.parse(ConfigManager.getThemeProperty(globalKey("mode"), ""));
    }

    private static Rectangle2D.Double globalCrop() {
        return parseCrop(ConfigManager.getThemeProperty(globalKey("crop"), ""));
    }

    // ===================== 图库 =====================

    /** 复制导入图片到插件图库，返回图库 id（失败返回 null）。 */
    public static String importImage(File source) {
        if (source == null || !source.isFile()) {
            return null;
        }
        String ext = extensionOf(source.getName());
        if (ext == null) {
            return null;
        }
        ensureDirs();
        String id = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        File dest = new File(IMAGES_DIR, id);
        try {
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return id;
        } catch (IOException e) {
            LogManager.log("主题-图片导入失败: " + source.getAbsolutePath() + "（" + e + "）", "PLUGIN_LOAD_ERROR");
            return null;
        }
    }

    public static boolean deleteImage(String imageId) {
        if (imageId == null || imageId.isEmpty()) {
            return false;
        }
        File f = imageFile(imageId);
        return f != null && f.delete();
    }

    /** 图库全部 id（文件名升序）。 */
    public static List<String> listImages() {
        File dir = new File(IMAGES_DIR);
        if (!dir.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = dir.listFiles(f -> f != null && f.isFile() && extensionOf(f.getName()) != null);
        if (files == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (File f : files) {
            out.add(f.getName());
        }
        Collections.sort(out);
        return out;
    }

    public static File imageFile(String imageId) {
        if (imageId == null || imageId.isEmpty()) {
            return null;
        }
        File f = new File(IMAGES_DIR, imageId);
        return f.isFile() ? f : null;
    }

    public static void ensureDirs() {
        new File(IMAGES_DIR).mkdirs();
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (String allowed : ALLOWED_EXT) {
            if (allowed.equals(ext)) {
                return ext;
            }
        }
        return null;
    }

    // ===================== 裁剪框 =====================

    public static Rectangle2D.Double parseCrop(String s) {
        if (s == null || s.trim().isEmpty()) {
            return (Rectangle2D.Double) ThemeStyle.FULL_CROP.clone();
        }
        try {
            String[] p = s.trim().split(",");
            if (p.length == 4) {
                double x = Double.parseDouble(p[0].trim());
                double y = Double.parseDouble(p[1].trim());
                double w = Double.parseDouble(p[2].trim());
                double h = Double.parseDouble(p[3].trim());
                return new Rectangle2D.Double(
                        Math.max(0, Math.min(1, x)),
                        Math.max(0, Math.min(1, y)),
                        Math.max(0.001, Math.min(1, w)),
                        Math.max(0.001, Math.min(1, h)));
            }
        } catch (NumberFormatException ignore) {
            // 回退整图
        }
        return (Rectangle2D.Double) ThemeStyle.FULL_CROP.clone();
    }

    public static String cropToString(Rectangle2D.Double crop) {
        if (crop == null) {
            crop = ThemeStyle.FULL_CROP;
        }
        return String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f", crop.x, crop.y, crop.width, crop.height);
    }

    // ===================== 类别清空 / 恢复默认 =====================

    /** 设置 UI 读取辅助：类别当前生效模式（未指定=继承全局模式）。 */
    public static BackdropMode categoryModeNow(String category) {
        if (category == null) {
            return globalMode();
        }
        String per = ConfigManager.getThemeProperty(catKey(category, "mode"), "").trim();
        return per.isEmpty() ? globalMode() : BackdropMode.parse(per);
    }

    /** 设置 UI 读取辅助：全局当前模式。 */
    public static BackdropMode globalModeNow() {
        return globalMode();
    }

    /** 设置 UI 读取辅助：类别当前图片 id（未指定=继承全局图片；可能为空串）。 */
    public static String categoryImageIdNow(String category) {
        return categoryImageId(category);
    }

    /** 别名：类别实际生效图片 id（含继承全局）。 */
    public static String categoryResolvedImageId(String category) {
        return categoryImageId(category);
    }

    /** 设置 UI 读取辅助：全局或类别当前裁剪配置串（可能为空串 → 视作整图）。 */
    public static String configCropString(String category) {
        return category == null
                ? ConfigManager.getThemeProperty(globalKey("crop"), "")
                : ConfigManager.getThemeProperty(catKey(category, "crop"), "");
    }

    /** 清空某类别全部指定（图片/模式/裁剪 → 图片回继承全局、模式回默认、裁剪回整图）。 */
    public static void clearCategory(String category) {
        if (category == null) {
            return;
        }
        ConfigManager.setThemeProperty(catKey(category, "image"), null);
        ConfigManager.setThemeProperty(catKey(category, "mode"), null);
        ConfigManager.setThemeProperty(catKey(category, "crop"), null);
    }

    /** 恢复出厂：关闭启用、遮罩/表格档回默认、全局空图（整图/填充）、全部类别清空。 */
    public static void resetToDefaults() {
        setEnabled(false);
        setMask(MASK_DEFAULT);
        setTableTier(TABLE_TIER_DEFAULT);
        setGlobalImage(null);
        setGlobalMode(BackdropMode.FILL);
        ConfigManager.setThemeProperty(globalKey("crop"), null);
        for (String c : CATEGORY_IDS) {
            clearCategory(c);
        }
    }

    private static int clampInt(String v, int min, int max, int def) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(v.trim())));
        } catch (Exception e) {
            return def;
        }
    }
}

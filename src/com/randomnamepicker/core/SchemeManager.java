package com.randomnamepicker.core;

import com.randomnamepicker.model.NumberRange;
import com.randomnamepicker.model.Scheme;
import com.randomnamepicker.model.SeatConfig;
import java.awt.Point;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SchemeManager {
    private static final String SCHEMES_DIR = "data/schemes";
    private static final String SCHEMES_INDEX_FILE = "data/schemes/index.txt";

    /**
     * 内置“默认方案”的名称与类型（G1，P0 修复批次新增）。
     * <p>
     * 它不来自 index.txt（由 NamePickerApp 构造期硬编码追加），因此必须在此集中定义：
     * 方案创建的重名校验、下拉框显示区分、存量同名冲突提示均以此为唯一来源。
     * </p>
     */
    public static final String BUILTIN_DEFAULT_SCHEME_NAME = "默认方案";
    public static final String BUILTIN_DEFAULT_SCHEME_TYPE = "name_list";

    private DataManager dataManager;

    public SchemeManager() {
        createSchemesDirectory();
        dataManager = new DataManager();
    }

    private void createSchemesDirectory() {
        File schemesDir = new File(SCHEMES_DIR);
        if (!schemesDir.exists()) {
            schemesDir.mkdirs();
        }
    }

    public List<Scheme> getAllSchemes() {
        List<Scheme> schemes = new ArrayList<>();
        File indexFile = new File(SCHEMES_INDEX_FILE);

        if (indexFile.exists()) {
            // G1：显式 UTF-8 + 以首个逗号切分 + 跳过空行/无逗号行。
            // 旧实现要求 split(",").length == 2，方案名或类型含逗号的行会被静默丢弃（方案“消失”）。
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(indexFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    int idx = line.indexOf(',');
                    if (idx <= 0) {
                        continue;
                    }
                    String name = line.substring(0, idx).trim();
                    String type = line.substring(idx + 1).trim();
                    if (!name.isEmpty()) {
                        schemes.add(new Scheme(name, type));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return schemes;
    }

    public void addScheme(String schemeName, String type) {
        List<Scheme> schemes = getAllSchemes();
        schemes.add(new Scheme(schemeName, type));
        saveSchemesIndex(schemes);
    }

    public void removeScheme(String schemeName) {
        List<Scheme> schemes = getAllSchemes();
        schemes.removeIf(scheme -> scheme.getName().equals(schemeName));
        saveSchemesIndex(schemes);

        deleteSchemeFiles(schemeName);
    }

    private void saveSchemesIndex(List<Scheme> schemes) {
        // G1：与读取端同为显式 UTF-8（避免默认字符集差异导致方案名乱码）
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(SCHEMES_INDEX_FILE), StandardCharsets.UTF_8))) {
            for (Scheme scheme : schemes) {
                writer.println(scheme.getName() + "," + scheme.getType());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteSchemeFiles(String schemeName) {
        File namesFile = new File(SCHEMES_DIR + "/" + schemeName + "_names.txt");
        if (namesFile.exists()) {
            namesFile.delete();
        }

        File numberFile = new File(SCHEMES_DIR + "/" + schemeName + "_number.txt");
        if (numberFile.exists()) {
            numberFile.delete();
        }

        File seatFile = new File(SCHEMES_DIR + "/" + schemeName + "_seat.txt");
        if (seatFile.exists()) {
            seatFile.delete();
        }
    }

    public NumberRange getNumberRange(String schemeName) {
        try {
            String content = dataManager.loadNumberRange(schemeName);
            if (content != null) {
                String[] parts = content.split(",");
                if (parts.length == 2) {
                    return new NumberRange(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            LogManager.log("加载方案[" + schemeName + "]数字范围失败: " + e.getMessage(), "LOAD_NUMBER_ERROR");
        }
        return null;
    }

    public void saveNumberRange(String schemeName, NumberRange range) {
        try {
            String content = range.getMin() + "," + range.getMax();
            dataManager.saveNumberRange(schemeName, content);
            LogManager.log("方案[" + schemeName + "]数字范围已保存", "SAVE_NUMBER_SUCCESS");
        } catch (Exception e) {
            e.printStackTrace();
            LogManager.log("保存方案[" + schemeName + "]数字范围失败: " + e.getMessage(), "SAVE_NUMBER_ERROR");
        }
    }

    public SeatConfig getSeatConfig(String schemeName) {
        try {
            String content = dataManager.loadSeatConfig(schemeName);
            if (content != null) {
                String[] lines = content.split("\n");
                if (lines.length > 0) {
                    String[] dimensions = lines[0].trim().split(",");
                    if (dimensions.length == 2) {
                        int rows = Integer.parseInt(dimensions[0].trim());
                        int cols = Integer.parseInt(dimensions[1].trim());

                        // T3.5（Q-b 不变式）：越界座位（坐标超出 rows/cols）在“读取层”过滤，
                        // 使候选集恒满足“座位 ⊆ 行列范围”。不改动磁盘文件，仅影响读取结果。
                        List<Point> selectedSeats = new ArrayList<>();
                        int dropped = 0;
                        for (int i = 1; i < lines.length; i++) {
                            String[] coords = lines[i].trim().split(",");
                            if (coords.length == 2) {
                                Point seat = new Point(Integer.parseInt(coords[0].trim()),
                                        Integer.parseInt(coords[1].trim()));
                                if (seat.x < 1 || seat.x > rows || seat.y < 1 || seat.y > cols) {
                                    dropped++;
                                    continue;
                                }
                                selectedSeats.add(seat);
                            }
                        }
                        if (dropped > 0) {
                            LogManager.log("方案[" + schemeName + "]座位配置含 " + dropped
                                    + " 个越界座位已忽略（行列=" + rows + "×" + cols + "）", "SEAT_OUT_OF_RANGE");
                        }

                        return new SeatConfig(rows, cols, selectedSeats);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            LogManager.log("加载方案[" + schemeName + "]座位配置失败: " + e.getMessage(), "LOAD_SEAT_ERROR");
        }
        return null;
    }

    public void saveSeatConfig(String schemeName, SeatConfig config) {
        try {
            StringBuilder content = new StringBuilder();
            content.append(config.getRows()).append(",").append(config.getCols()).append("\n");
            for (Point seat : config.getSelectedSeats()) {
                content.append(seat.x).append(",").append(seat.y).append("\n");
            }
            dataManager.saveSeatConfig(schemeName, content.toString());
            LogManager.log("方案[" + schemeName + "]座位配置已保存", "SAVE_SEAT_SUCCESS");
        } catch (Exception e) {
            e.printStackTrace();
            LogManager.log("保存方案[" + schemeName + "]座位配置失败: " + e.getMessage(), "SAVE_SEAT_ERROR");
        }
    }
}


import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.awt.Point;

public class SchemeManager {
    private static final String SCHEMES_DIR = "data/schemes";
    private static final String SCHEMES_INDEX_FILE = "data/schemes/index.txt";
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
            try (BufferedReader reader = new BufferedReader(new FileReader(indexFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        schemes.add(new Scheme(parts[0], parts[1]));
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
        try (PrintWriter writer = new PrintWriter(new FileWriter(SCHEMES_INDEX_FILE))) {
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
                    String[] dimensions = lines[0].split(",");
                    if (dimensions.length == 2) {
                        int rows = Integer.parseInt(dimensions[0]);
                        int cols = Integer.parseInt(dimensions[1]);

                        List<Point> selectedSeats = new ArrayList<>();
                        for (int i = 1; i < lines.length; i++) {
                            String[] coords = lines[i].split(",");
                            if (coords.length == 2) {
                                selectedSeats.add(new Point(Integer.parseInt(coords[0]), Integer.parseInt(coords[1])));
                            }
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

class Scheme {
    private String name;
    private String type;

    public Scheme(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return name;
    }
}

class NumberRange {
    private int min;
    private int max;

    public NumberRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }
}

class SeatConfig {
    private int rows;
    private int cols;
    private List<Point> selectedSeats;

    public SeatConfig(int rows, int cols, List<Point> selectedSeats) {
        this.rows = rows;
        this.cols = cols;
        this.selectedSeats = selectedSeats;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public List<Point> getSelectedSeats() {
        return selectedSeats;
    }
}

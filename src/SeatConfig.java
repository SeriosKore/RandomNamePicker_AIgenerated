import java.awt.Point;
import java.util.List;

/**
 * 座位配置模型（public：插件 API 的一部分）。
 */
public class SeatConfig {
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

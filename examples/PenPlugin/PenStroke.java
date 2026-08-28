import java.util.ArrayList;
import java.util.List;

/**
 * 一条笔画：记录采样点、时间与各点宽度。
 * 宽度模型 = 基础宽度（压感） × 速度因子（快则细、慢则粗，形成笔锋），
 * 笔画首尾由渲染器施加渐细（起笔/收笔笔锋）。
 */
public class PenStroke {

    private final List<Double> xs = new ArrayList<>();
    private final List<Double> ys = new ArrayList<>();
    private final List<Double> ws = new ArrayList<>();
    private final List<Long> ts = new ArrayList<>();

    /** 是否为橡皮笔画。 */
    public boolean eraser;
    /** 是否已结束（用于收笔渐细）。 */
    public boolean finished;
    /** 本笔画缓存的压感（0~1，无压感为 -1）与基础宽度，避免每个移动事件都查询原生层。 */
    public double pressure = -1;
    public double baseWidth = 3.2;

    private double lastX;
    private double lastY;
    private long lastT;
    private double emaSpeed;    // 指数平滑速度（px/ms）

    public PenStroke(boolean eraser) {
        this.eraser = eraser;
    }

    public int size() {
        return xs.size();
    }

    public double xAt(int i) {
        return xs.get(i);
    }

    public double yAt(int i) {
        return ys.get(i);
    }

    public double wAt(int i) {
        return ws.get(i);
    }

    public long tAt(int i) {
        return ts.get(i);
    }

    public boolean isEraser() {
        return eraser;
    }

    /**
     * 追加一个点。
     *
     * @param pressure  压感 0~1（无压感设备传 -1）
     * @param baseWidth 基础宽度（像素）
     */
    public void addPoint(double x, double y, double pressure, double baseWidth) {
        long now = System.nanoTime();
        if (xs.isEmpty()) {
            xs.add(x);
            ys.add(y);
            ws.add(Math.max(0.8, baseWidth * 0.55));   // 起笔细
            ts.add(now);
            lastX = x;
            lastY = y;
            lastT = now;
            return;
        }

        double dt = (now - lastT) / 1e6;   // 毫秒
        double dist = Math.hypot(x - lastX, y - lastY);
        double speed = dt > 0 ? dist / dt : 0;
        emaSpeed = emaSpeed == 0 && ts.size() == 1 ? speed : emaSpeed * 0.7 + speed * 0.3;

        double w = widthModel(baseWidth, pressure);
        xs.add(x);
        ys.add(y);
        ws.add(w);
        ts.add(now);
        lastX = x;
        lastY = y;
        lastT = now;
    }

    /** 宽度模型：压感决定基础宽度，速度越快越细（笔锋）。 */
    private double widthModel(double baseWidth, double pressure) {
        double speedFactor = clamp(1.4 - 0.45 * emaSpeed, 0.5, 1.15);
        double w;
        if (pressure >= 0) {
            w = baseWidth * (0.25 + 0.75 * pressure) * speedFactor;
        } else {
            w = baseWidth * speedFactor;
        }
        return clamp(w, 0.8, 9.0);
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }
}

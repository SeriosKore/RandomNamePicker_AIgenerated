import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 笔画渲染器：Catmull-Rom 平滑采样 + 变宽轮廓多边形填充。
 * - 平滑：对原始点做 Catmull-Rom 插值采样，消除折线锯齿，保证流畅；
 * - 笔锋：首尾按累计距离渐细（起笔/收笔），宽度随采样点线性插值；
 * - 增量渲染：绘制过程中只重绘最近几段；收笔时整条重绘以应用收笔渐细。
 */
public class PenStrokeRenderer {

    /** 首尾渐细段长度（像素）。 */
    private static final double TAPER_LEN = 36;
    /** 采样步长（像素）。 */
    private static final double SAMPLE_STEP = 2.2;

    /**
     * 渲染笔画。
     *
     * @param whole true = 整条重绘（收笔时使用，应用收笔渐细）；
     *              false = 增量渲染（只画最近几段，保证流畅）。
     */
    public static void render(Graphics2D g, PenStroke stroke, Color color, boolean whole) {
        int n = stroke.size();
        if (n == 0) {
            return;
        }
        int start = whole ? 0 : Math.max(0, n - 3);
        double cumStart = distanceUpTo(stroke, start);
        double total = distanceUpTo(stroke, n - 1);
        List<double[]> samples = sample(stroke, start, n - 1, cumStart, total);
        if (samples.isEmpty()) {
            return;
        }

        // 圆头端点
        if (samples.size() == 1) {
            double[] s = samples.get(0);
            double r = s[2] / 2;
            g.fill(new java.awt.geom.Ellipse2D.Double(s[0] - r, s[1] - r, r * 2, r * 2));
            return;
        }

        double[] first = samples.get(0);
        double[] last = samples.get(samples.size() - 1);
        g.fill(new java.awt.geom.Ellipse2D.Double(first[0] - first[2] / 2, first[1] - first[2] / 2,
                first[2], first[2]));
        g.fill(new java.awt.geom.Ellipse2D.Double(last[0] - last[2] / 2, last[1] - last[2] / 2,
                last[2], last[2]));

        // 变宽多边形：左侧轮廓 + 右侧轮廓（倒序）
        int m = samples.size();
        double[] left = new double[m * 2];
        double[] right = new double[m * 2];
        for (int i = 0; i < m; i++) {
            double[] prev = samples.get(Math.max(0, i - 1));
            double[] next = samples.get(Math.min(m - 1, i + 1));
            double dx = next[0] - prev[0];
            double dy = next[1] - prev[1];
            double len = Math.hypot(dx, dy);
            double nx = len == 0 ? 0 : -dy / len;
            double ny = len == 0 ? 0 : dx / len;
            double half = samples.get(i)[2] / 2;
            left[i * 2] = samples.get(i)[0] + nx * half;
            left[i * 2 + 1] = samples.get(i)[1] + ny * half;
            right[i * 2] = samples.get(i)[0] - nx * half;
            right[i * 2 + 1] = samples.get(i)[1] - ny * half;
        }

        Path2D path = new Path2D.Double();
        path.moveTo(left[0], left[1]);
        for (int i = 1; i < m; i++) {
            path.lineTo(left[i * 2], left[i * 2 + 1]);
        }
        for (int i = m - 1; i >= 0; i--) {
            path.lineTo(right[i * 2], right[i * 2 + 1]);
        }
        path.closePath();
        g.fill(path);
    }

    /**
     * 对 [from, to] 区间的点做 Catmull-Rom 平滑采样，输出 (x, y, width) 三元组。
     */
    private static List<double[]> sample(PenStroke stroke, int from, int to,
                                         double cumStart, double total) {
        List<double[]> samples = new ArrayList<>();
        if (from >= to) {
            samples.add(new double[]{stroke.xAt(from), stroke.yAt(from), stroke.wAt(from)});
            return samples;
        }

        double dist = 0;
        for (int i = from; i < to; i++) {
            double x0 = stroke.xAt(Math.max(from, i - 1));
            double y0 = stroke.yAt(Math.max(from, i - 1));
            double x1 = stroke.xAt(i);
            double y1 = stroke.yAt(i);
            double x2 = stroke.xAt(i + 1);
            double y2 = stroke.yAt(i + 1);
            double x3 = stroke.xAt(Math.min(to, i + 2));
            double y3 = stroke.yAt(Math.min(to, i + 2));
            double w1 = stroke.wAt(i);
            double w2 = stroke.wAt(i + 1);

            double segLen = Math.hypot(x2 - x1, y2 - y1);
            int steps = Math.max(2, (int) Math.ceil(segLen / SAMPLE_STEP));
            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps;
                double px = catmull(x0, x1, x2, x3, t);
                double py = catmull(y0, y1, y2, y3, t);
                double w = w1 + (w2 - w1) * t;
                // 首尾笔锋：距笔画起点/终点近时渐细（收笔渐细仅在笔画结束后生效）
                double d = cumStart + dist + segLen * t;
                double startRamp = clamp(d / TAPER_LEN, 0.25, 1.0);
                double endRamp = stroke.finished ? clamp((total - d) / TAPER_LEN, 0.25, 1.0) : 1.0;
                w *= Math.min(startRamp, endRamp);
                samples.add(new double[]{px, py, w});
            }
            dist += segLen;
        }
        return samples;
    }

    /** 0 到 index 的累计笔画长度。 */
    private static double distanceUpTo(PenStroke stroke, int index) {
        double d = 0;
        for (int i = 0; i < index; i++) {
            d += Math.hypot(stroke.xAt(i + 1) - stroke.xAt(i), stroke.yAt(i + 1) - stroke.yAt(i));
        }
        return d;
    }

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t
                + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
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

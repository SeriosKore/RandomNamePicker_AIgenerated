/**
 * 数字范围模型（public：插件 API 的一部分）。
 */
public class NumberRange {
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

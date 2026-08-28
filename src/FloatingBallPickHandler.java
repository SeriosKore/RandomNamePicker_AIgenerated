import java.util.List;

/**
 * 悬浮球名字抽取拦截器（插件扩展点）。
 * 悬浮球执行名字抽取前，会依次询问所有已注册的拦截器；
 * 拦截器返回 true 表示“本次抽取已由插件接管”，主程序将不再执行默认单人滚动抽取。
 * 例如“多悬浮球插件”在单次抽取数量大于 1 时接管并展示多球动画。
 */
public interface FloatingBallPickHandler {

    /**
     * 拦截悬浮球的一次名字抽取。
     *
     * @param ball      触发抽取的悬浮球实例（插件可修改其显示文本/重绘）
     * @param scheme    当前方案
     * @param names     当前方案全部名单（非空）
     * @param pickCount 单次抽取数量（已按名单人数修正，最小为 1）
     * @return true 表示已接管本次抽取；false 表示交给主程序默认处理
     */
    boolean onNamePick(FloatingBall ball, Scheme scheme, List<String> names, int pickCount);
}

package com.randomnamepicker.ui;

import com.randomnamepicker.core.LogManager;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Timer;

/**
 * 通用滚停引擎（Stage2 重构产物，主窗与悬浮球共用；宿主插件生态一期新增可选自动停完成回调）。
 * <p>
 * - 候选由 {@link Supplier} 提供，显示由 {@link Consumer} 回调完成；
 *   截断/格式化等显示策略不写死在引擎里，由调用方在显示回调中自行处理
 *   （悬浮球做名字 5 字截断、主窗不截断）。
 * - maxTicks == null 表示无限滚动直到外部 stop()（主窗用法）；
 *   maxTicks 非 null 表示滚满该次数后自动停止（悬浮球传 20）。
 * - stop() 之后通过 {@link #getLastValue()} 返回最终定格候选，供主窗写日志。
 * - 可选 onAutoStop：仅在 maxTicks 滚满自动停止路径触发（默认 null = 现行为零变化）；
 *   供悬浮球在自停定格时派发 PICK_FINISHED 宿主事件；回调异常只记日志。
 * - 引擎不负责空数据提示：开始前由调用方先调 handler.canPick() 决定是否提示。
 * </p>
 */
public class RollingPicker {

    public static final int DEFAULT_INTERVAL_MS = 50;

    private final Supplier<String> candidateSupplier;
    private final Consumer<String> displayCallback;
    private final int intervalMs;
    private final Integer maxTicks;
    private final Runnable onAutoStop;

    private Timer timer;
    private int tickCount;
    private String lastValue;

    public RollingPicker(Supplier<String> candidateSupplier, Consumer<String> displayCallback) {
        this(candidateSupplier, displayCallback, DEFAULT_INTERVAL_MS, null, null);
    }

    public RollingPicker(Supplier<String> candidateSupplier, Consumer<String> displayCallback,
                         int intervalMs, Integer maxTicks) {
        this(candidateSupplier, displayCallback, intervalMs, maxTicks, null);
    }

    /**
     * 全量构造。onAutoStop 仅当 maxTicks 滚满自动停止时触发一次（随后可经 getLastValue()
     * 取最终定格候选）；可传 null（现行为零变化）。
     */
    public RollingPicker(Supplier<String> candidateSupplier, Consumer<String> displayCallback,
                         int intervalMs, Integer maxTicks, Runnable onAutoStop) {
        this.candidateSupplier = candidateSupplier;
        this.displayCallback = displayCallback;
        this.intervalMs = intervalMs;
        this.maxTicks = maxTicks;
        this.onAutoStop = onAutoStop;
    }

    /** 启动滚动；若已在滚动则先停止再重启（与旧实现“再次抽取前先停旧 Timer”等价）。 */
    public void start() {
        stop();
        tickCount = 0;
        lastValue = null;
        timer = new Timer(intervalMs, e -> {
            tickCount++;
            String candidate = candidateSupplier.get();
            lastValue = candidate;
            displayCallback.accept(candidate);
            if (maxTicks != null && tickCount >= maxTicks) {
                stop();
                fireAutoStop();
            }
        });
        timer.start();
    }

    /** 停止滚动。自动停止（maxTicks 滚满）与外部手动停止都走这里。 */
    public void stop() {
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        timer = null;
    }

    /** 停止后返回最后一次滚显候选；尚未产生任何候选时为 null。 */
    public String getLastValue() {
        return lastValue;
    }

    private void fireAutoStop() {
        if (onAutoStop == null) {
            return;
        }
        try {
            onAutoStop.run();
        } catch (Throwable t) {
            LogManager.log("滚停引擎自动停回调异常: " + t, "PLUGIN_LOAD_ERROR");
        }
    }
}

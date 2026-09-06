package com.randomnamepicker.plugin;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 宿主事件（宿主插件生态一期，九类，不可变载荷）。
 * <p>
 * 语义要点：
 * <ul>
 *   <li>载荷为不可变值对象（{@link Rectangle} 一律以副本存放、列表只读），不泄漏宿主 UI 对象；</li>
 *   <li>事件在 EDT 派发，逐监听器防御（异常只记日志）；</li>
 *   <li>订阅随插件卸载/整 jar 拒载回滚/onLoad 抛异常自动清理。</li>
 * </ul>
 */
public final class HostEvent {

    /** 事件类型（九类）。 */
    public enum Type {
        /** 插件集变化（提交成功后有提交时派发，与宿主 UI 刷新同口径；纯拒载由 PluginManager 加载历史查询承担） */
        PLUGINS_CHANGED,
        /** 方案切换（仅用户驱动且真实翻转；启动恢复/对话框重载/插件集刷新不发） */
        SCHEME_CHANGED,
        /** 模式切换（仅用户驱动且真实翻转；用户切方案触发的自动联动视为用户驱动、照发） */
        MODE_CHANGED,
        /** 抽取开始（仅真实抽取：canPick 通过并启动滚动） */
        PICK_STARTED,
        /** 抽取定格结束（仅真实抽取；被 canPick 拦截等未抽取出口不发） */
        PICK_FINISHED,
        /** 锁定/解锁状态翻转（覆盖 SettingsWindow 锁/解锁、密码验证成功、Ctrl+L×10 后门；仅真实翻转） */
        LOCK_CHANGED,
        /** 悬浮球显示（新建并显示后发，含当前 bounds） */
        BALL_SHOWN,
        /** 悬浮球隐藏/销毁（隐藏/关闭后发，含最后 bounds） */
        BALL_HIDDEN,
        /** 悬浮球几何（位置/尺寸）变化（节流后发，含 old/new bounds） */
        BALL_MOVED
    }

    /** 抽取触发源。 */
    public enum Source {
        MAIN, BALL
    }

    private final Type type;
    private final long timestamp;
    private final Source source;
    private final String schemeName;
    private final String schemeType;
    private final String modeId;
    private final String modeDisplayName;
    private final String result;
    private final Boolean locked;
    private final Rectangle oldBounds;
    private final Rectangle newBounds;
    private final List<PluginManager.LoadOutcome> outcomes;

    private HostEvent(Type type, long timestamp, Source source,
                      String schemeName, String schemeType,
                      String modeId, String modeDisplayName, String result,
                      Boolean locked, Rectangle oldBounds, Rectangle newBounds,
                      List<PluginManager.LoadOutcome> outcomes) {
        this.type = type;
        this.timestamp = timestamp;
        this.source = source;
        this.schemeName = schemeName;
        this.schemeType = schemeType;
        this.modeId = modeId;
        this.modeDisplayName = modeDisplayName;
        this.result = result;
        this.locked = locked;
        this.oldBounds = copy(oldBounds);
        this.newBounds = copy(newBounds);
        this.outcomes = outcomes == null
                ? Collections.<PluginManager.LoadOutcome>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(outcomes));
    }

    private static Rectangle copy(Rectangle r) {
        return r == null ? null : new Rectangle(r);
    }

    public Type getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Source getSource() {
        return source;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public String getSchemeType() {
        return schemeType;
    }

    public String getModeId() {
        return modeId;
    }

    public String getModeDisplayName() {
        return modeDisplayName;
    }

    public String getResult() {
        return result;
    }

    /** LOCK_CHANGED 专用：true=已锁定，false=已解锁；其它类型为 null。 */
    public Boolean getLocked() {
        return locked;
    }

    /** BALL_SHOWN/BALL_HIDDEN 的当前 bounds；副本，可安全持有。 */
    public Rectangle getBounds() {
        return copy(newBounds != null ? newBounds : oldBounds);
    }

    /** BALL_MOVED 的旧 bounds 副本；其它类型为 null。 */
    public Rectangle getOldBounds() {
        return copy(oldBounds);
    }

    /** BALL_MOVED 的新 bounds 副本；其它类型为 null。 */
    public Rectangle getNewBounds() {
        return copy(newBounds);
    }

    /** PLUGINS_CHANGED 载荷：派发时刻加载结果只读快照；其它类型为空列表。 */
    public List<PluginManager.LoadOutcome> getOutcomes() {
        return outcomes;
    }

    // ===================== 类型化工厂（宿主内部调用，保证载荷字段正确） =====================

    public static HostEvent pluginsChanged(List<PluginManager.LoadOutcome> outcomes) {
        return new HostEvent(Type.PLUGINS_CHANGED, System.currentTimeMillis(), null,
                null, null, null, null, null, null, null, null, outcomes);
    }

    public static HostEvent schemeChanged(String schemeName, String schemeType) {
        return new HostEvent(Type.SCHEME_CHANGED, System.currentTimeMillis(), null,
                schemeName, schemeType, null, null, null, null, null, null, null);
    }

    public static HostEvent modeChanged(String modeId, String modeDisplayName) {
        return new HostEvent(Type.MODE_CHANGED, System.currentTimeMillis(), null,
                null, null, modeId, modeDisplayName, null, null, null, null, null);
    }

    public static HostEvent pickStarted(Source source, String schemeName, String schemeType,
                                        String modeId, String modeDisplayName) {
        return new HostEvent(Type.PICK_STARTED, System.currentTimeMillis(), source,
                schemeName, schemeType, modeId, modeDisplayName, null, null, null, null, null);
    }

    public static HostEvent pickFinished(Source source, String schemeName, String schemeType,
                                         String modeId, String modeDisplayName, String result) {
        return new HostEvent(Type.PICK_FINISHED, System.currentTimeMillis(), source,
                schemeName, schemeType, modeId, modeDisplayName, result, null, null, null, null);
    }

    public static HostEvent lockChanged(boolean locked) {
        return new HostEvent(Type.LOCK_CHANGED, System.currentTimeMillis(), null,
                null, null, null, null, null, locked, null, null, null);
    }

    public static HostEvent ballShown(Rectangle bounds) {
        return new HostEvent(Type.BALL_SHOWN, System.currentTimeMillis(), null,
                null, null, null, null, null, null, null, bounds, null);
    }

    public static HostEvent ballHidden(Rectangle bounds) {
        return new HostEvent(Type.BALL_HIDDEN, System.currentTimeMillis(), null,
                null, null, null, null, null, null, null, bounds, null);
    }

    public static HostEvent ballMoved(Rectangle oldBounds, Rectangle newBounds) {
        return new HostEvent(Type.BALL_MOVED, System.currentTimeMillis(), null,
                null, null, null, null, null, null, oldBounds, newBounds, null);
    }
}

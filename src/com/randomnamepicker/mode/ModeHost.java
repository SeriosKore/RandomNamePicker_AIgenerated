package com.randomnamepicker.mode;

import com.randomnamepicker.core.DataManager;
import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.core.NameManager;
import com.randomnamepicker.core.SchemeManager;
import com.randomnamepicker.model.Scheme;
import java.awt.Rectangle;
import java.awt.Window;

/**
 * 模式宿主接口（Stage2 重构产物；宿主插件生态一期新增悬浮球只读几何查询）。
 * <p>
 * ModeHandler 通过本接口访问宿主能力，不再直接依赖 ui.NamePickerApp；
 * NamePickerApp 实现本接口。方法集按现有内置子类真实用法确定（getDataManager /
 * getLogManager 为宿主资源访问的接口完备项，供 Stage3 插件能力使用）。
 * </p>
 * <p>
 * 注意：悬浮球右键菜单调用的主窗操作（showConfigWindow / showSettingsWindow /
 * hideFloatingBall 等）不属于 ModeHandler 能力，不放入本接口；
 * FloatingBall 本阶段继续通过其对主窗的既有引用调用。
 * </p>
 */
public interface ModeHost {

    /** 当前方案对象（内置三个子类均需用到 currentScheme.getName()）。 */
    Scheme getCurrentScheme();

    NameManager getNameManager();

    SchemeManager getSchemeManager();

    DataManager getDataManager();

    LogManager getLogManager();

    /**
     * 悬浮球当前是否可见（宿主插件生态一期 G1；只读快照，须 EDT 调用）。
     */
    boolean isFloatingBallVisible();

    /**
     * 悬浮球当前 bounds（宿主插件生态一期 G1；只读快照，须 EDT 调用；未建/不可见返回 null）。
     */
    Rectangle getFloatingBallBounds();

    /**
     * 宿主主窗 bounds（宿主插件生态一期 G1；只读快照，须 EDT 调用）。
     */
    Rectangle getMainWindowBounds();

    /**
     * 宿主窗口自身（模态子窗口 owner 用；P0 修复批次新增）。
     * <p>
     * 内置模式弹设置对话框（NumberPicker / SeatPicker 等）必须用本方法取父窗：
     * 宿主应覆写为返回自身窗口（NamePickerApp 返回 {@code this}）。
     * </p>
     * <p>
     * 为什么不用 {@link #getOwner()}：本接口的 getOwner() 即 AWT
     * {@code java.awt.Window#getOwner()}，而<b>无 owner 的顶级窗返回 null</b>——宿主主窗
     * 恰是无 owner 的顶级 JFrame，故 getOwner() 恒为 null；把它当父窗转型会在对话框构造期
     * 产生 NPE（2026-09 事故：座位/数字设置按钮报
     * {@code Cannot invoke "...NamePickerApp.getSchemeManager()" because "this.mainApp" is null}）。
     * </p>
     * <p>
     * 默认实现回退到 {@link #getOwner()}（保持既有实现者的源码/二进制兼容）；宿主务必覆写，
     * 否则该回退值仍可能为 null。
     * </p>
     */
    default Window getHostWindow() {
        return getOwner();
    }

    /**
     * AWT 窗口 owner（JFileChooser / JOptionPane 父窗可传 null，AWT 会居中屏幕）。
     * <p>
     * 返回类型必须用 java.awt.Window 而非 Frame：NamePickerApp 继承自 JFrame，其
     * java.awt.Window#getOwner() 已被 AWT 模态机制使用；若宿主以 Frame 协变重写它并返回
     * 自身，会令 owner 链自引用，导致所有模态子窗口死锁（白屏无法关闭）。因此本方法即
     * AWT 的 Window.getOwner() 实现，宿主不得再重写。
     * </p>
     * <p>
     * <b>注意：顶级窗（宿主主窗）无 owner，本方法返回 null。</b>需要“宿主窗口自身”作模态子窗
     * owner 时请用 {@link #getHostWindow()}，不要用本方法。
     * </p>
     */
    Window getOwner();
}


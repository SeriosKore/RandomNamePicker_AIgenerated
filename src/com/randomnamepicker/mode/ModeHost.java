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
     * 父窗口（JOptionPane / JFileChooser 父窗口用；构造 NumberPicker / SeatPicker 时按需
     * 转型为 Frame —— 当前唯一宿主 NamePickerApp 即 JFrame）。
     * <p>
     * 返回类型必须用 java.awt.Window 而非 Frame：NamePickerApp 继承自 JFrame，其
     * java.awt.Window#getOwner() 已被 AWT 模态机制使用；若宿主以 Frame 协变重写它并返回
     * 自身，会令 owner 链自引用，导致所有模态子窗口死锁（白屏无法关闭）。因此本方法即
     * AWT 的 Window.getOwner() 实现，宿主不得再重写。
     * </p>
     */
    Window getOwner();
}


package com.randomnamepicker.mode;

import com.randomnamepicker.core.NameManager;
import com.randomnamepicker.core.SchemeManager;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 模式策略抽象契约（Stage2 重构后；宿主插件生态一期新增“模式专属悬浮球右键菜单”default 口子）。
 * <p>
 * 每个模式提供：稳定 modeId、下拉框显示名、可抽取校验、随机候选提供器，
 * 以及两个附加按钮的文案与点击行为。宿主统一为 {@link ModeHost}，
 * mode 包不再依赖 ui.NamePickerApp。
 * </p>
 * <p>
 * 说明：旧契约中的死方法 getModeButton1() / getModeButton2() 已删除——
 * 主窗口从不调用它们（子类各自 new 的按钮是死对象），删除属行为无影响。
 * </p>
 */
public abstract class ModeHandler {

    /**
     * 模式专属菜单项（悬浮球右键菜单用；宿主插件生态一期 G4）。
     * 仅承载展示标题与点击动作两个值，不承载宿主/插件对象引用。
     */
    public static final class ModeMenuItem {
        private final String title;
        private final ActionListener action;

        public ModeMenuItem(String title, ActionListener action) {
            this.title = title;
            this.action = action;
        }

        public String getTitle() {
            return title;
        }

        public ActionListener getAction() {
            return action;
        }
    }

    protected ModeHost host;
    protected SchemeManager schemeManager;
    protected NameManager nameManager;

    public ModeHandler(ModeHost host) {
        this.host = host;
        this.schemeManager = host.getSchemeManager();
        this.nameManager = host.getNameManager();
    }

    /**
     * 悬浮球右键菜单的“模式专属配置项”（宿主插件生态一期 G4）。
     * <p>
     * 默认返回空 = 无模式专属项；插件模式可覆写以在悬浮球右键菜单中提供专属入口
     * （内置三模式仍由宿主按 modeId 保留既有“配置名单/数字设置/座位设置”项，行为与现版一致）。
     * 生效语义：悬浮球每次新建实例时读取当前模式（切换模式后需重开悬浮球）。
     * </p>
     */
    public List<ModeMenuItem> getContextMenuItems() {
        return Collections.emptyList();
    }

    /** 稳定标识；内置三个与方案类型一致（name_list / number / seat）；插件可自定义。 */
    public abstract String getModeId();

    /** 下拉框显示文本；内置三个保持原串（UI 与日志共用同一文本）。 */
    public abstract String getDisplayName();

    /**
     * 返回 null 表示可开始抽取；非 null 为该模式“不可抽取原因”。
     * <p>
     * 语义与现状等价：主窗收到非 null 时用返回值作为弹窗文案并复位；
     * 悬浮球收到非 null 时不弹窗，由悬浮球按 modeId 映射现状短文本
     * （name_list→“损坏”、number→“无范围”、seat→“无座位”；
     * 未来插件模式无映射时回退显示本返回值）。
     * </p>
     */
    public abstract String canPick();

    /** 返回一个随机候选结果字符串（随机名字 / 随机整数 / 随机座位坐标）；主窗与悬浮球共用。 */
    public abstract Supplier<String> nextCandidate();

    public abstract void handleButton1Click();

    public abstract void handleButton2Click();

    public abstract String getButton1Text();

    public abstract String getButton2Text();
}

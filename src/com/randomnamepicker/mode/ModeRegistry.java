package com.randomnamepicker.mode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 最小模式注册表（Stage2 重构产物）。
 * <p>
 * 有序结构：modeId → (displayName, schemeType, 工厂)。下拉框数据源 = 注册表显示名列表；
 * 代码内一律经 modeId / 注册表条目判断，不再 switch 中文字符串。
 * 本阶段仅内置三项；register / unregister 已就位，为 Stage3 插件动态注册模式预留路径。
 * </p>
 * <p>
 * 工厂采用 Function&lt;ModeHost, ModeHandler&gt;（而非无参 Supplier）：Handler 构造需要
 * 宿主实例（可能同时存在主窗/其它宿主），注册时无法预知宿主，故在取用时注入。
 * </p>
 */
public final class ModeRegistry {

    /** 单条注册项。schemeType 仅内置模式用于“方案类型 → 内置模式”联动；插件可置 null。 */
    public static final class ModeDefinition {
        private final String modeId;
        private final String displayName;
        private final String schemeType;
        private final Function<ModeHost, ModeHandler> factory;

        public ModeDefinition(String modeId, String displayName, String schemeType,
                              Function<ModeHost, ModeHandler> factory) {
            this.modeId = modeId;
            this.displayName = displayName;
            this.schemeType = schemeType;
            this.factory = factory;
        }

        public String getModeId() {
            return modeId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getSchemeType() {
            return schemeType;
        }

        /** 以给定宿主实例化 Handler。 */
        public ModeHandler create(ModeHost host) {
            return factory.apply(host);
        }
    }

    private static final Map<String, ModeDefinition> BY_MODE_ID = new LinkedHashMap<>();
    private static final Map<String, String> DISPLAY_NAME_TO_MODE_ID = new LinkedHashMap<>();
    private static final Map<String, String> SCHEME_TYPE_TO_MODE_ID = new LinkedHashMap<>();

    static {
        register(new ModeDefinition("name_list", "名字列表模式", "name_list", NameListModeHandler::new));
        register(new ModeDefinition("number", "数字模式", "number", NumberModeHandler::new));
        register(new ModeDefinition("seat", "座位模式", "seat", SeatModeHandler::new));
    }

    private ModeRegistry() {
    }

    /** 注册一个模式（Stage3 插件注册入口）。重复 modeId 或重复显示名视为注册冲突。 */
    public static synchronized void register(ModeDefinition definition) {
        if (BY_MODE_ID.containsKey(definition.getModeId())) {
            throw new IllegalStateException("模式已注册: " + definition.getModeId());
        }
        if (DISPLAY_NAME_TO_MODE_ID.containsKey(definition.getDisplayName())) {
            throw new IllegalStateException("模式显示名已注册: " + definition.getDisplayName());
        }
        BY_MODE_ID.put(definition.getModeId(), definition);
        DISPLAY_NAME_TO_MODE_ID.put(definition.getDisplayName(), definition.getModeId());
        if (definition.getSchemeType() != null) {
            SCHEME_TYPE_TO_MODE_ID.put(definition.getSchemeType(), definition.getModeId());
        }
    }

    /** 反注册（供插件卸载 / 演示清理；内置模式不建议反注册）。 */
    public static synchronized void unregister(String modeId) {
        ModeDefinition definition = BY_MODE_ID.remove(modeId);
        if (definition != null) {
            DISPLAY_NAME_TO_MODE_ID.remove(definition.getDisplayName());
            if (definition.getSchemeType() != null) {
                SCHEME_TYPE_TO_MODE_ID.remove(definition.getSchemeType());
            }
        }
    }

    /** 下拉框数据源：按注册顺序返回全部显示名（只读快照）。 */
    public static synchronized List<String> getDisplayNames() {
        List<String> names = new ArrayList<>();
        for (ModeDefinition definition : BY_MODE_ID.values()) {
            names.add(definition.getDisplayName());
        }
        return Collections.unmodifiableList(names);
    }

    /** 按显示名查注册项；不存在返回 null。 */
    public static synchronized ModeDefinition getByDisplayName(String displayName) {
        String modeId = DISPLAY_NAME_TO_MODE_ID.get(displayName);
        return modeId == null ? null : BY_MODE_ID.get(modeId);
    }

    /** 按 modeId 查注册项；不存在返回 null。 */
    public static synchronized ModeDefinition getByModeId(String modeId) {
        return BY_MODE_ID.get(modeId);
    }

    /** 方案类型 → 内置模式联动映射；无对应返回 null。 */
    public static synchronized String getModeIdForSchemeType(String schemeType) {
        return SCHEME_TYPE_TO_MODE_ID.get(schemeType);
    }
}

package com.randomnamepicker.probe;

/**
 * 打包禁令 QA 专用“宿主包形状”类（宿主插件生态一期 D3）。
 * <p>
 * 该类并非宿主类，仅用于构造“jar 内出现 com/randomnamepicker/ 前缀 .class 条目”的违规打包场景，
 * 触发宿主整 jar 拒载 BUNDLED_HOST_CLASS。仅编译期使用，宿主与插件运行时均不会加载它。
 * </p>
 */
public class BundledHostProbe {

    private BundledHostProbe() {
    }

    public static String marker() {
        return "bundled-host-like-probe";
    }
}

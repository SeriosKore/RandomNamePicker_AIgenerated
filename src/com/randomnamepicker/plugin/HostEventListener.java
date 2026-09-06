package com.randomnamepicker.plugin;

/**
 * 宿主事件监听接口（宿主插件生态一期）。
 * <p>
 * 插件经 {@link PluginContext#addHostEventListener(HostEventListener)} 订阅宿主事件；
 * 回调在 <b>EDT</b> 派发，逐监听器防御执行（异常只记日志，不影响其它监听器）。
 * 订阅随插件卸载 / 整 jar 拒载回滚 / onLoad 抛异常自动清理（归属键 = jar 文件名 + 实现类全名），
 * 插件仍应在 onUnload 显式退订（双保险）。
 * </p>
 */
public interface HostEventListener {

    void onHostEvent(HostEvent event);
}

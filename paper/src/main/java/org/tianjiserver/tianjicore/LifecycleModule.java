package org.tianjiserver.tianjicore;

/**
 * 可选模块生命周期接口。
 * 模块在停用、重载或插件关闭前需要释放资源（如关闭打开的界面）时实现此接口。
 */
public interface LifecycleModule {

    /**
     * 模块停用前执行的清理逻辑。
     */
    void shutdown();
}

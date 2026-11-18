package org.tianjiserver.tianjicore;

import org.bukkit.event.Listener;
import org.tianjiserver.tianjicore.common.protocol.ModuleInfoDto;
import org.tianjiserver.tianjicore.common.protocol.ReloadResponse;
import org.tianjiserver.tianjicore.common.protocol.StatusResponse;
import org.tianjiserver.tianjicore.common.protocol.ToggleResponse;

import java.util.List;
import java.util.Optional;

/**
 * 模块管理辅助类。
 * 负责模块操作的封装，向外部控制层与命令层提供简洁的接口。
 */
public class TianjiCoreModuleHelper {

    private final TianjiCoreModuleManager moduleManager;

    public TianjiCoreModuleHelper(TianjiCore plugin) {
        this.moduleManager = new TianjiCoreModuleManager(plugin);
    }

    /**
     * 启动模块系统。
     */
    public void bootstrap() {
        moduleManager.bootstrap();
    }

    /**
     * 关闭模块系统。
     */
    public void shutdown() {
        moduleManager.shutdown();
    }

    /**
     * 查询所有模块运行状态。
     */
    public StatusResponse getStatus() {
        return moduleManager.status();
    }

    /**
     * 切换（开关）指定模块。
     */
    public ToggleResponse toggleModule(String moduleInput) {
        return moduleManager.toggle(moduleInput);
    }

    /**
     * 重载指定模块或插件。
     */
    public ReloadResponse reloadModule(String moduleInput) {
        return moduleManager.reload(moduleInput);
    }

    /**
     * 获取所有可切换模块的键。
     */
    public List<String> getToggleableModuleKeys() {
        return moduleManager.getToggleableModuleKeys();
    }

    /**
     * 获取所有模块的键。
     */
    public List<String> getModuleKeys() {
        return moduleManager.getModuleKeys();
    }

    /**
     * 获取所有模块运行状态快照。
     */
    public List<ModuleInfoDto> getModuleInfos() {
        return moduleManager.getModuleInfos();
    }

    /**
     * 获取插件重载的目标参数名。
     */
    public String getReloadPluginTarget() {
        return moduleManager.getReloadPluginTarget();
    }

    /**
     * 获取当前已开启的指定模块实例。
     */
    public <T extends Listener> Optional<T> getActiveModule(String moduleKey, Class<T> moduleType) {
        return moduleManager.getActiveModule(moduleKey, moduleType);
    }
}

package org.tianjiserver.tianjicore;

import org.bukkit.plugin.java.JavaPlugin;
import org.tianjiserver.tianjicore.control.ControlHttpServer;
import org.tianjiserver.tianjicore.feature.fumo.FumoCommand;
import org.tianjiserver.tianjicore.feature.fumo.FumoModule;
import revxrsal.commands.bukkit.BukkitLamp;

/**
 * TianjiCore Paper 插件主入口。
 * 负责初始化配置、模块生命周期、控制端 HTTP 服务与本地功能命令注册。
 */
public class TianjiCore extends JavaPlugin {

    private static TianjiCore instance;
    private TianjiCoreModuleHelper moduleHelper;
    private ControlHttpServer controlHttpServer;

    /**
     * 插件启用入口：初始化配置、模块、HTTP 控制服务与玩偶命令注册。
     */
    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        moduleHelper = new TianjiCoreModuleHelper(this);
        moduleHelper.bootstrap();

        controlHttpServer = new ControlHttpServer(this, moduleHelper);
        controlHttpServer.start();

        var lamp = BukkitLamp.builder(this).build();
        lamp.register(new FumoCommand(() -> moduleHelper.getActiveModule("fumo", FumoModule.class)));

        getLogger().info("TianjiCore 已启动");
    }

    /**
     * 插件关闭入口：先停用控制服务端，再执行模块卸载流程。
     */
    @Override
    public void onDisable() {
        if (controlHttpServer != null) {
            controlHttpServer.stop();
        }
        if (moduleHelper != null) {
            moduleHelper.shutdown();
        }
    }

    /**
     * 提供全局插件实例访问入口。
     */
    public static TianjiCore getInstance() {
        return instance;
    }
}

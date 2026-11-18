package org.tianjiserver.tianjicore.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import org.tianjiserver.tianjicore.common.protocol.ControlProtocol;
import org.tianjiserver.tianjicore.velocity.TianjiCoreCommand.SuggestArg;
import revxrsal.commands.Lamp;
import revxrsal.commands.velocity.VelocityLamp;
import revxrsal.commands.velocity.actor.VelocityCommandActor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * TianjiCore Velocity 插件主入口。
 * 负责初始化代理端配置、异步 HTTP 控制客户端以及全局 /tianjicore (/tc) 管理命令。
 */
@Plugin(
        id = "tianjicore-velocity",
        name = "TianjiCore-Velocity",
        version = "1.0.4",
        description = "TianjiCore Velocity 代理端管理插件",
        authors = {"Tianji-Community"}
)
public class TianjiCoreVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final VelocityConfig config;
    private final ControlHttpClient httpClient;

    @Inject
    public TianjiCoreVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.config = new VelocityConfig(dataDirectory, logger);
        this.httpClient = new ControlHttpClient();
    }

    /**
     * 代理启动初始化：加载配置并注册命令。
     */
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        config.bootstrap();

        Lamp<VelocityCommandActor> lamp = VelocityLamp.builder(this, server)
                .suggestionProviders(providers -> providers.addProviderForAnnotation(
                        SuggestArg.class,
                        annotation -> context -> provideSuggestions(annotation.value())
                ))
                .build();
        lamp.register(new TianjiCoreCommand(config, httpClient));

        logger.info("TianjiCore-Velocity 已启动");
    }

    /**
     * 代理关闭清理：关闭异步 HTTP 客户端资源。
     */
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        httpClient.close();
    }

    private List<String> provideSuggestions(TianjiCoreCommand.SuggestType type) {
        return switch (type) {
            case SERVER -> {
                List<String> suggestions = new ArrayList<>();
                suggestions.add(ControlProtocol.ALL_SERVERS_TARGET);
                suggestions.addAll(config.getSnapshot().serverNames());
                yield suggestions;
            }
            case TOGGLE_MODULE -> httpClient.getKnownToggleableKeys();
            case RELOAD_TARGET -> {
                List<String> suggestions = new ArrayList<>();
                suggestions.add(httpClient.getKnownReloadPluginTarget());
                suggestions.add(ControlProtocol.RELOAD_PROXY_TARGET);
                suggestions.addAll(httpClient.getKnownModuleKeys());
                yield suggestions;
            }
        };
    }
}

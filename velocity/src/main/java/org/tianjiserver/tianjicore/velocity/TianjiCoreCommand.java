package org.tianjiserver.tianjicore.velocity;

import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.tianjiserver.tianjicore.common.protocol.ControlProtocol;
import org.tianjiserver.tianjicore.common.protocol.ModuleInfoDto;
import org.tianjiserver.tianjicore.common.protocol.ReloadFailureDto;
import org.tianjiserver.tianjicore.common.protocol.ReloadResponse;
import org.tianjiserver.tianjicore.common.protocol.StatusResponse;
import org.tianjiserver.tianjicore.common.protocol.ToggleResponse;
import org.tianjiserver.tianjicore.velocity.ControlHttpClient.ServerCallResult;
import org.tianjiserver.tianjicore.velocity.VelocityConfig.BackendServerConfig;
import org.tianjiserver.tianjicore.velocity.VelocityConfig.ConfigSnapshot;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.velocity.annotation.CommandPermission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Velocity 端 TianjiCore 管理命令处理器。
 * 通过异步 HTTP 控制通道统一管理全部或指定后端 Paper 子服的模块状态与重载。
 */
@Command({"tianjicore", "tc"})
public class TianjiCoreCommand {

    private final VelocityConfig velocityConfig;
    private final ControlHttpClient httpClient;
    private final MiniMessage mini = MiniMessage.miniMessage();

    public TianjiCoreCommand(VelocityConfig velocityConfig, ControlHttpClient httpClient) {
        this.velocityConfig = velocityConfig;
        this.httpClient = httpClient;
    }

    /**
     * 根命令占位：输出帮助信息。
     */
    @CommandPlaceholder
    public void handleRoot(CommandSource source) {
        handleHelpCommand(source);
    }

    /**
     * 开关全部或指定子服的指定模块（仅允许可切换模块）。
     */
    @CommandPermission("tianjicore.command.admin")
    @Subcommand("toggle")
    public void handleToggleCommand(
            CommandSource source,
            @SuggestArg(SuggestType.TOGGLE_MODULE) String moduleInput,
            @Optional @SuggestArg(SuggestType.SERVER) String serverInput
    ) {
        ConfigSnapshot snapshot = velocityConfig.getSnapshot();
        List<BackendServerConfig> targets = resolveTargetsOrNotify(source, snapshot, serverInput);
        if (targets.isEmpty()) {
            return;
        }

        httpClient.toggleModule(targets, moduleInput, snapshot.requestTimeoutMs())
                .whenComplete((results, throwable) -> {
                    if (throwable != null) {
                        sendUnexpectedError(source, throwable);
                        return;
                    }
                    renderToggleResults(source, moduleInput, results);
                });
    }

    /**
     * 重载 Velocity 端配置、指定模块或插件整体配置。
     */
    @CommandPermission("tianjicore.command.admin")
    @Subcommand("reload")
    public void handleReloadCommand(
            CommandSource source,
            @SuggestArg(SuggestType.RELOAD_TARGET) String targetInput,
            @Optional @SuggestArg(SuggestType.SERVER) String serverInput
    ) {
        String normalizedTarget = targetInput.toLowerCase(Locale.ROOT).trim();
        boolean reloadProxyOnly = ControlProtocol.RELOAD_PROXY_ALIASES.contains(normalizedTarget);
        boolean reloadPlugin = ControlProtocol.DEFAULT_RELOAD_PLUGIN_ALIASES.contains(normalizedTarget);

        ConfigSnapshot previousSnapshot = velocityConfig.getSnapshot();
        if (reloadProxyOnly || reloadPlugin) {
            try {
                velocityConfig.reload();
                source.sendMessage(mini.deserialize("<green>Velocity 端配置已重载"));
            } catch (Exception exception) {
                source.sendMessage(Component.text("Velocity 端配置重载失败: " + safeMessage(exception),
                        NamedTextColor.RED));
                if (reloadProxyOnly) {
                    return;
                }
            }
            if (reloadProxyOnly) {
                return;
            }
        }

        ConfigSnapshot snapshot = velocityConfig.getSnapshot();
        List<BackendServerConfig> targets = resolveTargetsOrNotify(source, snapshot, serverInput);
        if (targets.isEmpty()) {
            return;
        }

        Map<String, String> fallbackTokens = buildFallbackTokens(previousSnapshot, snapshot);
        httpClient.reloadTarget(targets, targetInput, snapshot.requestTimeoutMs(), fallbackTokens)
                .whenComplete((results, throwable) -> {
                    if (throwable != null) {
                        sendUnexpectedError(source, throwable);
                        return;
                    }
                    renderReloadResults(source, targetInput, results);
                });
    }

    private static Map<String, String> buildFallbackTokens(
            ConfigSnapshot previousSnapshot,
            ConfigSnapshot currentSnapshot
    ) {
        if (previousSnapshot == currentSnapshot || previousSnapshot.servers().isEmpty()) {
            return Map.of();
        }
        Map<String, String> fallbackTokens = new HashMap<>();
        for (BackendServerConfig current : currentSnapshot.servers().values()) {
            BackendServerConfig previous = previousSnapshot.servers().get(current.normalizedName());
            if (previous != null
                    && previous.token() != null
                    && !previous.token().isBlank()
                    && !previous.token().equals(current.token())) {
                fallbackTokens.put(current.normalizedName(), previous.token());
            }
        }
        return fallbackTokens;
    }

    /**
     * 查看全部或指定子服中各模块的实际运行状态。
     */
    @CommandPermission("tianjicore.command.admin")
    @Subcommand("status")
    public void handleStatusCommand(
            CommandSource source,
            @Optional @SuggestArg(SuggestType.SERVER) String serverInput
    ) {
        ConfigSnapshot snapshot = velocityConfig.getSnapshot();
        List<BackendServerConfig> targets = resolveTargetsOrNotify(source, snapshot, serverInput);
        if (targets.isEmpty()) {
            return;
        }

        httpClient.fetchStatus(targets, snapshot.requestTimeoutMs())
                .whenComplete((results, throwable) -> {
                    if (throwable != null) {
                        sendUnexpectedError(source, throwable);
                        return;
                    }
                    renderStatusResults(source, results);
                });
    }

    /**
     * 输出当前可用子命令与模块/子服参数提示。
     */
    @Subcommand("help")
    public void handleHelpCommand(CommandSource source) {
        List<String> serverNames = velocityConfig.getSnapshot().serverNames();
        String serversLabel = serverNames.isEmpty() ? "(未配置)" : String.join(", ", serverNames);

        source.sendMessage(mini.deserialize("<yellow>命令帮助:"));
        source.sendMessage(mini.deserialize("<gray>/tianjicore status [server] <white>查看全部或指定子服的模块运行状态"));
        source.sendMessage(mini.deserialize("<gray>/tianjicore toggle <module> [server] <white>开关全部或指定子服的模块"));
        source.sendMessage(mini.deserialize("<gray>/tianjicore reload <module|plugin|proxy> [server] <white>重载代理、插件或指定模块"));
        source.sendMessage(mini.deserialize("<gray>可开关模块: <aqua>" + String.join(", ", httpClient.getKnownToggleableKeys())));
        source.sendMessage(mini.deserialize("<gray>可重载模块: <aqua>" + String.join(", ", httpClient.getKnownModuleKeys())));
        source.sendMessage(mini.deserialize("<gray>整体重载参数: <aqua>" + httpClient.getKnownReloadPluginTarget()
                + "<gray> | 代理配置重载: <aqua>" + ControlProtocol.RELOAD_PROXY_TARGET));
        source.sendMessage(Component.text("受管子服 (默认 all): ", NamedTextColor.GRAY)
                .append(Component.text(serversLabel, NamedTextColor.AQUA)));
    }

    private List<BackendServerConfig> resolveTargetsOrNotify(
            CommandSource source,
            ConfigSnapshot snapshot,
            String serverInput
    ) {
        if (snapshot.servers().isEmpty()) {
            source.sendMessage(mini.deserialize(
                    "<red>未配置任何受管子服，请在 plugins/tianjicore-velocity/config.yml 的 servers 中配置后端节点。"));
            return List.of();
        }

        List<BackendServerConfig> targets = snapshot.resolveTargets(serverInput);
        if (targets.isEmpty()) {
            source.sendMessage(Component.text("未知受管子服: " + serverInput, NamedTextColor.RED));
            source.sendMessage(Component.text("可用子服: all, " + String.join(", ", snapshot.serverNames()),
                    NamedTextColor.YELLOW));
        }
        return targets;
    }

    private void renderStatusResults(CommandSource source, List<ServerCallResult<StatusResponse>> results) {
        source.sendMessage(Component.text("模块运行状态:", NamedTextColor.YELLOW));
        for (ServerCallResult<StatusResponse> result : results) {
            if (!result.isSuccess()) {
                source.sendMessage(serverPrefix(result.serverName())
                        .append(Component.text("查询失败: " + result.error(), NamedTextColor.RED)));
                continue;
            }

            source.sendMessage(serverPrefix(result.serverName())
                    .append(Component.text("已连接", NamedTextColor.GREEN)));
            for (ModuleInfoDto module : result.payload().modules()) {
                source.sendMessage(Component.text("  " + module.displayName() + " (" + module.key() + "): ",
                                NamedTextColor.GRAY)
                        .append(Component.text(module.enabled() ? "开启" : "关闭",
                                module.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
            }
        }
    }

    private void renderToggleResults(
            CommandSource source,
            String moduleInput,
            List<ServerCallResult<ToggleResponse>> results
    ) {
        for (ServerCallResult<ToggleResponse> result : results) {
            Component prefix = serverPrefix(result.serverName());
            if (!result.isSuccess()) {
                source.sendMessage(prefix.append(Component.text("请求失败: " + result.error(), NamedTextColor.RED)));
                continue;
            }

            ToggleResponse response = result.payload();
            ModuleInfoDto info = response.moduleInfo();
            switch (response.status()) {
                case UNKNOWN_MODULE -> {
                    source.sendMessage(prefix.append(Component.text("未知模块: " + moduleInput, NamedTextColor.RED)));
                    source.sendMessage(prefix.append(Component.text("可开关模块: ", NamedTextColor.YELLOW))
                            .append(Component.text(String.join(", ", response.toggleableKeys()), NamedTextColor.AQUA)));
                }
                case NOT_TOGGLEABLE -> {
                    source.sendMessage(prefix.append(Component.text("该模块不支持 toggle: " + info.key(),
                            NamedTextColor.RED)));
                    source.sendMessage(prefix.append(Component.text("可开关模块: ", NamedTextColor.YELLOW))
                            .append(Component.text(String.join(", ", response.toggleableKeys()), NamedTextColor.AQUA)));
                }
                case FAILED -> source.sendMessage(prefix.append(
                        Component.text("模块切换失败: " + info.displayName(), NamedTextColor.RED)));
                case SUCCESS -> source.sendMessage(prefix.append(
                        Component.text(info.displayName() + " 已" + (info.enabled() ? "开启" : "关闭"),
                                NamedTextColor.GREEN)));
            }
        }
    }

    private void renderReloadResults(
            CommandSource source,
            String targetInput,
            List<ServerCallResult<ReloadResponse>> results
    ) {
        for (ServerCallResult<ReloadResponse> result : results) {
            Component prefix = serverPrefix(result.serverName());
            if (!result.isSuccess()) {
                source.sendMessage(prefix.append(Component.text("请求失败: " + result.error(), NamedTextColor.RED)));
                continue;
            }

            ReloadResponse response = result.payload();
            switch (response.status()) {
                case SUCCESS_PLUGIN -> source.sendMessage(prefix.append(
                        Component.text("插件与模块配置已重载", NamedTextColor.GREEN)));
                case UNKNOWN_MODULE -> {
                    source.sendMessage(prefix.append(Component.text("未知模块: " + targetInput, NamedTextColor.RED)));
                    source.sendMessage(prefix.append(Component.text("可用模块: ", NamedTextColor.YELLOW))
                            .append(Component.text(String.join(", ", response.moduleKeys()), NamedTextColor.AQUA)));
                }
                case FAILED -> {
                    source.sendMessage(prefix.append(Component.text("重载失败:", NamedTextColor.RED)));
                    for (ReloadFailureDto failure : response.failures()) {
                        source.sendMessage(Component.text("  " + failure.target() + ": " + failure.reason(),
                                NamedTextColor.RED));
                    }
                }
                case SUCCESS_MODULE -> {
                    ModuleInfoDto info = response.moduleInfo();
                    source.sendMessage(prefix.append(
                                    Component.text(info.displayName() + " 已重载，当前状态: ", NamedTextColor.GREEN))
                            .append(Component.text(info.enabled() ? "开启" : "关闭",
                                    info.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
                }
            }
        }
    }

    private static Component serverPrefix(String serverName) {
        return Component.text("[" + serverName + "] ", NamedTextColor.GOLD);
    }

    private static void sendUnexpectedError(CommandSource source, Throwable throwable) {
        source.sendMessage(Component.text("执行远端控制请求时发生异常: " + safeMessage(throwable),
                NamedTextColor.RED));
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    /**
     * 命令参数补全类型。
     */
    public enum SuggestType {
        SERVER,
        TOGGLE_MODULE,
        RELOAD_TARGET
    }

    /**
     * 标记需要动态补全的命令参数。
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface SuggestArg {
        SuggestType value();
    }
}

package org.tianjiserver.tianjicore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.tianjiserver.tianjicore.common.protocol.ControlProtocol;
import org.tianjiserver.tianjicore.common.protocol.ModuleInfoDto;
import org.tianjiserver.tianjicore.common.protocol.ReloadFailureDto;
import org.tianjiserver.tianjicore.common.protocol.ReloadResponse;
import org.tianjiserver.tianjicore.common.protocol.ReloadStatus;
import org.tianjiserver.tianjicore.common.protocol.StatusResponse;
import org.tianjiserver.tianjicore.common.protocol.ToggleResponse;
import org.tianjiserver.tianjicore.common.protocol.ToggleStatus;
import org.tianjiserver.tianjicore.feature.FirstJoinMessage;
import org.tianjiserver.tianjicore.feature.PhantomSpawnBlocker;
import org.tianjiserver.tianjicore.feature.fumo.FumoModule;
import org.tianjiserver.tianjicore.fixer.EndermanBlockMoveBlocker;
import org.tianjiserver.tianjicore.fixer.RecipeBugFix;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 模块管理器。
 * 统一管理模块注册、启停、重载与配置持久化。
 */
class TianjiCoreModuleManager {

    private static final String RELOAD_PLUGIN_TARGET = ControlProtocol.RELOAD_PLUGIN_TARGET;
    private static final Set<String> RELOAD_PLUGIN_ALIASES = Set.copyOf(ControlProtocol.DEFAULT_RELOAD_PLUGIN_ALIASES);

    private final TianjiCore plugin;
    private final Map<String, ModuleState> modules = new LinkedHashMap<>();
    private final Map<String, String> moduleAliasIndex = new LinkedHashMap<>();

    TianjiCoreModuleManager(TianjiCore plugin) {
        this.plugin = plugin;
    }

    void bootstrap() {
        // 在这里集中声明模块，命令层无需感知具体模块实现。
        registerModule(
            "firstjoinmessage",
            "首次进服消息",
            true,
            FirstJoinMessage::new,
            "welcome",
            "firstjoin"
        );
        registerModule(
                "recipebugfix",
                "配方修复",
                false,
                RecipeBugFix::new,
                "recipe",
                "recipes"
        );
        registerModule(
                "phantomspawnblocker",
                "阻止幻翼生成",
                true,
                PhantomSpawnBlocker::new,
                "phantom",
                "phantomblocker"
        );
        registerModule(
                "endermanmushroombugfix",
                "禁止末影人搬动方块",
                false,
                EndermanBlockMoveBlocker::new,
                "enderman",
                "endermanblockmoveblocker",
                "mushroomfix"
        );
        registerModule(
                "fumo",
                "玩偶",
                true,
                () -> new FumoModule(plugin),
                "doll",
                "plush",
                "plushie"
        );

        plugin.getConfig().options().copyDefaults(true);
        modules.keySet().forEach(moduleKey ->
                plugin.getConfig().addDefault(moduleConfigPath(moduleKey), true)
        );
        // 保存默认值后，按配置应用模块状态。
        plugin.saveConfig();

        applyConfigStates(false);
    }

    void shutdown() {
        modules.values().forEach(this::stopModuleIfRunning);
    }

    /**
     * 查询所有模块当前运行状态。
     */
    StatusResponse status() {
        return new StatusResponse(getModuleInfos(), RELOAD_PLUGIN_TARGET);
    }

    /**
     * 切换指定模块的启停状态。
     */
    ToggleResponse toggle(String rawModuleInput) {
        ModuleState module = findModule(rawModuleInput);
        if (module == null) {
            return new ToggleResponse(ToggleStatus.UNKNOWN_MODULE, null, getToggleableModuleKeys());
        }
        if (!module.toggleable) {
            return new ToggleResponse(ToggleStatus.NOT_TOGGLEABLE, module.toInfo(), getToggleableModuleKeys());
        }

        boolean targetState = !module.enabled;
        // toggle 的目标状态为“当前状态取反”。
        if (targetState && !startModule(module)) {
            return new ToggleResponse(ToggleStatus.FAILED, module.toInfo(), getToggleableModuleKeys());
        }
        if (!targetState) {
            stopModule(module);
        }

        plugin.getConfig().set(moduleConfigPath(module.key), targetState);
        plugin.saveConfig();
        return new ToggleResponse(ToggleStatus.SUCCESS, module.toInfo(), getToggleableModuleKeys());
    }

    /**
     * 重载插件配置，并按目标刷新对应模块。
     */
    ReloadResponse reload(String rawTargetInput) {
        String target = normalize(rawTargetInput);
        boolean reloadPlugin = RELOAD_PLUGIN_ALIASES.contains(target);
        ModuleState module = findModule(rawTargetInput);
        if (!reloadPlugin && module == null) {
            return new ReloadResponse(ReloadStatus.UNKNOWN_MODULE, null, List.of(), getModuleKeys(), RELOAD_PLUGIN_TARGET);
        }

        try {
            // reloadConfig 本身会记录并吞掉 YAML 加载错误，先校验以避免误报成功。
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (configFile.exists()) {
                new YamlConfiguration().load(configFile);
            }
            plugin.reloadConfig();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "插件配置重载失败", exception);
            return new ReloadResponse(ReloadStatus.FAILED, null,
                    List.of(new ReloadFailureDto("config.yml", failureReason(exception))),
                    getModuleKeys(), RELOAD_PLUGIN_TARGET);
        }

        if (reloadPlugin) {
            List<ReloadFailureDto> failures = applyConfigStates(true);
            return new ReloadResponse(failures.isEmpty() ? ReloadStatus.SUCCESS_PLUGIN : ReloadStatus.FAILED,
                    null, failures, getModuleKeys(), RELOAD_PLUGIN_TARGET);
        }

        ReloadFailureDto failure = applyConfigState(module, true);
        return new ReloadResponse(failure == null ? ReloadStatus.SUCCESS_MODULE : ReloadStatus.FAILED,
                module.toInfo(), failure == null ? List.of() : List.of(failure),
                getModuleKeys(), RELOAD_PLUGIN_TARGET);
    }

    /**
     * 返回插件级重载的固定参数名。
     */
    String getReloadPluginTarget() {
        return RELOAD_PLUGIN_TARGET;
    }

    /**
     * 返回所有可用于 reload 的目标（含 plugin）。
     */
    List<String> getReloadTargets() {
        List<String> targets = new ArrayList<>();
        targets.add(RELOAD_PLUGIN_TARGET);
        targets.addAll(getModuleKeys());
        return targets;
    }

    /**
     * 返回所有模块主键（按注册顺序）。
     */
    List<String> getModuleKeys() {
        return List.copyOf(modules.keySet());
    }

    /**
     * 返回模块实际运行状态，而非配置中的目标状态。
     */
    List<ModuleInfoDto> getModuleInfos() {
        return modules.values().stream().map(ModuleState::toInfo).toList();
    }

    /**
     * 返回允许 toggle 的模块主键。
     */
    List<String> getToggleableModuleKeys() {
        return modules.values().stream()
                .filter(module -> module.toggleable)
                .map(module -> module.key)
                .collect(Collectors.toList());
    }

    /**
     * 获取当前处于运行状态的指定模块实例。
     */
    <T extends Listener> Optional<T> getActiveModule(String rawKey, Class<T> moduleType) {
        ModuleState module = findModule(rawKey);
        if (module == null || !module.enabled || !moduleType.isInstance(module.listenerInstance)) {
            return Optional.empty();
        }
        return Optional.of(moduleType.cast(module.listenerInstance));
    }

    private List<ReloadFailureDto> applyConfigStates(boolean forceRestartEnabledModule) {
        List<ReloadFailureDto> failures = new ArrayList<>();
        for (ModuleState module : modules.values()) {
            ReloadFailureDto failure = applyConfigState(module, forceRestartEnabledModule);
            if (failure != null) {
                failures.add(failure);
            }
        }
        return List.copyOf(failures);
    }

    private ReloadFailureDto applyConfigState(ModuleState module, boolean forceRestartEnabledModule) {
        try {
            boolean shouldEnable = plugin.getConfig().getBoolean(moduleConfigPath(module.key), true);
            if (shouldEnable) {
                boolean started = forceRestartEnabledModule ? restartModule(module) : startModule(module);
                if (!started) {
                    return new ReloadFailureDto(module.key, module.lastFailure);
                }
            } else {
                stopModule(module);
            }
            return null;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "模块配置应用失败: " + module.key, exception);
            return new ReloadFailureDto(module.key, failureReason(exception));
        }
    }

    private boolean restartModule(ModuleState module) {
        stopModule(module);
        return startModule(module);
    }

    private boolean startModule(ModuleState module) {
        if (module.enabled) {
            return true;
        }

        try {
            // 通过工厂创建模块监听器实例。
            Listener listener = module.listenerFactory.get();
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            module.listenerInstance = listener;
            module.enabled = true;
            module.lastFailure = null;
            plugin.getLogger().info("模块已开启: " + module.key);
            return true;
        } catch (Exception exception) {
            module.lastFailure = failureReason(exception);
            plugin.getLogger().log(Level.SEVERE, "模块开启失败: " + module.key, exception);
            module.listenerInstance = null;
            module.enabled = false;
            return false;
        }
    }

    private void stopModule(ModuleState module) {
        if (!module.enabled) {
            return;
        }

        stopModuleIfRunning(module);
        plugin.getLogger().info("模块已关闭: " + module.key);
    }

    private void stopModuleIfRunning(ModuleState module) {
        if (module.listenerInstance instanceof LifecycleModule lifecycleModule) {
            try {
                lifecycleModule.shutdown();
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "模块清理失败: " + module.key, exception);
            }
        }
        if (module.listenerInstance != null) {
            // 注销监听器，确保模块关闭后不再响应事件。
            HandlerList.unregisterAll(module.listenerInstance);
        }
        module.listenerInstance = null;
        module.enabled = false;
    }

    /**
     * 注册模块并建立主键/别名映射。
     */
    private void registerModule(
            String moduleKey,
            String displayName,
            boolean toggleable,
            Supplier<? extends Listener> listenerFactory,
            String... aliases
    ) {
        String normalizedKey = normalize(moduleKey);
        ModuleState module = new ModuleState(normalizedKey, displayName, toggleable, listenerFactory);
        modules.put(normalizedKey, module);
        moduleAliasIndex.put(normalizedKey, normalizedKey);

        for (String alias : aliases) {
            moduleAliasIndex.put(normalize(alias), normalizedKey);
        }
    }

    /**
     * 按主键或别名定位模块。
     */
    private ModuleState findModule(String rawInput) {
        String normalizedInput = normalize(rawInput);
        String moduleKey = moduleAliasIndex.get(normalizedInput);
        if (moduleKey == null) {
            return null;
        }
        return modules.get(moduleKey);
    }

    private String moduleConfigPath(String moduleKey) {
        return "modules." + moduleKey + ".enabled";
    }

    /**
     * 标准化命令输入，统一比较规则。
     */
    private String normalize(String input) {
        return input.toLowerCase(Locale.ROOT).trim();
    }

    private static String failureReason(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static class ModuleState {
        private final String key;
        private final String displayName;
        private final boolean toggleable;
        private final Supplier<? extends Listener> listenerFactory;
        private Listener listenerInstance;
        private boolean enabled;
        private String lastFailure;

        private ModuleState(
                String key,
                String displayName,
                boolean toggleable,
                Supplier<? extends Listener> listenerFactory
        ) {
            this.key = key;
            this.displayName = displayName;
            this.toggleable = toggleable;
            this.listenerFactory = listenerFactory;
            this.listenerInstance = null;
            this.enabled = false;
        }

        private ModuleInfoDto toInfo() {
            return new ModuleInfoDto(key, displayName, enabled, toggleable);
        }
    }
}

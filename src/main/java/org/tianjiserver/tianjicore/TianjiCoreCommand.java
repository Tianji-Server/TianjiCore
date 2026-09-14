package org.tianjiserver.tianjicore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;


/**
 * TianjiCore 命令入口。
 * 负责对接命令框架，并将模块操作委托给模块辅助层。
 */
@Command({"tianjicore", "tianji", "tc"})
public class TianjiCoreCommand {

    private final TianjiCoreModuleHelper moduleHelper;
    private final MiniMessage mini = MiniMessage.miniMessage();

    public TianjiCoreCommand(TianjiCore plugin) {
        this.moduleHelper = new TianjiCoreModuleHelper(plugin);
    }

    /**
     * 插件启动时初始化模块管理器。
     */
    public void bootstrap() {
        moduleHelper.bootstrap();
    }

    /**
     * 插件关闭时执行模块下线流程。
     */
    public void shutdown() {
        moduleHelper.shutdown();
    }

    /**
     * 开关指定模块（仅允许可切换模块）。
     */
    @CommandPermission("tianjicore.command.admin")
    @Subcommand("toggle")
    public void handleToggleCommand(CommandSender sender, String moduleInput) {
        var result = moduleHelper.toggleModule(moduleInput);

        switch (result.status()) {
            case UNKNOWN_MODULE:
                sender.sendMessage(mini.deserialize("<red>未知模块: " + moduleInput));
                sender.sendMessage(mini.deserialize(
                        "<yellow>可开关模块: <aqua>" + String.join(", ", moduleHelper.getToggleableModuleKeys())));
                break;

            case NOT_TOGGLEABLE:
                var moduleInfo = result.moduleInfo();
                sender.sendMessage(mini.deserialize(
                        "<red>该模块不支持 toggle: " + moduleInfo.key()));
                sender.sendMessage(mini.deserialize(
                        "<yellow>可开关模块: <aqua>" + String.join(", ", moduleHelper.getToggleableModuleKeys())));
                break;

            case FAILED:
                moduleInfo = result.moduleInfo();
                sender.sendMessage(mini.deserialize(
                        "<red>模块切换失败: " + moduleInfo.displayName()));
                break;

            case SUCCESS:
                moduleInfo = result.moduleInfo();
                sender.sendMessage(mini.deserialize(
                        "<green>" + moduleInfo.displayName() + " 已" + (moduleInfo.enabled() ? "开启" : "关闭")));
                break;
        }
    }

    /**
     * 重载指定模块，或执行插件级整体重载。
     */
    @CommandPermission("tianjicore.command.admin")
    @Subcommand("reload")
    public void handleReloadCommand(CommandSender sender, String moduleInput) {
        var result = moduleHelper.reloadModule(moduleInput);

        switch (result.status()) {
            case SUCCESS_PLUGIN:
                sender.sendMessage(mini.deserialize("<green>插件与模块配置已重载"));
                break;

            case UNKNOWN_MODULE:
                sender.sendMessage(mini.deserialize("<red>未知模块: " + moduleInput));
                sender.sendMessage(mini.deserialize(
                        "<yellow>可用模块: <aqua>" + String.join(", ", moduleHelper.getModuleKeys())));
                break;

            case FAILED:
                sender.sendMessage(Component.text("重载失败:", NamedTextColor.RED));
                for (var failure : result.failures()) {
                    // 异常文本按纯文本显示，避免其中的标签被 MiniMessage 解析。
                    sender.sendMessage(Component.text(
                            failure.target() + ": " + failure.reason(), NamedTextColor.RED));
                }
                break;

            case SUCCESS_MODULE:
                var moduleInfo = result.moduleInfo();
                sender.sendMessage(mini.deserialize(
                        "<green>" + moduleInfo.displayName() + " 已重载，当前状态: "
                                + (moduleInfo.enabled() ? "<green>开启" : "<red>关闭")));
                break;
        }
    }

    /**
     * 查看全部模块的实际运行状态。
     */
    @CommandPermission("tianjicore.command.admin")
    @Subcommand("status")
    public void handleStatusCommand(CommandSender sender) {
        sender.sendMessage(Component.text("模块运行状态:", NamedTextColor.YELLOW));
        for (var module : moduleHelper.getModuleInfos()) {
            sender.sendMessage(Component.text(module.displayName() + " (" + module.key() + "): ",
                            NamedTextColor.GRAY)
                    .append(Component.text(module.enabled() ? "开启" : "关闭",
                            module.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }
    }

    /**
     * 输出当前可用子命令与模块参数提示。
     */
    @Subcommand("help")
    public void handleHelpCommand(CommandSender sender) {
        sender.sendMessage(mini.deserialize("<yellow>命令帮助:"));
        sender.sendMessage(mini.deserialize("<gray>/tianjicore status <white>查看所有模块的运行状态"));
        sender.sendMessage(mini.deserialize("<gray>/tianjicore toggle <module> <white>开关指定模块"));
        sender.sendMessage(mini.deserialize("<gray>/tianjicore reload <module|plugin> <white>重载插件或指定模块"));
        sender.sendMessage(mini.deserialize("<gray>可开关模块: <aqua>" + String.join(", ", moduleHelper.getToggleableModuleKeys())));
        sender.sendMessage(mini.deserialize("<gray>可重载模块: <aqua>" + String.join(", ", moduleHelper.getModuleKeys())));
        sender.sendMessage(mini.deserialize("<gray>插件重载参数: <aqua>" + moduleHelper.getReloadPluginTarget()));
    }
}

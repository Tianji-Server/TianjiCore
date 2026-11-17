package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.function.Supplier;

/**
 * Fumo 玩偶模块命令入口（仅管理员可制作）。
 */
public class FumoCommand {

    private final Supplier<java.util.Optional<FumoModule>> moduleSupplier;
    private final MiniMessage mini = MiniMessage.miniMessage();

    public FumoCommand(Supplier<java.util.Optional<FumoModule>> moduleSupplier) {
        this.moduleSupplier = moduleSupplier;
    }

    /**
     * 根命令：输出玩偶模块管理帮助。
     */
    @CommandPermission("tianjicore.fumo.admin")
    @Command("fumo")
    public void handleRoot(CommandSender sender) {
        handleHelp(sender);
    }

    /**
     * 打开 Fumo 玩偶制作界面，可传入自定义物品名称（支持 MiniMessage）。
     */
    @CommandPermission("tianjicore.fumo.admin")
    @Command("fumo craft")
    public void handleCraft(Player player, @Optional String name) {
        var activeModule = moduleSupplier.get();
        if (activeModule.isEmpty()) {
            player.sendMessage(mini.deserialize("<red>玩偶模块当前已关闭，请先使用 /tc toggle fumo 开启。"));
            return;
        }
        activeModule.get().openCraftGui(player, name);
    }

    /**
     * 输出玩偶命令帮助信息。
     */
    @CommandPermission("tianjicore.fumo.admin")
    @Command("fumo help")
    public void handleHelp(CommandSender sender) {
        sender.sendMessage(mini.deserialize("<yellow>Fumo 玩偶命令帮助:"));
        sender.sendMessage(mini.deserialize("<gray>/fumo craft [名称] <white>打开玩偶制作台并自定义 6 槽装备"));
        sender.sendMessage(mini.deserialize("<gray>/fumo help <white>查看此帮助信息"));
    }
}

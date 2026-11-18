package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.function.Supplier;

/**
 * Fumo 玩偶模块命令入口（仅管理员可制作）。
 * 主命令遵循 tianji-* 命名规范，保留 fumo 作为无前缀快捷别名。
 */
@Command({"tianji-fumo", "fumo"})
@CommandPermission("tianjicore.fumo.admin")
public class FumoCommand {

    private final Supplier<java.util.Optional<FumoModule>> moduleSupplier;
    private final MiniMessage mini = MiniMessage.miniMessage();

    public FumoCommand(Supplier<java.util.Optional<FumoModule>> moduleSupplier) {
        this.moduleSupplier = moduleSupplier;
    }

    /**
     * 根命令：输出玩偶模块管理帮助。
     */
    @CommandPlaceholder
    public void handleRoot(CommandSender sender) {
        handleHelp(sender);
    }

    /**
     * 打开 Fumo 玩偶制作界面，可传入自定义物品名称（支持 MiniMessage）。
     */
    @Subcommand("craft")
    public void handleCraft(Player player, @Optional String name) {
        var activeModule = moduleSupplier.get();
        if (activeModule.isEmpty()) {
            player.sendMessage(mini.deserialize("<red>当前子服的玩偶模块已关闭，请先使用 /tc toggle fumo <子服> 开启。"));
            return;
        }
        activeModule.get().openCraftGui(player, name);
    }

    /**
     * 输出玩偶命令帮助信息。
     */
    @Subcommand("help")
    public void handleHelp(CommandSender sender) {
        sender.sendMessage(mini.deserialize("<yellow>玩偶命令帮助 <gray>(别名: /fumo):"));
        sender.sendMessage(mini.deserialize("<gray>/tianji-fumo craft [名称] <white>打开玩偶制作台并自定义 6 槽装备"));
        sender.sendMessage(mini.deserialize("<gray>/tianji-fumo help <white>查看此帮助信息"));
    }
}

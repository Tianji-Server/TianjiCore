package org.tianjiserver.tianjicore.feature;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.tianjiserver.tianjicore.TianjiCore;

/**
 * 首次进服欢迎消息模块。
 */
public class FirstJoinMessage implements Listener {

    private static final long MESSAGE_DELAY_TICKS = 60L;

    private final TianjiCore plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public FirstJoinMessage() {
        plugin = TianjiCore.getInstance();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(miniMessage.deserialize(
                        plugin.getConfig().getString("first-join-message.message", "")
                ));
            }
        }, MESSAGE_DELAY_TICKS);
    }
}
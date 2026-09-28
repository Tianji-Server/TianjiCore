package org.tianjiserver.tianjicore.feature;

import org.bukkit.Material;
import org.bukkit.EntityEffect;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.tianjiserver.tianjicore.TianjiCore;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 管理首次进服玩家的伤害保护和一次性面包领取。
 */
public class NewbieManager implements Listener {

    private static final int INITIAL_PROTECTION_USES = 3;
    private static final int BREAD_AMOUNT = 16;

    private final TianjiCore plugin;
    private final File dataFile;
    private final YamlConfiguration data;

    public NewbieManager(TianjiCore plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "newbie-data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) {
            return;
        }

        String playerPath = playerPath(player.getUniqueId());
        if (data.contains(playerPath)) {
            return;
        }

        data.set(playerPath + ".protection-uses", INITIAL_PROTECTION_USES);
        data.set(playerPath + ".bread-claimed", false);
        saveData();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        String protectionPath = playerPath(player.getUniqueId()) + ".protection-uses";
        int remainingUses = data.getInt(protectionPath, 0);
        if (remainingUses <= 0) {
            return;
        }

        event.setCancelled(true);
        player.playEffect(EntityEffect.TOTEM_RESURRECT);
        data.set(protectionPath, remainingUses - 1);
        saveData();
    }

    public BreadClaimResult claimBread(Player player) {
        String playerPath = playerPath(player.getUniqueId());
        if (!data.contains(playerPath)) {
            return BreadClaimResult.NOT_ELIGIBLE;
        }
        if (data.getBoolean(playerPath + ".bread-claimed")) {
            return BreadClaimResult.ALREADY_CLAIMED;
        }

        data.set(playerPath + ".bread-claimed", true);
        if (!saveData()) {
            data.set(playerPath + ".bread-claimed", false);
            return BreadClaimResult.SAVE_FAILED;
        }

        var overflow = player.getInventory().addItem(new ItemStack(Material.BREAD, BREAD_AMOUNT));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        return BreadClaimResult.SUCCESS;
    }

    private String playerPath(UUID playerId) {
        return "players." + playerId;
    }

    private boolean saveData() {
        try {
            data.save(dataFile);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存新手数据: " + exception.getMessage());
            return false;
        }
    }

    public enum BreadClaimResult {
        SUCCESS,
        ALREADY_CLAIMED,
        NOT_ELIGIBLE,
        SAVE_FAILED
    }
}
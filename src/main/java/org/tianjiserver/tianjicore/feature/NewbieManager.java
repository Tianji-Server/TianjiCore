package org.tianjiserver.tianjicore.feature;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.tianjiserver.tianjicore.TianjiCore;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 管理首次进服奖励和一次性面包领取。
 */
public class NewbieManager implements Listener {

    static final int BREAD_AMOUNT = 16;
    static final int TOTEM_AMOUNT = 3;
    private static final int NEWBIE_EFFECT_DURATION_TICKS = 20 * 60 * 10;

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

        data.set(playerPath + ".bread-claimed", true);
        if (!saveData()) {
            data.set(playerPath, null);
            return;
        }

        applyNewbieEffects(player);
        giveBread(player);
        giveTotems(player);
    }

    void applyNewbieEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                NEWBIE_EFFECT_DURATION_TICKS, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                NEWBIE_EFFECT_DURATION_TICKS, 0));
    }

    public BreadClaimResult claimBread(Player player) {
        String playerPath = playerPath(player.getUniqueId());
        if (data.getBoolean(playerPath + ".bread-claimed")) {
            return BreadClaimResult.ALREADY_CLAIMED;
        }

        data.set(playerPath + ".bread-claimed", true);
        if (!saveData()) {
            data.set(playerPath + ".bread-claimed", false);
            return BreadClaimResult.SAVE_FAILED;
        }

        giveBread(player);
        return BreadClaimResult.SUCCESS;
    }

    void giveBread(Player player) {
        var overflow = player.getInventory().addItem(new ItemStack(Material.BREAD, BREAD_AMOUNT));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    void giveTotems(Player player) {
        var overflow = player.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING, TOTEM_AMOUNT));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private String playerPath(UUID playerId) {
        return "players." + playerId;
    }

    private boolean saveData() {
        try {
            data.save(dataFile);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存新手数据：" + exception.getMessage());
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
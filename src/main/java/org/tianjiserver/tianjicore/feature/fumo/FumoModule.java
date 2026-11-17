package org.tianjiserver.tianjicore.feature.fumo;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.tianjiserver.tianjicore.LifecycleModule;
import org.tianjiserver.tianjicore.TianjiCore;

/**
 * Fumo 玩偶模块入口。
 * 统一装配配置、物品编解码器、管理员制作 GUI 与实体交互保护。
 */
public class FumoModule implements Listener, LifecycleModule {

    private final FumoCraftGui craftGui;
    private final FumoEntityListener entityListener;

    public FumoModule(TianjiCore plugin) {
        FumoPose pose = FumoPose.from(plugin.getConfig());
        FumoItemCodec codec = new FumoItemCodec(plugin);
        this.craftGui = new FumoCraftGui(codec);
        this.entityListener = new FumoEntityListener(pose, codec);
    }

    /**
     * 为管理员打开 Fumo 玩偶制作界面。
     */
    public void openCraftGui(Player player, String rawName) {
        craftGui.open(player, rawName);
    }

    @Override
    public void shutdown() {
        craftGui.shutdown();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        craftGui.onInventoryClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        craftGui.onInventoryDrag(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        craftGui.onInventoryClose(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        craftGui.onPlayerQuit(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        entityListener.onPlayerInteract(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onManipulateArmorStand(PlayerArmorStandManipulateEvent event) {
        entityListener.onManipulateArmorStand(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        entityListener.onInteractAtEntity(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        entityListener.onEntityDamage(event);
    }
}

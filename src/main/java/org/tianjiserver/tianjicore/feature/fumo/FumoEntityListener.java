package org.tianjiserver.tianjicore.feature.fumo;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.tianjiserver.tianjicore.TianjiCore;

import java.util.Map;

/**
 * Fumo 玩偶实体放置、落点追踪、回收与防破坏监听器。
 * 不记录放置者/制作者信息，也不限制回收归属权。
 */
public class FumoEntityListener {

    private final FumoItemCodec codec;
    private final FumoTrackerManager trackerManager;

    public FumoEntityListener(TianjiCore plugin, FumoItemCodec codec) {
        this.codec = codec;
        this.trackerManager = new FumoTrackerManager(plugin);
        sanitizeLoadedEntities(plugin);
    }

    /**
     * 模块停用前停止所有追踪任务并清理临时隐形盔甲架。
     */
    public void shutdown() {
        trackerManager.shutdown();
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (!codec.isFumoItem(item)) {
            return;
        }

        event.setCancelled(true);
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Block targetBlock = clickedBlock.isPassable()
                ? clickedBlock
                : clickedBlock.getRelative(event.getBlockFace());
        if (!targetBlock.isPassable()) {
            event.getPlayer().sendMessage(Component.text("该位置空间不足，无法放置玩偶！", NamedTextColor.RED));
            return;
        }

        Player player = event.getPlayer();
        Location spawnLocation = computeSpawnLocation(targetBlock, player);
        ItemStack[] slots = codec.decodeEquipment(item);
        Component itemDisplayName = codec.extractItemDisplayName(item);
        boolean includeDerivativeNotice = codec.hasDerivativeNotice(item);

        ArmorStand trackerStand = spawnTrackerEntity(spawnLocation);
        ArmorStand fumoStand = spawnFumoEntity(
                spawnLocation.clone().add(0.0, FumoTrackerManager.TRACK_OFFSET_Y, 0.0),
                slots,
                itemDisplayName,
                includeDerivativeNotice
        );
        trackerManager.startTracking(fumoStand, trackerStand, spawnLocation);
        consumeOneIfNeeded(player, hand);
    }

    public void onManipulateArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isProtectedArmorStand(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (isProtectedArmorStand(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) {
            return;
        }
        if (codec.isTrackerEntity(stand)) {
            event.setCancelled(true);
            if (event instanceof EntityDamageByEntityEvent byEntity
                    && byEntity.getDamager() instanceof Player player
                    && player.isSneaking()) {
                trackerManager.findTrackedFumo(stand.getUniqueId())
                        .ifPresent(fumo -> pickupFumo(player, fumo));
            }
            return;
        }
        if (!codec.isFumoEntity(stand)) {
            return;
        }

        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                pickupFumo(player, stand);
            }
            return;
        }

        event.setCancelled(true);
    }

    public void onEntityKnockback(EntityKnockbackEvent event) {
        if (isProtectedArmorStand(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    public void onEntityPortal(EntityPortalEvent event) {
        if (isProtectedArmorStand(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof ArmorStand stand) {
                sanitizeArmorStand(stand);
            }
        }
    }

    private ArmorStand spawnFumoEntity(
            Location location,
            ItemStack[] slots,
            Component itemDisplayName,
            boolean includeDerivativeNotice
    ) {
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setSmall(true);
            stand.setArms(true);
            stand.setBasePlate(false);
            applyFumoPhysicsImmunity(stand);
            stand.setCanPickupItems(false);
            stand.setRemoveWhenFarAway(false);
            // 名称仅存在于物品上，世界中不展示悬浮字。
            stand.customName(null);
            stand.setCustomNameVisible(false);

            FumoPose.DEFAULT
                    .withRandomLimbAndHeadJitter(FumoPose.PLACEMENT_JITTER_TOTAL_DEGREES)
                    .applyTo(stand);

            EntityEquipment equipment = stand.getEquipment();
            for (int i = 0; i < FumoItemCodec.FUMO_SLOTS.size(); i++) {
                EquipmentSlot slot = FumoItemCodec.FUMO_SLOTS.get(i);
                ItemStack piece = slots[i];
                equipment.setItem(slot, piece == null ? null : piece.clone(), true);
            }
            lockAllEquipmentSlots(stand);

            codec.markEntity(stand, itemDisplayName, includeDerivativeNotice);
        });
    }

    private ArmorStand spawnTrackerEntity(Location location) {
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setArms(false);
            stand.setBasePlate(false);
            stand.setGravity(true);
            stand.setCanMove(true);
            stand.setMarker(false);
            stand.setSilent(true);
            stand.setCanPickupItems(false);
            stand.setRemoveWhenFarAway(false);
            stand.setPersistent(false);
            stand.customName(null);
            stand.setCustomNameVisible(false);
            lockAllEquipmentSlots(stand);
            codec.markTrackerEntity(stand);
        });
    }

    private boolean isProtectedArmorStand(Entity entity) {
        return codec.isFumoEntity(entity) || codec.isTrackerEntity(entity);
    }

    private void sanitizeLoadedEntities(TianjiCore plugin) {
        for (World world : plugin.getServer().getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                sanitizeArmorStand(stand);
            }
        }
    }

    private void sanitizeArmorStand(ArmorStand stand) {
        if (codec.isFumoEntity(stand)) {
            applyFumoPhysicsImmunity(stand);
        } else if (codec.isTrackerEntity(stand) && !trackerManager.isActiveTracker(stand.getUniqueId())) {
            stand.remove();
        }
    }

    private static void applyFumoPhysicsImmunity(ArmorStand stand) {
        stand.setGravity(false);
        stand.setCanMove(false);
    }

    private static void lockAllEquipmentSlots(ArmorStand stand) {
        for (EquipmentSlot slot : FumoItemCodec.FUMO_SLOTS) {
            stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
            stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }
    }

    private void pickupFumo(Player player, ArmorStand stand) {
        if (!stand.isValid()) {
            return;
        }

        trackerManager.stopTrackingByFumoId(stand.getUniqueId());
        ItemStack fumoItem = codec.extractFumoItem(stand);
        Location dropLocation = stand.getLocation();
        stand.remove();

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(fumoItem);
        for (ItemStack extra : overflow.values()) {
            dropLocation.getWorld().dropItemNaturally(dropLocation, extra);
        }
    }

    private static Location computeSpawnLocation(Block targetBlock, Player player) {
        Location location = targetBlock.getLocation().add(0.5, 0.0, 0.5);
        float yaw = normalizeYaw(player.getLocation().getYaw() + 180.0f);
        if (player.isSneaking()) {
            yaw = Math.round(yaw / 45.0f) * 45.0f;
        }
        location.setYaw(yaw);
        location.setPitch(0.0f);
        return location;
    }

    private static float normalizeYaw(float rawYaw) {
        float yaw = rawYaw % 360.0f;
        if (yaw < 0.0f) {
            yaw += 360.0f;
        }
        return yaw;
    }

    private static void consumeOneIfNeeded(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack current = player.getInventory().getItem(hand);
        if (current.isEmpty()) {
            return;
        }
        if (current.getAmount() <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            current.setAmount(current.getAmount() - 1);
            player.getInventory().setItem(hand, current);
        }
    }
}

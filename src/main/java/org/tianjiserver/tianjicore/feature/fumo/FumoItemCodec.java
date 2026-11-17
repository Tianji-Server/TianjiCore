package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Level;

/**
 * Fumo 玩偶物品与盔甲架装备栏编解码器。
 * 装备数据直接保存在 Fumo 物品 PDC 与盔甲架原生装备栏上，不写入服务端外部存储，
 * 也不记录制作者或归属权信息。
 */
public class FumoItemCodec {

    /**
     * Fumo 玩偶支持的六个盔甲架装备槽位顺序。
     */
    public static final List<EquipmentSlot> FUMO_SLOTS = List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND
    );

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    private final Plugin plugin;
    private final NamespacedKey equipmentKey;
    private final NamespacedKey markerKey;
    private final NamespacedKey trackerMarkerKey;
    private final NamespacedKey entityItemNameKey;

    public FumoItemCodec(Plugin plugin) {
        this.plugin = plugin;
        this.equipmentKey = new NamespacedKey(plugin, "fumo_equipment");
        this.markerKey = new NamespacedKey(plugin, "fumo_marker");
        this.trackerMarkerKey = new NamespacedKey(plugin, "fumo_tracker_marker");
        this.entityItemNameKey = new NamespacedKey(plugin, "fumo_item_name");
    }

    /**
     * 解析 Fumo 物品显示名称；空输入时返回默认玩偶名称。
     */
    public static Component resolveDisplayName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return Component.text("Fumo 玩偶", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false);
        }
        return MINI.deserialize(rawName.trim())
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * 判断给定物品是否为有效的 Fumo 玩偶物品。
     */
    public boolean isFumoItem(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(equipmentKey, PersistentDataType.BYTE_ARRAY);
    }

    /**
     * 判断给定实体是否为 Fumo 盔甲架。
     */
    public boolean isFumoEntity(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) {
            return false;
        }
        return stand.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    /**
     * 判断给定实体是否为 Fumo 落点追踪用隐形盔甲架。
     */
    public boolean isTrackerEntity(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) {
            return false;
        }
        return stand.getPersistentDataContainer().has(trackerMarkerKey, PersistentDataType.BYTE);
    }

    /**
     * 创建包含 6 槽装备快照与显示名称的 Fumo 物品。
     */
    public ItemStack createFumoItem(ItemStack[] slots, Component displayName) {
        ItemStack[] normalized = normalizeSlots(slots);
        ItemStack item = new ItemStack(Material.ARMOR_STAND);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        Component finalName = displayName != null ? displayName : resolveDisplayName(null);
        meta.displayName(finalName.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));

        byte[] encoded = ItemStack.serializeItemsAsBytes(normalized);
        meta.getPersistentDataContainer().set(equipmentKey, PersistentDataType.BYTE_ARRAY, encoded);
        meta.lore(buildLore());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 从 Fumo 物品解码 6 槽装备数组（空槽位为 {@code null}）。
     */
    public ItemStack[] decodeEquipment(ItemStack item) {
        ItemStack[] empty = new ItemStack[FUMO_SLOTS.size()];
        if (!isFumoItem(item)) {
            return empty;
        }
        ItemMeta meta = item.getItemMeta();
        byte[] bytes = meta.getPersistentDataContainer().get(equipmentKey, PersistentDataType.BYTE_ARRAY);
        if (bytes == null || bytes.length == 0) {
            return empty;
        }

        try {
            ItemStack[] raw = ItemStack.deserializeItemsFromBytes(bytes);
            return normalizeSlots(raw);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "解码 Fumo 物品装备数据失败", exception);
            return empty;
        }
    }

    /**
     * 提取 Fumo 物品的显示名称。
     */
    public Component extractItemDisplayName(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                Component displayName = meta.displayName();
                if (displayName != null) {
                    return displayName;
                }
            }
        }
        return resolveDisplayName(null);
    }

    /**
     * 标记已放置的 Fumo 盔甲架，仅在实体 PDC 隐式保留物品名称以便回收还原（不显示在实体头顶）。
     */
    public void markEntity(ArmorStand stand, Component itemDisplayName) {
        PersistentDataContainer pdc = stand.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        if (itemDisplayName != null) {
            pdc.set(entityItemNameKey, PersistentDataType.STRING, GSON.serialize(itemDisplayName));
        }
    }

    /**
     * 标记放置时临时生成的重力追踪隐形盔甲架。
     */
    public void markTrackerEntity(ArmorStand stand) {
        stand.getPersistentDataContainer().set(trackerMarkerKey, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * 从已放置的 Fumo 盔甲架读取当前装备与隐式物品名称，重新编码为 Fumo 物品。
     */
    public ItemStack extractFumoItem(ArmorStand stand) {
        ItemStack[] slots = new ItemStack[FUMO_SLOTS.size()];
        EntityEquipment equipment = stand.getEquipment();
        for (int i = 0; i < FUMO_SLOTS.size(); i++) {
            ItemStack piece = equipment.getItem(FUMO_SLOTS.get(i));
            slots[i] = (piece == null || piece.isEmpty()) ? null : piece.clone();
        }

        Component displayName = resolveEntityItemName(stand);
        return createFumoItem(slots, displayName);
    }

    private Component resolveEntityItemName(ArmorStand stand) {
        String rawJson = stand.getPersistentDataContainer().get(entityItemNameKey, PersistentDataType.STRING);
        if (rawJson != null && !rawJson.isBlank()) {
            try {
                return GSON.deserialize(rawJson);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "读取 Fumo 实体物品名称失败，回退默认名称", exception);
            }
        }
        return resolveDisplayName(null);
    }

    private static ItemStack[] normalizeSlots(ItemStack[] source) {
        ItemStack[] normalized = new ItemStack[FUMO_SLOTS.size()];
        if (source == null) {
            return normalized;
        }
        int limit = Math.min(source.length, FUMO_SLOTS.size());
        for (int i = 0; i < limit; i++) {
            ItemStack item = source[i];
            normalized[i] = (item == null || item.isEmpty()) ? null : item.clone();
        }
        return normalized;
    }

    private static List<Component> buildLore() {
        return List.of(
                loreLine("FUMO玩偶摆件", NamedTextColor.GRAY),
                loreLine("────────────", NamedTextColor.DARK_GRAY),
                loreLine("FUMO特性", NamedTextColor.AQUA),
                loreBullet("右键方块放置，潜行放置对齐 45° 朝向"),
                loreBullet("自动落至表面后固定，不受重力与移动影响"),
                loreBullet("免疫伤害、击退与传送，锁定装备防误取"),
                loreBullet("放置后不显示悬浮名称，自带轻微坐姿微调"),
                Component.empty(),
                loreLine("拆除方法", NamedTextColor.YELLOW),
                loreLine("• ", NamedTextColor.DARK_GRAY)
                        .append(loreLine("潜行 + 左键 / 右键", NamedTextColor.WHITE))
                        .append(loreLine(" 点击玩偶即可回收", NamedTextColor.GRAY))
        );
    }

    private static Component loreBullet(String text) {
        return loreLine("• ", NamedTextColor.DARK_GRAY)
                .append(loreLine(text, NamedTextColor.GRAY));
    }

    private static Component loreLine(String text, NamedTextColor color) {
        return Component.text(text, color)
                .decoration(TextDecoration.ITALIC, false);
    }
}

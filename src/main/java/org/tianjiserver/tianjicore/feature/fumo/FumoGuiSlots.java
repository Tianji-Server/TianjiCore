package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.createButton;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.nonItalic;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.CROSS_SLOTS;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.INPUT_SLOTS;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.SMART_FALLBACK_SLOTS;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.SlotSpec;

/**
 * 十字架装备槽的占位符管理与物品合法性校验。
 */
final class FumoGuiSlots {

    private final FumoItemCodec codec;
    private final NamespacedKey placeholderKey;

    FumoGuiSlots(Plugin plugin, FumoItemCodec codec) {
        this.codec = codec;
        this.placeholderKey = new NamespacedKey(plugin, "fumo_gui_placeholder");
    }

    boolean isPlaceholder(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(placeholderKey, PersistentDataType.BYTE);
    }

    boolean isRealItem(ItemStack item) {
        return item != null && !item.isEmpty() && !isPlaceholder(item);
    }

    ItemStack realItemOrNull(ItemStack item) {
        return isRealItem(item) ? item : null;
    }

    boolean isAllowedEquipment(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return true;
        }
        if (isBlockedContainerMaterial(item.getType()) || codec.isFumoItem(item) || isPlaceholder(item)) {
            return false;
        }
        return !(item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.hasBlockState()
                && blockStateMeta.getBlockState() instanceof Container);
    }

    void rejectEquipment(Player player) {
        FumoGuiItems.deny(player, "该物品不可放入玩偶装备槽！", NamedTextColor.RED);
    }

    ItemStack createPlaceholderItem(SlotSpec spec) {
        List<Component> lore = List.of(
                nonItalic("状态: ", NamedTextColor.DARK_GRAY)
                        .append(nonItalic("未装备", NamedTextColor.GRAY)),
                nonItalic(spec.positionHint(), NamedTextColor.DARK_GRAY),
                Component.empty(),
                nonItalic("• 左键/右键放置手中物品", NamedTextColor.YELLOW),
                nonItalic("• Shift+点击背包物品自动填入", NamedTextColor.YELLOW)
        );
        ItemStack stack = createButton(
                Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                nonItalic(spec.label(), NamedTextColor.AQUA),
                lore,
                false
        );
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(placeholderKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * 写入十字架槽位；传入空物品时恢复为占位符。
     */
    void setCrossSlotItem(Inventory inventory, int slot, ItemStack item) {
        if (item == null || item.isEmpty()) {
            SlotSpec spec = FumoGuiLayout.crossSlot(slot);
            inventory.setItem(slot, spec != null ? createPlaceholderItem(spec) : null);
            return;
        }
        inventory.setItem(slot, item);
    }

    void restoreEmptyPlaceholders(Inventory inventory) {
        for (SlotSpec spec : CROSS_SLOTS) {
            ItemStack current = inventory.getItem(spec.slot());
            if (current == null || current.isEmpty()) {
                inventory.setItem(spec.slot(), createPlaceholderItem(spec));
            }
        }
    }

    int countEquippedSlots(Inventory inventory) {
        int count = 0;
        for (int slot : INPUT_SLOTS) {
            if (isRealItem(inventory.getItem(slot))) {
                count++;
            }
        }
        return count;
    }

    /**
     * 为 Shift+点击的物品寻找目标槽位：优先匹配物品自身装备栏，否则按回退顺序取第一个空位。
     *
     * @return 目标槽位，无空位时返回 -1
     */
    int findSmartTargetSlot(Inventory inventory, ItemStack item) {
        EquipmentSlot preferred = item.getType().getEquipmentSlot();
        for (SlotSpec spec : CROSS_SLOTS) {
            if (spec.equipmentSlot() == preferred && !isRealItem(inventory.getItem(spec.slot()))) {
                return spec.slot();
            }
        }
        for (int slot : SMART_FALLBACK_SLOTS) {
            if (!isRealItem(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isBlockedContainerMaterial(Material material) {
        if (material == null) {
            return true;
        }
        return material == Material.BUNDLE || material.name().endsWith("SHULKER_BOX");
    }
}

package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * Fumo 编辑台通用的物品构建、文本与反馈工具。
 */
final class FumoGuiItems {

    private FumoGuiItems() {
    }

    static Component nonItalic(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    static Component nonItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    static Component describeSlotItem(ItemStack item) {
        if (item == null) {
            return nonItalic("空", NamedTextColor.DARK_GRAY);
        }
        return nonItalic(item.effectiveName().colorIfAbsent(NamedTextColor.WHITE));
    }

    static ItemStack createFiller(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static ItemStack createButton(Material material, Component title, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(title);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    static void playSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    static void playDeny(Player player) {
        playSound(player, Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
    }

    static void playEquip(Player player) {
        playSound(player, Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 1.1f);
    }

    /**
     * 发送提示消息并播放拒绝音效。
     */
    static void deny(Player player, String message, NamedTextColor color) {
        player.sendMessage(nonItalic(message, color));
        playDeny(player);
    }
}

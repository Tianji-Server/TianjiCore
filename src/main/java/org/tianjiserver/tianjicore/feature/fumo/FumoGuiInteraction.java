package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.function.Consumer;

import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.giveOrDrop;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.playDeny;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.playEquip;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.playSound;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.ARMOR_IMPORT_MAPPINGS;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.ArmorMapping;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.GUI_SIZE;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.HAND_SLOT;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.INPUT_SLOTS;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.OFF_HAND_SLOT;

/**
 * 处理十字架装备槽的放置/拖拽交互，以及交换主副手、导入护甲、清空等快捷操作。
 */
final class FumoGuiInteraction {

    private final FumoGuiSlots slots;
    private final FumoGuiRenderer renderer;
    private final Consumer<FumoGuiHolder> postClickSync;

    /**
     * @param postClickSync 交由原版处理的点击/拖拽完成后，下一 tick 同步占位符与面板的回调
     */
    FumoGuiInteraction(FumoGuiSlots slots, FumoGuiRenderer renderer, Consumer<FumoGuiHolder> postClickSync) {
        this.slots = slots;
        this.renderer = renderer;
        this.postClickSync = postClickSync;
    }

    // ---------------------------------------------------------------- 槽位交互

    void handlePlayerInventoryClick(InventoryClickEvent event, Player player, FumoGuiHolder holder) {
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        if (!event.isShiftClick()) {
            return;
        }

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.isEmpty()) {
            return;
        }
        if (!slots.isAllowedEquipment(clickedItem)) {
            slots.rejectEquipment(player);
            return;
        }

        int targetSlot = slots.findSmartTargetSlot(holder.inventory(), clickedItem);
        if (targetSlot == -1) {
            FumoGuiItems.deny(player, "玩偶装备槽位已满！", NamedTextColor.YELLOW);
            return;
        }

        holder.inventory().setItem(targetSlot, clickedItem.clone());
        event.setCurrentItem(null);
        onPlaced(player, holder);
    }

    void handleCrossSlotClick(int rawSlot, InventoryClickEvent event, Player player, FumoGuiHolder holder) {
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        if (!slots.isRealItem(holder.inventory().getItem(rawSlot))) {
            handleEmptyCrossSlotClick(rawSlot, event, player, holder);
            return;
        }

        if (!slots.isAllowedEquipment(resolveIncomingItem(event, player))) {
            event.setCancelled(true);
            slots.rejectEquipment(player);
            return;
        }

        postClickSync.accept(holder);
    }

    void handleDrag(InventoryDragEvent event, FumoGuiHolder holder) {
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < GUI_SIZE);
        if (!touchesTop) {
            return;
        }

        boolean invalidOrPlaceholder = event.getRawSlots().stream()
                .filter(slot -> slot < GUI_SIZE)
                .anyMatch(slot -> !FumoGuiLayout.isCrossSlot(slot)
                        || slots.isPlaceholder(holder.inventory().getItem(slot)));
        boolean allowedCursor = slots.isAllowedEquipment(event.getOldCursor());
        if (invalidOrPlaceholder || !allowedCursor) {
            event.setCancelled(true);
            if (!allowedCursor && event.getWhoClicked() instanceof Player player) {
                slots.rejectEquipment(player);
            }
            return;
        }

        postClickSync.accept(holder);
    }

    /**
     * 空槽位上显示的是占位符，原版交互会把占位符换到光标上，因此统一取消事件并手动放置物品。
     */
    private void handleEmptyCrossSlotClick(int rawSlot, InventoryClickEvent event, Player player, FumoGuiHolder holder) {
        event.setCancelled(true);
        Inventory inventory = holder.inventory();
        PlayerInventory playerInventory = player.getInventory();

        switch (event.getClick()) {
            case LEFT -> {
                ItemStack cursor = event.getCursor();
                if (canPlace(cursor, player)) {
                    inventory.setItem(rawSlot, cursor.clone());
                    event.getView().setCursor(null);
                    onPlaced(player, holder);
                }
            }
            case RIGHT -> {
                ItemStack cursor = event.getCursor();
                if (canPlace(cursor, player)) {
                    inventory.setItem(rawSlot, cursor.asOne());
                    event.getView().setCursor(cursor.getAmount() <= 1 ? null : cursor.asQuantity(cursor.getAmount() - 1));
                    onPlaced(player, holder);
                }
            }
            case NUMBER_KEY -> {
                int button = event.getHotbarButton();
                ItemStack hotbarItem = button >= 0 ? playerInventory.getItem(button) : null;
                if (canPlace(hotbarItem, player)) {
                    inventory.setItem(rawSlot, hotbarItem.clone());
                    playerInventory.setItem(button, null);
                    onPlaced(player, holder);
                }
            }
            case SWAP_OFFHAND -> {
                ItemStack offhand = playerInventory.getItemInOffHand();
                if (canPlace(offhand, player)) {
                    inventory.setItem(rawSlot, offhand.clone());
                    playerInventory.setItemInOffHand(null);
                    onPlaced(player, holder);
                }
            }
            default -> {
            }
        }
    }

    /**
     * 来源物品为空时静默忽略；不允许放入时提示玩家。
     */
    private boolean canPlace(ItemStack source, Player player) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        if (!slots.isAllowedEquipment(source)) {
            slots.rejectEquipment(player);
            return false;
        }
        return true;
    }

    private void onPlaced(Player player, FumoGuiHolder holder) {
        playEquip(player);
        renderer.refreshControlPanel(holder);
    }

    private static ItemStack resolveIncomingItem(InventoryClickEvent event, Player player) {
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            return button >= 0 ? player.getInventory().getItem(button) : null;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return player.getInventory().getItemInOffHand();
        }
        return event.getCursor();
    }

    // ---------------------------------------------------------------- 快捷操作

    void handleSwapHands(Player player, FumoGuiHolder holder) {
        Inventory inventory = holder.inventory();
        ItemStack handItem = slots.realItemOrNull(inventory.getItem(HAND_SLOT));
        ItemStack offHandItem = slots.realItemOrNull(inventory.getItem(OFF_HAND_SLOT));
        if (handItem == null && offHandItem == null) {
            playDeny(player);
            return;
        }

        slots.setCrossSlotItem(inventory, HAND_SLOT, offHandItem);
        slots.setCrossSlotItem(inventory, OFF_HAND_SLOT, handItem);
        renderer.refreshControlPanel(holder);
        playSound(player, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    void handleImportWornArmor(Player player, FumoGuiHolder holder) {
        Inventory guiInventory = holder.inventory();
        PlayerInventory playerInventory = player.getInventory();
        int imported = 0;

        for (ArmorMapping mapping : ARMOR_IMPORT_MAPPINGS) {
            if (slots.isRealItem(guiInventory.getItem(mapping.guiSlot()))) {
                continue;
            }
            ItemStack worn = playerInventory.getItem(mapping.armorSlot());
            if (worn == null || worn.isEmpty() || !slots.isAllowedEquipment(worn)) {
                continue;
            }

            guiInventory.setItem(mapping.guiSlot(), worn.clone());
            playerInventory.setItem(mapping.armorSlot(), null);
            imported++;
        }

        if (imported == 0) {
            FumoGuiItems.deny(player, "没有可导入的穿戴护甲（或对应槽位已有物品）。", NamedTextColor.YELLOW);
            return;
        }

        renderer.refreshControlPanel(holder);
        playSound(player, Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.1f);
    }

    void handleClearAll(Player player, FumoGuiHolder holder) {
        Inventory inventory = holder.inventory();
        int cleared = 0;
        for (int slotIndex : INPUT_SLOTS) {
            ItemStack item = slots.realItemOrNull(inventory.getItem(slotIndex));
            if (item != null) {
                slots.setCrossSlotItem(inventory, slotIndex, null);
                giveOrDrop(player, item);
                cleared++;
            }
        }

        if (cleared == 0) {
            playDeny(player);
            return;
        }

        renderer.refreshControlPanel(holder);
        playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.7f, 0.95f);
    }
}

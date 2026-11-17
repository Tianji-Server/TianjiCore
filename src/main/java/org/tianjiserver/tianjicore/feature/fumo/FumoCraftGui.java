package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理员专属 Fumo 玩偶制作界面。
 * 负责 6 槽装备输入、防嵌套容器校验、物品化生成以及关闭/模块卸载时的安全退物。
 */
public class FumoCraftGui {

    private static final int GUI_SIZE = 27;
    private static final int CONFIRM_SLOT = 17;
    private static final int CANCEL_SLOT = 26;
    private static final int INFO_SLOT = 8;

    private static final int[] INPUT_SLOTS = {9, 10, 11, 12, 14, 15};
    private static final Map<Integer, Integer> INPUT_SLOT_INDEX = Map.of(
            9, 0,
            10, 1,
            11, 2,
            12, 3,
            14, 4,
            15, 5
    );

    private final FumoItemCodec codec;
    private final Map<UUID, GuiHolder> activeSessions = new ConcurrentHashMap<>();

    public FumoCraftGui(FumoItemCodec codec) {
        this.codec = codec;
    }

    /**
     * 为目标管理员打开 Fumo 制作 GUI。
     */
    public void open(Player player, String rawName) {
        Component targetName = FumoItemCodec.resolveDisplayName(rawName);
        Component title = Component.text("玩偶制作台", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false);

        GuiHolder holder = new GuiHolder(player.getUniqueId(), targetName);
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.bindInventory(inventory);
        populateDecorations(inventory, targetName);

        activeSessions.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
    }

    /**
     * 模块停用或重载时关闭所有未完成的制作界面并退还物品。
     */
    public void shutdown() {
        for (GuiHolder holder : List.copyOf(activeSessions.values())) {
            Player player = Bukkit.getPlayer(holder.playerId());
            if (player != null) {
                refundAndUntrack(player, holder);
                if (player.getOpenInventory().getTopInventory().getHolder() == holder) {
                    player.closeInventory();
                }
            } else {
                activeSessions.remove(holder.playerId(), holder);
            }
        }
        activeSessions.clear();
    }

    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }

        if (!clicked.equals(holder.inventory())) {
            if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot == CONFIRM_SLOT) {
            event.setCancelled(true);
            handleConfirm(player, holder);
            return;
        }
        if (rawSlot == CANCEL_SLOT) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        if (!INPUT_SLOT_INDEX.containsKey(rawSlot)) {
            event.setCancelled(true);
            return;
        }

        ItemStack incoming = resolveIncomingItem(event, player);
        if (!isAllowedEquipment(incoming)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("该物品不可放入玩偶装备槽！", NamedTextColor.RED));
        }
    }

    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder)) {
            return;
        }

        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < GUI_SIZE);
        if (!touchesTop) {
            return;
        }

        boolean invalidSlot = event.getRawSlots().stream()
                .filter(slot -> slot < GUI_SIZE)
                .anyMatch(slot -> !INPUT_SLOT_INDEX.containsKey(slot));
        if (invalidSlot || !isAllowedEquipment(event.getOldCursor())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && !isAllowedEquipment(event.getOldCursor())) {
                player.sendMessage(Component.text("该物品不可放入玩偶装备槽！", NamedTextColor.RED));
            }
        }
    }

    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            refundAndUntrack(player, holder);
        } else {
            activeSessions.remove(holder.playerId(), holder);
        }
    }

    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GuiHolder holder = activeSessions.get(player.getUniqueId());
        if (holder != null) {
            refundAndUntrack(player, holder);
        }
    }

    private void handleConfirm(Player player, GuiHolder holder) {
        ItemStack[] slots = new ItemStack[FumoItemCodec.FUMO_SLOTS.size()];
        boolean hasAnyItem = false;

        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            ItemStack item = holder.inventory().getItem(INPUT_SLOTS[i]);
            if (item == null || item.isEmpty()) {
                slots[i] = null;
                continue;
            }
            if (!isAllowedEquipment(item)) {
                player.sendMessage(Component.text("装备槽中包含禁止放入的物品，请先取出！", NamedTextColor.RED));
                return;
            }
            slots[i] = item.clone();
            hasAnyItem = true;
        }

        if (!hasAnyItem) {
            player.sendMessage(Component.text("请至少放入一件自定义装备后再制作！", NamedTextColor.RED));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("背包已满，请先腾出至少一个空位！", NamedTextColor.RED));
            return;
        }

        ItemStack fumoItem = codec.createFumoItem(slots, holder.targetDisplayName());
        clearInputSlots(holder.inventory());
        holder.markCompleted();
        activeSessions.remove(player.getUniqueId(), holder);

        giveOrDrop(player, fumoItem);
        player.closeInventory();
        player.sendMessage(Component.text("已制作玩偶: ", NamedTextColor.GREEN)
                .append(holder.targetDisplayName()));
    }

    private void refundAndUntrack(Player player, GuiHolder holder) {
        if (!activeSessions.remove(player.getUniqueId(), holder) || holder.completed()) {
            return;
        }

        for (int slotIndex : INPUT_SLOTS) {
            ItemStack item = holder.inventory().getItem(slotIndex);
            if (item != null && !item.isEmpty()) {
                holder.inventory().setItem(slotIndex, null);
                giveOrDrop(player, item);
            }
        }
    }

    private boolean isAllowedEquipment(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return true;
        }
        if (isBlockedContainerMaterial(item.getType()) || codec.isFumoItem(item)) {
            return false;
        }
        if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.hasBlockState()
                && blockStateMeta.getBlockState() instanceof Container) {
            return false;
        }
        return true;
    }

    private static boolean isBlockedContainerMaterial(Material material) {
        if (material == null) {
            return true;
        }
        return material == Material.BUNDLE || material.name().endsWith("SHULKER_BOX");
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

    private static void clearInputSlots(Inventory inventory) {
        for (int slotIndex : INPUT_SLOTS) {
            inventory.setItem(slotIndex, null);
        }
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    private static void populateDecorations(Inventory inventory, Component targetName) {
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < GUI_SIZE; i++) {
            if (!INPUT_SLOT_INDEX.containsKey(i)) {
                inventory.setItem(i, filler);
            }
        }

        inventory.setItem(0, createSlotGuide(Material.LEATHER_HELMET, "头部装备槽", "放入下方第 1 格"));
        inventory.setItem(1, createSlotGuide(Material.LEATHER_CHESTPLATE, "胸甲装备槽", "放入下方第 2 格"));
        inventory.setItem(2, createSlotGuide(Material.LEATHER_LEGGINGS, "护腿装备槽", "放入下方第 3 格"));
        inventory.setItem(3, createSlotGuide(Material.LEATHER_BOOTS, "靴子装备槽", "放入下方第 4 格"));
        inventory.setItem(5, createSlotGuide(Material.IRON_SWORD, "主手物品槽", "放入下方第 5 格"));
        inventory.setItem(6, createSlotGuide(Material.SHIELD, "副手物品槽", "放入下方第 6 格"));

        inventory.setItem(INFO_SLOT, createButton(
                Material.ARMOR_STAND,
                Component.text("当前玩偶名称", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                List.of(
                        targetName.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE),
                        Component.text("仅在物品上显示，放置后不显示悬浮名", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                )
        ));

        inventory.setItem(CONFIRM_SLOT, createButton(
                Material.LIME_CONCRETE,
                Component.text("确认制作玩偶", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("点击打包左侧 6 槽装备并生成玩偶物品", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
        ));

        inventory.setItem(CANCEL_SLOT, createButton(
                Material.RED_CONCRETE,
                Component.text("取消制作", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("关闭界面并退还槽内物品", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
        ));
    }

    private static ItemStack createSlotGuide(Material material, String title, String hint) {
        return createButton(
                material,
                Component.text(title, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text(hint, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
        );
    }

    private static ItemStack createButton(Material material, Component title, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(title);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static final class GuiHolder implements InventoryHolder {
        private final UUID playerId;
        private final Component targetDisplayName;
        private Inventory inventory;
        private boolean completed;

        private GuiHolder(UUID playerId, Component targetDisplayName) {
            this.playerId = playerId;
            this.targetDisplayName = targetDisplayName;
        }

        private void bindInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private UUID playerId() {
            return playerId;
        }

        private Component targetDisplayName() {
            return targetDisplayName;
        }

        private Inventory inventory() {
            return inventory;
        }

        private boolean completed() {
            return completed;
        }

        private void markCompleted() {
            this.completed = true;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

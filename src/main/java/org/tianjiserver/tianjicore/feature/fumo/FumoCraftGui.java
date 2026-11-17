package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理员专属 Fumo 玩偶编辑与制作界面。
 * 采用 6x9 双箱布局：左侧以十字架形态展示 6 个装备/物品槽位，右侧集中放置信息预览与快捷操作按钮。
 */
public class FumoCraftGui {

    private static final int GUI_SIZE = 54;

    private static final int HEAD_SLOT = 11;
    private static final int HAND_SLOT = 19;
    private static final int CHEST_SLOT = 20;
    private static final int OFF_HAND_SLOT = 21;
    private static final int LEGS_SLOT = 29;
    private static final int FEET_SLOT = 38;

    private static final int INFO_SLOT = 15;
    private static final int DERIVATIVE_NOTICE_SLOT = 16;
    private static final int GUIDE_SLOT = 17;
    private static final int SWAP_HANDS_SLOT = 24;
    private static final int IMPORT_ARMOR_SLOT = 25;
    private static final int CLEAR_ALL_SLOT = 26;
    private static final int CONFIRM_SLOT = 42;
    private static final int CANCEL_SLOT = 44;

    private static final Set<Integer> DIVIDER_SLOTS = Set.of(5, 14, 23, 32, 41, 50);

    /**
     * 顺序与 {@link FumoItemCodec#FUMO_SLOTS} 一一对应。
     */
    private static final int[] INPUT_SLOTS = {
            HEAD_SLOT,
            CHEST_SLOT,
            LEGS_SLOT,
            FEET_SLOT,
            HAND_SLOT,
            OFF_HAND_SLOT
    };

    private static final List<SlotSpec> CROSS_SLOTS = List.of(
            new SlotSpec(HEAD_SLOT, EquipmentSlot.HEAD, "头部栏位", "位于十字架上方 · 戴在玩偶头顶"),
            new SlotSpec(HAND_SLOT, EquipmentSlot.HAND, "主手栏位", "位于十字架左侧 · 对应玩偶右手"),
            new SlotSpec(CHEST_SLOT, EquipmentSlot.CHEST, "胸甲栏位", "位于十字架中央 · 穿在玩偶躯干"),
            new SlotSpec(OFF_HAND_SLOT, EquipmentSlot.OFF_HAND, "副手栏位", "位于十字架右侧 · 对应玩偶左手"),
            new SlotSpec(LEGS_SLOT, EquipmentSlot.LEGS, "护腿栏位", "位于十字架中下 · 穿在玩偶腿部"),
            new SlotSpec(FEET_SLOT, EquipmentSlot.FEET, "靴子栏位", "位于十字架底部 · 穿在玩偶脚部")
    );

    private static final Map<Integer, SlotSpec> CROSS_SLOT_MAP = Map.of(
            HEAD_SLOT, CROSS_SLOTS.get(0),
            HAND_SLOT, CROSS_SLOTS.get(1),
            CHEST_SLOT, CROSS_SLOTS.get(2),
            OFF_HAND_SLOT, CROSS_SLOTS.get(3),
            LEGS_SLOT, CROSS_SLOTS.get(4),
            FEET_SLOT, CROSS_SLOTS.get(5)
    );

    private static final EquipmentSlot[] ARMOR_IMPORT_ORDER = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private static final int[] ARMOR_IMPORT_SLOTS = {
            HEAD_SLOT,
            CHEST_SLOT,
            LEGS_SLOT,
            FEET_SLOT
    };

    private final Plugin plugin;
    private final FumoItemCodec codec;
    private final NamespacedKey placeholderKey;
    private final Map<UUID, GuiHolder> activeSessions = new ConcurrentHashMap<>();

    public FumoCraftGui(Plugin plugin, FumoItemCodec codec) {
        this.plugin = plugin;
        this.codec = codec;
        this.placeholderKey = new NamespacedKey(plugin, "fumo_gui_placeholder");
    }

    /**
     * 为目标管理员打开 Fumo 玩偶编辑台。
     */
    public void open(Player player, String rawName) {
        Component targetName = FumoItemCodec.resolveDisplayName(rawName);
        Component title = nonItalic("玩偶编辑台", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD);

        GuiHolder holder = new GuiHolder(player.getUniqueId(), targetName);
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.bindInventory(inventory);
        populateLayout(holder);

        activeSessions.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
    }

    /**
     * 模块停用或重载时关闭所有未完成的编辑界面并退还物品。
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
            handlePlayerInventoryClick(event, player, holder);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (handleActionButtonClick(rawSlot, event, player, holder)) {
            return;
        }
        if (!CROSS_SLOT_MAP.containsKey(rawSlot)) {
            event.setCancelled(true);
            return;
        }
        handleCrossSlotClick(rawSlot, event, player, holder);
    }

    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }

        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < GUI_SIZE);
        if (!touchesTop) {
            return;
        }

        boolean invalidOrPlaceholder = event.getRawSlots().stream()
                .filter(slot -> slot < GUI_SIZE)
                .anyMatch(slot -> !CROSS_SLOT_MAP.containsKey(slot)
                        || isPlaceholder(holder.inventory().getItem(slot)));
        if (invalidOrPlaceholder || !isAllowedEquipment(event.getOldCursor())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && !isAllowedEquipment(event.getOldCursor())) {
                rejectEquipment(player);
            }
            return;
        }

        schedulePostClickSync(holder);
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

    private void handlePlayerInventoryClick(InventoryClickEvent event, Player player, GuiHolder holder) {
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
        if (!isAllowedEquipment(clickedItem)) {
            rejectEquipment(player);
            return;
        }

        int targetSlot = findSmartTargetSlot(holder.inventory(), clickedItem);
        if (targetSlot == -1) {
            player.sendMessage(nonItalic("玩偶装备槽位已满！", NamedTextColor.YELLOW));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        holder.inventory().setItem(targetSlot, clickedItem.clone());
        event.setCurrentItem(null);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 1.1f);
        refreshControlPanel(holder);
    }

    private boolean handleActionButtonClick(
            int rawSlot,
            InventoryClickEvent event,
            Player player,
            GuiHolder holder
    ) {
        switch (rawSlot) {
            case INFO_SLOT, GUIDE_SLOT -> {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
                return true;
            }
            case DERIVATIVE_NOTICE_SLOT -> {
                event.setCancelled(true);
                holder.toggleDerivativeNotice();
                refreshControlPanel(holder);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                return true;
            }
            case SWAP_HANDS_SLOT -> {
                event.setCancelled(true);
                handleSwapHands(player, holder);
                return true;
            }
            case IMPORT_ARMOR_SLOT -> {
                event.setCancelled(true);
                handleImportWornArmor(player, holder);
                return true;
            }
            case CLEAR_ALL_SLOT -> {
                event.setCancelled(true);
                handleClearAll(player, holder);
                return true;
            }
            case CONFIRM_SLOT -> {
                event.setCancelled(true);
                handleConfirm(player, holder);
                return true;
            }
            case CANCEL_SLOT -> {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
                player.closeInventory();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void handleCrossSlotClick(
            int rawSlot,
            InventoryClickEvent event,
            Player player,
            GuiHolder holder
    ) {
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        ItemStack current = holder.inventory().getItem(rawSlot);
        if (!isRealItem(current)) {
            handleEmptyCrossSlotClick(rawSlot, event, player, holder);
            return;
        }

        ItemStack incoming = resolveIncomingItem(event, player);
        if (!isAllowedEquipment(incoming)) {
            event.setCancelled(true);
            rejectEquipment(player);
            return;
        }

        schedulePostClickSync(holder);
    }

    private void handleEmptyCrossSlotClick(
            int rawSlot,
            InventoryClickEvent event,
            Player player,
            GuiHolder holder
    ) {
        event.setCancelled(true);
        ClickType click = event.getClick();

        if (click == ClickType.LEFT || click == ClickType.RIGHT) {
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.isEmpty()) {
                return;
            }
            if (!isAllowedEquipment(cursor)) {
                rejectEquipment(player);
                return;
            }

            if (click == ClickType.LEFT) {
                holder.inventory().setItem(rawSlot, cursor.clone());
                event.getView().setCursor(null);
            } else {
                holder.inventory().setItem(rawSlot, cursor.asOne());
                if (cursor.getAmount() <= 1) {
                    event.getView().setCursor(null);
                } else {
                    ItemStack remaining = cursor.clone();
                    remaining.setAmount(cursor.getAmount() - 1);
                    event.getView().setCursor(remaining);
                }
            }
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 1.1f);
            refreshControlPanel(holder);
            return;
        }

        if (click == ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            ItemStack hotbarItem = button >= 0 ? player.getInventory().getItem(button) : null;
            if (hotbarItem == null || hotbarItem.isEmpty()) {
                return;
            }
            if (!isAllowedEquipment(hotbarItem)) {
                rejectEquipment(player);
                return;
            }
            holder.inventory().setItem(rawSlot, hotbarItem.clone());
            player.getInventory().setItem(button, null);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 1.1f);
            refreshControlPanel(holder);
            return;
        }

        if (click == ClickType.SWAP_OFFHAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand.isEmpty()) {
                return;
            }
            if (!isAllowedEquipment(offhand)) {
                rejectEquipment(player);
                return;
            }
            holder.inventory().setItem(rawSlot, offhand.clone());
            player.getInventory().setItemInOffHand(null);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 1.1f);
            refreshControlPanel(holder);
        }
    }

    private void handleSwapHands(Player player, GuiHolder holder) {
        Inventory inventory = holder.inventory();
        ItemStack handItem = realItemOrNull(inventory.getItem(HAND_SLOT));
        ItemStack offHandItem = realItemOrNull(inventory.getItem(OFF_HAND_SLOT));
        if (handItem == null && offHandItem == null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        setCrossSlotItem(inventory, HAND_SLOT, offHandItem);
        setCrossSlotItem(inventory, OFF_HAND_SLOT, handItem);
        refreshControlPanel(holder);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    private void handleImportWornArmor(Player player, GuiHolder holder) {
        Inventory guiInventory = holder.inventory();
        PlayerInventory playerInventory = player.getInventory();
        int imported = 0;

        for (int i = 0; i < ARMOR_IMPORT_ORDER.length; i++) {
            int guiSlot = ARMOR_IMPORT_SLOTS[i];
            if (isRealItem(guiInventory.getItem(guiSlot))) {
                continue;
            }
            EquipmentSlot armorSlot = ARMOR_IMPORT_ORDER[i];
            ItemStack worn = playerInventory.getItem(armorSlot);
            if (worn == null || worn.isEmpty() || !isAllowedEquipment(worn)) {
                continue;
            }

            guiInventory.setItem(guiSlot, worn.clone());
            playerInventory.setItem(armorSlot, null);
            imported++;
        }

        if (imported == 0) {
            player.sendMessage(nonItalic("没有可导入的穿戴护甲（或对应槽位已有物品）。", NamedTextColor.YELLOW));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        refreshControlPanel(holder);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.1f);
    }

    private void handleClearAll(Player player, GuiHolder holder) {
        Inventory inventory = holder.inventory();
        int cleared = 0;
        for (int slotIndex : INPUT_SLOTS) {
            ItemStack item = realItemOrNull(inventory.getItem(slotIndex));
            if (item != null) {
                setCrossSlotItem(inventory, slotIndex, null);
                giveOrDrop(player, item);
                cleared++;
            }
        }

        if (cleared == 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        refreshControlPanel(holder);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 0.95f);
    }

    private void handleConfirm(Player player, GuiHolder holder) {
        ItemStack[] slots = new ItemStack[FumoItemCodec.FUMO_SLOTS.size()];
        boolean hasAnyItem = false;

        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            ItemStack item = realItemOrNull(holder.inventory().getItem(INPUT_SLOTS[i]));
            if (item == null) {
                slots[i] = null;
                continue;
            }
            if (!isAllowedEquipment(item)) {
                player.sendMessage(nonItalic("装备槽中包含禁止放入的物品，请先取出！", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                return;
            }
            slots[i] = item.clone();
            hasAnyItem = true;
        }

        if (!hasAnyItem) {
            player.sendMessage(nonItalic("请至少放入一件自定义装备后再制作！", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(nonItalic("背包已满，请先腾出至少一个空位！", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        ItemStack fumoItem = codec.createFumoItem(
                slots,
                holder.targetDisplayName(),
                holder.derivativeNoticeEnabled()
        );
        clearInputSlots(holder.inventory());
        holder.markCompleted();
        activeSessions.remove(player.getUniqueId(), holder);

        giveOrDrop(player, fumoItem);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.15f);
        player.closeInventory();
        player.sendMessage(nonItalic("已制作玩偶: ", NamedTextColor.GREEN)
                .append(holder.targetDisplayName()));
    }

    private void refundAndUntrack(Player player, GuiHolder holder) {
        if (!activeSessions.remove(player.getUniqueId(), holder) || holder.completed()) {
            return;
        }

        for (int slotIndex : INPUT_SLOTS) {
            ItemStack item = realItemOrNull(holder.inventory().getItem(slotIndex));
            if (item != null) {
                holder.inventory().setItem(slotIndex, null);
                giveOrDrop(player, item);
            }
        }
    }

    private void schedulePostClickSync(GuiHolder holder) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (activeSessions.get(holder.playerId()) != holder) {
                return;
            }
            restoreEmptyPlaceholders(holder.inventory());
            refreshControlPanel(holder);
        });
    }

    private void restoreEmptyPlaceholders(Inventory inventory) {
        for (SlotSpec spec : CROSS_SLOTS) {
            ItemStack current = inventory.getItem(spec.slot());
            if (current == null || current.isEmpty()) {
                inventory.setItem(spec.slot(), createPlaceholderItem(spec));
            }
        }
    }

    private void setCrossSlotItem(Inventory inventory, int slot, ItemStack item) {
        if (item == null || item.isEmpty()) {
            SlotSpec spec = CROSS_SLOT_MAP.get(slot);
            inventory.setItem(slot, spec != null ? createPlaceholderItem(spec) : null);
            return;
        }
        inventory.setItem(slot, item);
    }

    private int findSmartTargetSlot(Inventory inventory, ItemStack item) {
        EquipmentSlot preferred = item.getType().getEquipmentSlot();
        for (SlotSpec spec : CROSS_SLOTS) {
            if (spec.equipmentSlot() == preferred && !isRealItem(inventory.getItem(spec.slot()))) {
                return spec.slot();
            }
        }

        int[] fallbackOrder = {HAND_SLOT, OFF_HAND_SLOT, HEAD_SLOT, CHEST_SLOT, LEGS_SLOT, FEET_SLOT};
        for (int slot : fallbackOrder) {
            if (!isRealItem(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private void populateLayout(GuiHolder holder) {
        Inventory inventory = holder.inventory();
        ItemStack background = createFiller(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack divider = createFiller(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < GUI_SIZE; i++) {
            if (CROSS_SLOT_MAP.containsKey(i)) {
                inventory.setItem(i, createPlaceholderItem(CROSS_SLOT_MAP.get(i)));
            } else if (DIVIDER_SLOTS.contains(i)) {
                inventory.setItem(i, divider);
            } else {
                inventory.setItem(i, background);
            }
        }

        inventory.setItem(GUIDE_SLOT, createGuideButton());
        inventory.setItem(CANCEL_SLOT, createCancelButton());
        refreshControlPanel(holder);
    }

    private void refreshControlPanel(GuiHolder holder) {
        Inventory inventory = holder.inventory();
        int equippedCount = countEquippedSlots(inventory);

        inventory.setItem(INFO_SLOT, createInfoButton(holder, equippedCount));
        inventory.setItem(DERIVATIVE_NOTICE_SLOT, createDerivativeNoticeButton(holder.derivativeNoticeEnabled()));
        inventory.setItem(SWAP_HANDS_SLOT, createSwapHandsButton(inventory));
        inventory.setItem(IMPORT_ARMOR_SLOT, createImportArmorButton(inventory));
        inventory.setItem(CLEAR_ALL_SLOT, createClearAllButton(equippedCount));
        inventory.setItem(CONFIRM_SLOT, createConfirmButton(holder, equippedCount));
    }

    private int countEquippedSlots(Inventory inventory) {
        int count = 0;
        for (int slot : INPUT_SLOTS) {
            if (isRealItem(inventory.getItem(slot))) {
                count++;
            }
        }
        return count;
    }

    private ItemStack createPlaceholderItem(SlotSpec spec) {
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

    private ItemStack createInfoButton(GuiHolder holder, int equippedCount) {
        Inventory inventory = holder.inventory();
        NamedTextColor countColor = equippedCount == 0
                ? NamedTextColor.GRAY
                : (equippedCount == INPUT_SLOTS.length ? NamedTextColor.GREEN : NamedTextColor.AQUA);

        List<Component> lore = new ArrayList<>();
        lore.add(nonItalic("目标名称: ", NamedTextColor.GRAY)
                .append(holder.targetDisplayName().decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
        lore.add(nonItalic("装配进度: ", NamedTextColor.GRAY)
                .append(nonItalic(equippedCount + " / " + INPUT_SLOTS.length + " 槽", countColor)));
        lore.add(nonItalic("────────────────", NamedTextColor.DARK_GRAY));

        for (SlotSpec spec : CROSS_SLOTS) {
            ItemStack equipped = realItemOrNull(inventory.getItem(spec.slot()));
            lore.add(nonItalic(spec.shortLabel() + ": ", NamedTextColor.GRAY).append(describeSlotItem(equipped)));
        }

        lore.add(nonItalic("────────────────", NamedTextColor.DARK_GRAY));
        lore.add(nonItalic("名称仅在物品上显示，放置后不展示悬浮名", NamedTextColor.DARK_GRAY));

        return createButton(
                Material.ARMOR_STAND,
                nonItalic("玩偶概览", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                lore,
                false
        );
    }

    private ItemStack createDerivativeNoticeButton(boolean enabled) {
        if (enabled) {
            List<Component> lore = new java.util.ArrayList<>();
            lore.add(nonItalic("点击切换为关闭", NamedTextColor.YELLOW));
            lore.add(nonItalic("物品底部将附带:", NamedTextColor.DARK_GRAY));
            for (String line : FumoItemCodec.DERIVATIVE_NOTICE_LINES) {
                lore.add(Component.text(line, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, true));
            }
            return createButton(
                    Material.LIME_DYE,
                    nonItalic("二创声明: 开启", NamedTextColor.GREEN),
                    lore,
                    true
            );
        }
        return createButton(
                Material.GRAY_DYE,
                nonItalic("二创声明: 关闭", NamedTextColor.GRAY),
                List.of(
                        nonItalic("点击切换为开启", NamedTextColor.YELLOW),
                        nonItalic("当前不在物品底部写入二创声明", NamedTextColor.DARK_GRAY)
                ),
                false
        );
    }

    private ItemStack createGuideButton() {
        return createButton(
                Material.BOOK,
                nonItalic("操作说明", NamedTextColor.AQUA),
                List.of(
                        nonItalic("左侧十字架对应玩偶身体朝向:", NamedTextColor.GRAY),
                        nonItalic("  上: 头部 · 中: 胸甲 · 下: 护腿/靴子", NamedTextColor.WHITE),
                        nonItalic("  左: 主手 · 右: 副手", NamedTextColor.WHITE),
                        Component.empty(),
                        nonItalic("• 放置后潜行 + 左键可回收玩偶", NamedTextColor.GRAY),
                        nonItalic("• 禁止放入潜影盒、收纳袋或玩偶本身", NamedTextColor.DARK_GRAY)
                ),
                false
        );
    }

    private ItemStack createSwapHandsButton(Inventory inventory) {
        ItemStack hand = realItemOrNull(inventory.getItem(HAND_SLOT));
        ItemStack offHand = realItemOrNull(inventory.getItem(OFF_HAND_SLOT));
        boolean hasAny = hand != null || offHand != null;

        List<Component> lore = List.of(
                nonItalic("主手: ", NamedTextColor.GRAY).append(describeSlotItem(hand)),
                nonItalic("副手: ", NamedTextColor.GRAY).append(describeSlotItem(offHand)),
                Component.empty(),
                nonItalic(hasAny ? "点击互换左右手臂槽位物品" : "主手与副手均为空", hasAny ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
        );
        return createButton(
                Material.REPEATER,
                nonItalic("交换主副手", hasAny ? NamedTextColor.GOLD : NamedTextColor.GRAY),
                lore,
                false
        );
    }

    private ItemStack createImportArmorButton(Inventory inventory) {
        int emptyArmorSlots = 0;
        for (int slot : ARMOR_IMPORT_SLOTS) {
            if (!isRealItem(inventory.getItem(slot))) {
                emptyArmorSlots++;
            }
        }
        return createButton(
                Material.HOPPER,
                nonItalic("导入穿戴护甲", NamedTextColor.AQUA),
                List.of(
                        nonItalic("将身上穿戴的头/胸/腿/靴", NamedTextColor.GRAY),
                        nonItalic("自动移入左侧对应的空槽位", NamedTextColor.GRAY),
                        Component.empty(),
                        nonItalic("当前可用护甲空槽: " + emptyArmorSlots + " / 4", NamedTextColor.YELLOW)
                ),
                false
        );
    }

    private ItemStack createClearAllButton(int equippedCount) {
        boolean hasItems = equippedCount > 0;
        return createButton(
                Material.BRUSH,
                nonItalic("清空全部装备", hasItems ? NamedTextColor.GOLD : NamedTextColor.GRAY),
                List.of(
                        nonItalic(
                                hasItems ? "点击退还左侧 " + equippedCount + " 个槽位装备" : "当前没有已放入的装备",
                                hasItems ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY
                        ),
                        nonItalic("退还物品将直接回到你的背包", NamedTextColor.GRAY)
                ),
                false
        );
    }

    private ItemStack createConfirmButton(GuiHolder holder, int equippedCount) {
        if (equippedCount <= 0) {
            return createButton(
                    Material.GRAY_CONCRETE,
                    nonItalic("确认制作玩偶 (需放入装备)", NamedTextColor.GRAY),
                    List.of(
                            nonItalic("请先在左侧十字架放入至少 1 件装备", NamedTextColor.DARK_GRAY)
                    ),
                    false
            );
        }

        return createButton(
                Material.LIME_CONCRETE,
                nonItalic("确认制作玩偶", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(
                        nonItalic("打包 ", NamedTextColor.GRAY)
                                .append(nonItalic(equippedCount + " / " + INPUT_SLOTS.length, NamedTextColor.GREEN))
                                .append(nonItalic(" 槽装备并生成: ", NamedTextColor.GRAY))
                                .append(holder.targetDisplayName().decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)),
                        nonItalic("点击立即制作", NamedTextColor.YELLOW)
                ),
                true
        );
    }

    private static ItemStack createCancelButton() {
        return createButton(
                Material.RED_CONCRETE,
                nonItalic("取消制作", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                List.of(nonItalic("关闭编辑台并退还全部装备", NamedTextColor.GRAY)),
                false
        );
    }

    private Component describeSlotItem(ItemStack item) {
        if (item == null) {
            return nonItalic("空", NamedTextColor.DARK_GRAY);
        }
        return item.effectiveName()
                .colorIfAbsent(NamedTextColor.WHITE)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private boolean isRealItem(ItemStack item) {
        return item != null && !item.isEmpty() && !isPlaceholder(item);
    }

    private ItemStack realItemOrNull(ItemStack item) {
        return isRealItem(item) ? item : null;
    }

    private boolean isPlaceholder(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(placeholderKey, PersistentDataType.BYTE);
    }

    private boolean isAllowedEquipment(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return true;
        }
        if (isBlockedContainerMaterial(item.getType()) || codec.isFumoItem(item) || isPlaceholder(item)) {
            return false;
        }
        if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.hasBlockState()
                && blockStateMeta.getBlockState() instanceof Container) {
            return false;
        }
        return true;
    }

    private void rejectEquipment(Player player) {
        player.sendMessage(nonItalic("该物品不可放入玩偶装备槽！", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
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

    private static ItemStack createFiller(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack createButton(
            Material material,
            Component title,
            List<Component> lore,
            boolean glint
    ) {
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

    private static Component nonItalic(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private record SlotSpec(int slot, EquipmentSlot equipmentSlot, String label, String positionHint) {
        private String shortLabel() {
            return label.substring(0, 2);
        }
    }

    private static final class GuiHolder implements InventoryHolder {
        private final UUID playerId;
        private final Component targetDisplayName;
        private Inventory inventory;
        private boolean derivativeNoticeEnabled = true;
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

        private boolean derivativeNoticeEnabled() {
            return derivativeNoticeEnabled;
        }

        private void toggleDerivativeNotice() {
            this.derivativeNoticeEnabled = !this.derivativeNoticeEnabled;
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

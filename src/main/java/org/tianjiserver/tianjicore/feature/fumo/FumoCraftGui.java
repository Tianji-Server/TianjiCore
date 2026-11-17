package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.giveOrDrop;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.nonItalic;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.playSound;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.*;

/**
 * 管理员专属 Fumo 玩偶编辑与制作界面。
 * 负责会话生命周期、事件分发、制作确认与物品退还；
 * 布局见 {@link FumoGuiLayout}，渲染见 {@link FumoGuiRenderer}，槽位交互见 {@link FumoGuiInteraction}。
 */
public class FumoCraftGui {

    private final Plugin plugin;
    private final FumoItemCodec codec;
    private final FumoGuiSlots slots;
    private final FumoGuiRenderer renderer;
    private final FumoGuiInteraction interaction;
    private final Map<UUID, FumoGuiHolder> activeSessions = new ConcurrentHashMap<>();

    public FumoCraftGui(Plugin plugin, FumoItemCodec codec) {
        this.plugin = plugin;
        this.codec = codec;
        this.slots = new FumoGuiSlots(plugin, codec);
        this.renderer = new FumoGuiRenderer(slots);
        this.interaction = new FumoGuiInteraction(slots, renderer, this::schedulePostClickSync);
    }

    /**
     * 为目标管理员打开 Fumo 玩偶编辑台。
     */
    public void open(Player player, String rawName) {
        Component targetName = FumoItemCodec.resolveDisplayName(rawName);
        Component title = nonItalic("玩偶编辑台", NamedTextColor.GOLD).decorate(TextDecoration.BOLD);

        FumoGuiHolder holder = new FumoGuiHolder(player.getUniqueId(), targetName);
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.bindInventory(inventory);
        renderer.populateLayout(holder);

        activeSessions.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
    }

    /**
     * 模块停用或重载时关闭所有未完成的编辑界面并退还物品。
     */
    public void shutdown() {
        for (FumoGuiHolder holder : List.copyOf(activeSessions.values())) {
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
        if (!(event.getView().getTopInventory().getHolder() instanceof FumoGuiHolder holder)) {
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
            interaction.handlePlayerInventoryClick(event, player, holder);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (isCrossSlot(rawSlot)) {
            interaction.handleCrossSlotClick(rawSlot, event, player, holder);
            return;
        }

        event.setCancelled(true);
        handleButtonClick(rawSlot, player, holder);
    }

    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FumoGuiHolder holder) {
            interaction.handleDrag(event, holder);
        }
    }

    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof FumoGuiHolder holder)) {
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
        FumoGuiHolder holder = activeSessions.get(player.getUniqueId());
        if (holder != null) {
            refundAndUntrack(player, holder);
        }
    }

    /**
     * 处理右侧控制面板及装饰槽位的点击，事件已由调用方取消。
     */
    private void handleButtonClick(int rawSlot, Player player, FumoGuiHolder holder) {
        switch (rawSlot) {
            case INFO_SLOT, GUIDE_SLOT -> playSound(player, Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
            case DERIVATIVE_NOTICE_SLOT -> {
                holder.toggleDerivativeNotice();
                renderer.refreshControlPanel(holder);
                playSound(player, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            }
            case SWAP_HANDS_SLOT -> interaction.handleSwapHands(player, holder);
            case IMPORT_ARMOR_SLOT -> interaction.handleImportWornArmor(player, holder);
            case CLEAR_ALL_SLOT -> interaction.handleClearAll(player, holder);
            case CONFIRM_SLOT -> handleConfirm(player, holder);
            case CANCEL_SLOT -> {
                playSound(player, Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
                player.closeInventory();
            }
            default -> {
            }
        }
    }

    private void handleConfirm(Player player, FumoGuiHolder holder) {
        ItemStack[] equipment = new ItemStack[FumoItemCodec.FUMO_SLOTS.size()];
        boolean hasAnyItem = false;

        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            ItemStack item = slots.realItemOrNull(holder.inventory().getItem(INPUT_SLOTS[i]));
            if (item == null) {
                continue;
            }
            if (!slots.isAllowedEquipment(item)) {
                FumoGuiItems.deny(player, "装备槽中包含禁止放入的物品，请先取出！", NamedTextColor.RED);
                return;
            }
            equipment[i] = item.clone();
            hasAnyItem = true;
        }

        if (!hasAnyItem) {
            FumoGuiItems.deny(player, "请至少放入一件自定义装备后再制作！", NamedTextColor.RED);
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            FumoGuiItems.deny(player, "背包已满，请先腾出至少一个空位！", NamedTextColor.RED);
            return;
        }

        ItemStack fumoItem = codec.createFumoItem(
                equipment,
                holder.targetDisplayName(),
                holder.derivativeNoticeEnabled()
        );
        for (int slotIndex : INPUT_SLOTS) {
            holder.inventory().setItem(slotIndex, null);
        }
        holder.markCompleted();
        activeSessions.remove(player.getUniqueId(), holder);

        giveOrDrop(player, fumoItem);
        playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.15f);
        player.closeInventory();
        player.sendMessage(nonItalic("已制作玩偶: ", NamedTextColor.GREEN).append(holder.targetDisplayName()));
    }

    private void refundAndUntrack(Player player, FumoGuiHolder holder) {
        if (!activeSessions.remove(player.getUniqueId(), holder) || holder.completed()) {
            return;
        }

        for (int slotIndex : INPUT_SLOTS) {
            ItemStack item = slots.realItemOrNull(holder.inventory().getItem(slotIndex));
            if (item != null) {
                holder.inventory().setItem(slotIndex, null);
                giveOrDrop(player, item);
            }
        }
    }

    private void schedulePostClickSync(FumoGuiHolder holder) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (activeSessions.get(holder.playerId()) != holder) {
                return;
            }
            slots.restoreEmptyPlaceholders(holder.inventory());
            renderer.refreshControlPanel(holder);
        });
    }
}

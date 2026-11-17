package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.createButton;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.createFiller;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.describeSlotItem;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiItems.nonItalic;
import static org.tianjiserver.tianjicore.feature.fumo.FumoGuiLayout.*;

/**
 * 负责 Fumo 编辑台的背景布局与右侧控制面板按钮渲染。
 */
final class FumoGuiRenderer {

    private final FumoGuiSlots slots;

    FumoGuiRenderer(FumoGuiSlots slots) {
        this.slots = slots;
    }

    void populateLayout(FumoGuiHolder holder) {
        Inventory inventory = holder.inventory();
        ItemStack background = createFiller(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack divider = createFiller(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < GUI_SIZE; i++) {
            if (isCrossSlot(i)) {
                inventory.setItem(i, slots.createPlaceholderItem(crossSlot(i)));
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

    void refreshControlPanel(FumoGuiHolder holder) {
        Inventory inventory = holder.inventory();
        int equippedCount = slots.countEquippedSlots(inventory);

        inventory.setItem(INFO_SLOT, createInfoButton(holder, equippedCount));
        inventory.setItem(DERIVATIVE_NOTICE_SLOT, createDerivativeNoticeButton(holder.derivativeNoticeEnabled()));
        inventory.setItem(SWAP_HANDS_SLOT, createSwapHandsButton(inventory));
        inventory.setItem(IMPORT_ARMOR_SLOT, createImportArmorButton(inventory));
        inventory.setItem(CLEAR_ALL_SLOT, createClearAllButton(equippedCount));
        inventory.setItem(CONFIRM_SLOT, createConfirmButton(holder, equippedCount));
    }

    private ItemStack createInfoButton(FumoGuiHolder holder, int equippedCount) {
        Inventory inventory = holder.inventory();
        NamedTextColor countColor = equippedCount == 0
                ? NamedTextColor.GRAY
                : (equippedCount == INPUT_SLOTS.length ? NamedTextColor.GREEN : NamedTextColor.AQUA);

        List<Component> lore = new ArrayList<>();
        lore.add(nonItalic("目标名称: ", NamedTextColor.GRAY).append(nonItalic(holder.targetDisplayName())));
        lore.add(nonItalic("装配进度: ", NamedTextColor.GRAY)
                .append(nonItalic(equippedCount + " / " + INPUT_SLOTS.length + " 槽", countColor)));
        lore.add(nonItalic(FumoItemCodec.LORE_DIVIDER, NamedTextColor.DARK_GRAY));

        for (SlotSpec spec : CROSS_SLOTS) {
            ItemStack equipped = slots.realItemOrNull(inventory.getItem(spec.slot()));
            lore.add(nonItalic(spec.shortLabel() + ": ", NamedTextColor.GRAY).append(describeSlotItem(equipped)));
        }

        lore.add(nonItalic(FumoItemCodec.LORE_DIVIDER, NamedTextColor.DARK_GRAY));
        lore.add(nonItalic("名称仅在物品上显示，放置后不展示悬浮名", NamedTextColor.DARK_GRAY));

        return createButton(
                Material.ARMOR_STAND,
                nonItalic("玩偶概览", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                lore,
                false
        );
    }

    private static ItemStack createDerivativeNoticeButton(boolean enabled) {
        if (!enabled) {
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

        List<Component> lore = new ArrayList<>();
        lore.add(nonItalic("点击切换为关闭", NamedTextColor.YELLOW));
        lore.add(nonItalic("物品底部将附带:", NamedTextColor.DARK_GRAY));
        for (String line : FumoItemCodec.DERIVATIVE_NOTICE_LINES) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        }
        return createButton(
                Material.LIME_DYE,
                nonItalic("二创声明: 开启", NamedTextColor.GREEN),
                lore,
                true
        );
    }

    private static ItemStack createGuideButton() {
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
        ItemStack hand = slots.realItemOrNull(inventory.getItem(HAND_SLOT));
        ItemStack offHand = slots.realItemOrNull(inventory.getItem(OFF_HAND_SLOT));
        boolean hasAny = hand != null || offHand != null;

        List<Component> lore = List.of(
                nonItalic("主手: ", NamedTextColor.GRAY).append(describeSlotItem(hand)),
                nonItalic("副手: ", NamedTextColor.GRAY).append(describeSlotItem(offHand)),
                Component.empty(),
                hasAny
                        ? nonItalic("点击互换左右手臂槽位物品", NamedTextColor.YELLOW)
                        : nonItalic("主手与副手均为空", NamedTextColor.DARK_GRAY)
        );
        return createButton(
                Material.REPEATER,
                nonItalic("交换主副手", hasAny ? NamedTextColor.GOLD : NamedTextColor.GRAY),
                lore,
                false
        );
    }

    private ItemStack createImportArmorButton(Inventory inventory) {
        long emptyArmorSlots = ARMOR_IMPORT_MAPPINGS.stream()
                .filter(mapping -> !slots.isRealItem(inventory.getItem(mapping.guiSlot())))
                .count();
        return createButton(
                Material.HOPPER,
                nonItalic("导入穿戴护甲", NamedTextColor.AQUA),
                List.of(
                        nonItalic("将身上穿戴的头/胸/腿/靴", NamedTextColor.GRAY),
                        nonItalic("自动移入左侧对应的空槽位", NamedTextColor.GRAY),
                        Component.empty(),
                        nonItalic("当前可用护甲空槽: " + emptyArmorSlots + " / " + ARMOR_IMPORT_MAPPINGS.size(),
                                NamedTextColor.YELLOW)
                ),
                false
        );
    }

    private static ItemStack createClearAllButton(int equippedCount) {
        boolean hasItems = equippedCount > 0;
        return createButton(
                Material.BRUSH,
                nonItalic("清空全部装备", hasItems ? NamedTextColor.GOLD : NamedTextColor.GRAY),
                List.of(
                        hasItems
                                ? nonItalic("点击退还左侧 " + equippedCount + " 个槽位装备", NamedTextColor.YELLOW)
                                : nonItalic("当前没有已放入的装备", NamedTextColor.DARK_GRAY),
                        nonItalic("退还物品将直接回到你的背包", NamedTextColor.GRAY)
                ),
                false
        );
    }

    private static ItemStack createConfirmButton(FumoGuiHolder holder, int equippedCount) {
        if (equippedCount <= 0) {
            return createButton(
                    Material.GRAY_CONCRETE,
                    nonItalic("确认制作玩偶 (需放入装备)", NamedTextColor.GRAY),
                    List.of(nonItalic("请先在左侧十字架放入至少 1 件装备", NamedTextColor.DARK_GRAY)),
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
                                .append(nonItalic(holder.targetDisplayName())),
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
}

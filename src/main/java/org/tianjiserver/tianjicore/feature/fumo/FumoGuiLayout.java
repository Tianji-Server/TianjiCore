package org.tianjiserver.tianjicore.feature.fumo;

import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fumo 编辑台的 6x9 槽位布局定义。
 * 左侧以十字架形态展示 6 个装备/物品槽位，右侧集中放置信息预览与快捷操作按钮。
 */
final class FumoGuiLayout {

    static final int GUI_SIZE = 54;

    static final int HEAD_SLOT = 11;
    static final int HAND_SLOT = 19;
    static final int CHEST_SLOT = 20;
    static final int OFF_HAND_SLOT = 21;
    static final int LEGS_SLOT = 29;
    static final int FEET_SLOT = 38;

    static final int INFO_SLOT = 15;
    static final int DERIVATIVE_NOTICE_SLOT = 16;
    static final int GUIDE_SLOT = 17;
    static final int SWAP_HANDS_SLOT = 24;
    static final int IMPORT_ARMOR_SLOT = 25;
    static final int CLEAR_ALL_SLOT = 26;
    static final int CONFIRM_SLOT = 42;
    static final int CANCEL_SLOT = 44;

    static final Set<Integer> DIVIDER_SLOTS = Set.of(5, 14, 23, 32, 41, 50);

    /**
     * 顺序与 {@link FumoItemCodec#FUMO_SLOTS} 一一对应。
     */
    static final int[] INPUT_SLOTS = {
            HEAD_SLOT,
            CHEST_SLOT,
            LEGS_SLOT,
            FEET_SLOT,
            HAND_SLOT,
            OFF_HAND_SLOT
    };

    /**
     * Shift+点击无法匹配首选装备栏时的回退填充顺序。
     */
    static final int[] SMART_FALLBACK_SLOTS = {
            HAND_SLOT,
            OFF_HAND_SLOT,
            HEAD_SLOT,
            CHEST_SLOT,
            LEGS_SLOT,
            FEET_SLOT
    };

    static final List<SlotSpec> CROSS_SLOTS = List.of(
            new SlotSpec(HEAD_SLOT, EquipmentSlot.HEAD, "头部栏位", "位于十字架上方 · 戴在玩偶头顶"),
            new SlotSpec(HAND_SLOT, EquipmentSlot.HAND, "主手栏位", "位于十字架左侧 · 对应玩偶右手"),
            new SlotSpec(CHEST_SLOT, EquipmentSlot.CHEST, "胸甲栏位", "位于十字架中央 · 穿在玩偶躯干"),
            new SlotSpec(OFF_HAND_SLOT, EquipmentSlot.OFF_HAND, "副手栏位", "位于十字架右侧 · 对应玩偶左手"),
            new SlotSpec(LEGS_SLOT, EquipmentSlot.LEGS, "护腿栏位", "位于十字架中下 · 穿在玩偶腿部"),
            new SlotSpec(FEET_SLOT, EquipmentSlot.FEET, "靴子栏位", "位于十字架底部 · 穿在玩偶脚部")
    );

    private static final Map<Integer, SlotSpec> CROSS_SLOT_MAP = CROSS_SLOTS.stream()
            .collect(Collectors.toUnmodifiableMap(SlotSpec::slot, Function.identity()));

    /**
     * “导入穿戴护甲”时玩家护甲栏与编辑台槽位的对应关系。
     */
    static final List<ArmorMapping> ARMOR_IMPORT_MAPPINGS = List.of(
            new ArmorMapping(EquipmentSlot.HEAD, HEAD_SLOT),
            new ArmorMapping(EquipmentSlot.CHEST, CHEST_SLOT),
            new ArmorMapping(EquipmentSlot.LEGS, LEGS_SLOT),
            new ArmorMapping(EquipmentSlot.FEET, FEET_SLOT)
    );

    private FumoGuiLayout() {
    }

    static boolean isCrossSlot(int slot) {
        return CROSS_SLOT_MAP.containsKey(slot);
    }

    static SlotSpec crossSlot(int slot) {
        return CROSS_SLOT_MAP.get(slot);
    }

    record SlotSpec(int slot, EquipmentSlot equipmentSlot, String label, String positionHint) {
        String shortLabel() {
            return label.substring(0, 2);
        }
    }

    record ArmorMapping(EquipmentSlot armorSlot, int guiSlot) {
    }
}

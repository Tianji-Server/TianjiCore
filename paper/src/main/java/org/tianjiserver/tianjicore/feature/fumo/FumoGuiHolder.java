package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * 单个玩家的 Fumo 编辑台会话状态。
 */
final class FumoGuiHolder implements InventoryHolder {

    private final UUID playerId;
    private final Component targetDisplayName;
    private Inventory inventory;
    private boolean derivativeNoticeEnabled = true;
    private boolean completed;

    FumoGuiHolder(UUID playerId, Component targetDisplayName) {
        this.playerId = playerId;
        this.targetDisplayName = targetDisplayName;
    }

    void bindInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    UUID playerId() {
        return playerId;
    }

    Component targetDisplayName() {
        return targetDisplayName;
    }

    Inventory inventory() {
        return inventory;
    }

    boolean derivativeNoticeEnabled() {
        return derivativeNoticeEnabled;
    }

    void toggleDerivativeNotice() {
        this.derivativeNoticeEnabled = !this.derivativeNoticeEnabled;
    }

    boolean completed() {
        return completed;
    }

    void markCompleted() {
        this.completed = true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

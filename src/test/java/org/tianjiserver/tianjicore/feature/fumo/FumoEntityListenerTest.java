package org.tianjiserver.tianjicore.feature.fumo;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.mockito.Mockito.*;

class FumoEntityListenerTest {

    private FumoItemCodec codec;
    private FumoEntityListener listener;
    private ArmorStand stand;
    private Player player;
    private PlayerInventory inventory;
    private ItemStack fumoItem;

    @BeforeEach
    void setUp() {
        codec = mock(FumoItemCodec.class);
        listener = new FumoEntityListener(FumoConfig.DEFAULT, codec);
        stand = mock(ArmorStand.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        fumoItem = mock(ItemStack.class);

        World world = mock(World.class);
        Location location = new Location(world, 0.0, 64.0, 0.0);

        when(codec.isFumoEntity(stand)).thenReturn(true);
        when(stand.isValid()).thenReturn(true);
        when(stand.getLocation()).thenReturn(location);
        when(codec.extractFumoItem(stand, FumoConfig.DEFAULT)).thenReturn(fumoItem);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(fumoItem)).thenReturn(new HashMap<>());
    }

    @Test
    void leftClickWithoutSneakCancelsDamageWithoutPickingUp() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(stand);
        when(event.getDamager()).thenReturn(player);
        when(player.isSneaking()).thenReturn(false);

        listener.onEntityDamage(event);

        verify(event).setCancelled(true);
        verify(codec, never()).extractFumoItem(any(), any());
        verify(stand, never()).remove();
        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test
    void sneakLeftClickCancelsDamageAndPicksUpFumo() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(stand);
        when(event.getDamager()).thenReturn(player);
        when(player.isSneaking()).thenReturn(true);

        listener.onEntityDamage(event);

        verify(event).setCancelled(true);
        verify(codec).extractFumoItem(stand, FumoConfig.DEFAULT);
        verify(stand).remove();
        verify(inventory).addItem(fumoItem);
    }

    @Test
    void sneakRightClickStillPicksUpWhenEnabled() {
        PlayerInteractAtEntityEvent event = mock(PlayerInteractAtEntityEvent.class);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getRightClicked()).thenReturn(stand);
        when(event.getPlayer()).thenReturn(player);
        when(player.isSneaking()).thenReturn(true);

        listener.onInteractAtEntity(event);

        verify(event).setCancelled(true);
        verify(codec).extractFumoItem(stand, FumoConfig.DEFAULT);
        verify(stand).remove();
        verify(inventory).addItem(fumoItem);
    }
}

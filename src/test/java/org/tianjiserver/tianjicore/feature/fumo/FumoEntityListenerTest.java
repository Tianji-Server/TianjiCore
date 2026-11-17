package org.tianjiserver.tianjicore.feature.fumo;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.function.Consumer;

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
        listener = new FumoEntityListener(codec);
        stand = mock(ArmorStand.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        fumoItem = mock(ItemStack.class);

        World world = mock(World.class);
        Location location = new Location(world, 0.0, 64.0, 0.0);

        when(codec.isFumoEntity(stand)).thenReturn(true);
        when(stand.isValid()).thenReturn(true);
        when(stand.getLocation()).thenReturn(location);
        when(codec.extractFumoItem(stand)).thenReturn(fumoItem);
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
        verify(codec, never()).extractFumoItem(any());
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
        verify(codec).extractFumoItem(stand);
        verify(stand).remove();
        verify(inventory).addItem(fumoItem);
    }

    @Test
    void sneakRightClickStillPicksUp() {
        PlayerInteractAtEntityEvent event = mock(PlayerInteractAtEntityEvent.class);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getRightClicked()).thenReturn(stand);
        when(event.getPlayer()).thenReturn(player);
        when(player.isSneaking()).thenReturn(true);

        listener.onInteractAtEntity(event);

        verify(event).setCancelled(true);
        verify(codec).extractFumoItem(stand);
        verify(stand).remove();
        verify(inventory).addItem(fumoItem);
    }

    @Test
    void nonPlayerDamageIsAlwaysCancelled() {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(stand);

        listener.onEntityDamage(event);

        verify(event).setCancelled(true);
        verify(stand, never()).remove();
    }

    @Test
    @SuppressWarnings("unchecked")
    void placingFumoEnablesGravitySoGravityAndWaterFlowApply() {
        World world = mock(World.class);
        Block clickedBlock = mock(Block.class);
        Block targetBlock = mock(Block.class);
        ArmorStand spawnedStand = mock(ArmorStand.class);
        EntityEquipment equipment = mock(EntityEquipment.class);

        when(codec.isFumoItem(fumoItem)).thenReturn(true);
        when(codec.decodeEquipment(fumoItem)).thenReturn(new ItemStack[FumoItemCodec.FUMO_SLOTS.size()]);
        when(codec.extractItemDisplayName(fumoItem)).thenReturn(Component.text("Fumo"));

        when(clickedBlock.isPassable()).thenReturn(false);
        when(clickedBlock.getRelative(BlockFace.UP)).thenReturn(targetBlock);
        when(targetBlock.isPassable()).thenReturn(true);
        when(targetBlock.getLocation()).thenReturn(new Location(world, 10.0, 64.0, 20.0));
        when(player.getLocation()).thenReturn(new Location(world, 10.0, 64.0, 18.0));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(spawnedStand.getEquipment()).thenReturn(equipment);

        when(world.spawn(any(Location.class), eq(ArmorStand.class), any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<ArmorStand> callback = invocation.getArgument(2);
            callback.accept(spawnedStand);
            return spawnedStand;
        });

        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getItem()).thenReturn(fumoItem);
        when(event.getClickedBlock()).thenReturn(clickedBlock);
        when(event.getBlockFace()).thenReturn(BlockFace.UP);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(spawnedStand).setGravity(true);
        verify(spawnedStand).setSmall(true);
        verify(spawnedStand).setArms(true);
        verify(spawnedStand).setBasePlate(false);
    }
}

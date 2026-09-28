package org.tianjiserver.tianjicore.feature;

import org.bukkit.EntityEffect;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tianjiserver.tianjicore.TianjiCore;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class NewbieManagerTest {

    @TempDir
    Path dataFolder;

    @Test
    void usesProtectionOnlyForLethalDamage() {
        TianjiCore plugin = mock(TianjiCore.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(mock(Logger.class));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPlayedBefore()).thenReturn(false);
        when(player.getHealth()).thenReturn(10.0);

        NewbieManager newbieManager = spy(new NewbieManager(plugin));
        doNothing().when(newbieManager).applyNewbieEffects(player);
        doNothing().when(newbieManager).giveBread(player);
        PlayerJoinEvent joinEvent = mock(PlayerJoinEvent.class);
        when(joinEvent.getPlayer()).thenReturn(player);
        newbieManager.onPlayerJoin(joinEvent);

        for (int hit = 0; hit < 3; hit++) {
            EntityDamageEvent damageEvent = mock(EntityDamageEvent.class);
            when(damageEvent.getEntity()).thenReturn(player);
            when(damageEvent.getFinalDamage()).thenReturn(5.0);
            newbieManager.onPlayerDamage(damageEvent);
            verify(damageEvent, never()).setCancelled(true);
        }

        for (int hit = 0; hit < 4; hit++) {
            EntityDamageEvent damageEvent = mock(EntityDamageEvent.class);
            when(damageEvent.getEntity()).thenReturn(player);
            when(damageEvent.getFinalDamage()).thenReturn(10.0);
            newbieManager.onPlayerDamage(damageEvent);
            if (hit < 3) {
                verify(damageEvent).setCancelled(true);
            } else {
                verify(damageEvent, never()).setCancelled(true);
            }
        }

        verify(player, times(3)).playEffect(EntityEffect.TOTEM_RESURRECT);
        verify(player).sendMessage("剩余无敌次数：2");
        verify(player).sendMessage("剩余无敌次数：1");
        verify(player).sendMessage("剩余无敌次数：0");
    }
}
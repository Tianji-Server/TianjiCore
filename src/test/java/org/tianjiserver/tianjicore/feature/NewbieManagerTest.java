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
    void playsTotemEffectExactlyOnceForEachOfThreeProtectedHits() {
        TianjiCore plugin = mock(TianjiCore.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(mock(Logger.class));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPlayedBefore()).thenReturn(false);

        NewbieManager newbieManager = new NewbieManager(plugin);
        PlayerJoinEvent joinEvent = mock(PlayerJoinEvent.class);
        when(joinEvent.getPlayer()).thenReturn(player);
        newbieManager.onPlayerJoin(joinEvent);

        for (int hit = 0; hit < 4; hit++) {
            EntityDamageEvent damageEvent = mock(EntityDamageEvent.class);
            when(damageEvent.getEntity()).thenReturn(player);
            newbieManager.onPlayerDamage(damageEvent);

            if (hit < 3) {
                verify(damageEvent).setCancelled(true);
            } else {
                verify(damageEvent, never()).setCancelled(true);
            }
        }

        verify(player, times(3)).playEffect(EntityEffect.TOTEM_RESURRECT);
    }
}
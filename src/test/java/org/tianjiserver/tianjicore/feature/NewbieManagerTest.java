package org.tianjiserver.tianjicore.feature;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tianjiserver.tianjicore.TianjiCore;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NewbieManagerTest {

    @TempDir
    Path dataFolder;

    @Test
    void grantsBreadAndTotemsOnlyOnceOnFirstJoin() {
        TianjiCore plugin = mock(TianjiCore.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(mock(Logger.class));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPlayedBefore()).thenReturn(false);
        NewbieManager newbieManager = spy(new NewbieManager(plugin));
        doNothing().when(newbieManager).applyNewbieEffects(player);
        doNothing().when(newbieManager).giveBread(player);
        doNothing().when(newbieManager).giveTotems(player);
        PlayerJoinEvent joinEvent = mock(PlayerJoinEvent.class);
        when(joinEvent.getPlayer()).thenReturn(player);
        newbieManager.onPlayerJoin(joinEvent);
        newbieManager.onPlayerJoin(joinEvent);

        verify(newbieManager).applyNewbieEffects(player);
        verify(newbieManager).giveBread(player);
        verify(newbieManager).giveTotems(player);
        assertEquals(16, NewbieManager.BREAD_AMOUNT);
        assertEquals(3, NewbieManager.TOTEM_AMOUNT);
    }
}
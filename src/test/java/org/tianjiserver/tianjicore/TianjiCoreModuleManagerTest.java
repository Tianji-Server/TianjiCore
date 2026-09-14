package org.tianjiserver.tianjicore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.tianjiserver.tianjicore.TianjiCoreModuleManager.ReloadStatus.*;

class TianjiCoreModuleManagerTest {
    @TempDir
    Path dataFolder;

    private TianjiCore plugin;
    private PluginManager pluginManager;
    private YamlConfiguration config;
    private TianjiCoreModuleManager manager;

    @BeforeEach
    void setUp() {
        plugin = mock(TianjiCore.class);
        Server server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        config = new YamlConfiguration();
        for (String key : List.of("firstjoinmessage", "recipebugfix", "phantomspawnblocker",
                "endermanmushroombugfix")) {
            config.set("modules." + key + ".enabled", false);
        }
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        manager = new TianjiCoreModuleManager(plugin);
        manager.bootstrap();
    }

    @Test
    void wholeReloadSucceedsWithMixedEnabledAndDisabledModules() {
        enable("phantomspawnblocker");
        var result = manager.reload("ALL");
        assertEquals(SUCCESS_PLUGIN, result.status());
        assertTrue(result.failures().isEmpty());
        assertEquals(List.of(false, false, true, false), states());
    }

    @Test
    void wholeReloadReportsEveryFailureAndContinuesToLaterModules() {
        enable("firstjoinmessage");
        enable("phantomspawnblocker");
        enable("endermanmushroombugfix");
        doThrow(new IllegalStateException("first failure"))
                .doThrow(new IllegalArgumentException("second failure"))
                .doNothing().when(pluginManager).registerEvents(any(Listener.class), eq(plugin));

        var result = manager.reload("plugin");
        assertEquals(FAILED, result.status());
        assertEquals(List.of("firstjoinmessage", "phantomspawnblocker"),
                result.failures().stream().map(TianjiCoreModuleManager.ReloadFailure::target).toList());
        assertTrue(result.failures().get(0).reason().contains("first failure"));
        assertTrue(result.failures().get(1).reason().contains("second failure"));
        assertEquals(List.of(false, false, false, true), states());
        verify(pluginManager, times(3)).registerEvents(any(Listener.class), eq(plugin));
    }

    @Test
    void singleReloadReportsReasonAndLaterSuccessClearsFailure() {
        enable("phantomspawnblocker");
        doThrow(new IllegalStateException("registration failed"))
                .doNothing().when(pluginManager).registerEvents(any(Listener.class), eq(plugin));

        var failure = manager.reload("phantom");
        assertEquals(FAILED, failure.status());
        assertFalse(failure.moduleInfo().enabled());
        assertEquals("IllegalStateException: registration failed", failure.failures().getFirst().reason());

        var success = manager.reload("phantom");
        assertEquals(SUCCESS_MODULE, success.status());
        assertTrue(success.moduleInfo().enabled());
        assertTrue(success.failures().isEmpty());
    }

    @Test
    void exceptionWithoutMessageStillHasReadableReason() {
        enable("phantomspawnblocker");
        doThrow(new IllegalStateException()).when(pluginManager)
                .registerEvents(any(Listener.class), eq(plugin));
        assertEquals("IllegalStateException", manager.reload("phantom").failures().getFirst().reason());
    }

    @Test
    void reloadingDisabledConfigurationStopsRunningModuleSuccessfully() {
        enable("phantomspawnblocker");
        manager.reload("phantom");
        config.set("modules.phantomspawnblocker.enabled", false);
        var result = manager.reload("phantom");
        assertEquals(SUCCESS_MODULE, result.status());
        assertFalse(result.moduleInfo().enabled());
        assertTrue(result.failures().isEmpty());
        assertEquals(List.of(false, false, false, false), states());
    }

    @Test
    void singleReloadDoesNotApplyOtherModuleChanges() {
        enable("phantomspawnblocker");
        enable("endermanmushroombugfix");
        manager.reload("phantom");
        assertEquals(List.of(false, false, true, false), states());
    }

    @Test
    void malformedYamlReportsConfigurationFailureAndPreservesRunningModules() throws Exception {
        enable("phantomspawnblocker");
        manager.reload("phantom");
        clearInvocations(plugin);
        Files.writeString(dataFolder.resolve("config.yml"), "modules: [unterminated");

        var result = manager.reload("plugin");
        assertEquals(FAILED, result.status());
        assertEquals("config.yml", result.failures().getFirst().target());
        assertTrue(result.failures().getFirst().reason().contains("InvalidConfigurationException"));
        assertEquals(List.of(false, false, true, false), states());
        verify(plugin, never()).reloadConfig();
    }

    @Test
    void unknownTargetDoesNotReloadConfiguration() {
        assertEquals(UNKNOWN_MODULE, manager.reload("missing").status());
        verify(plugin, never()).reloadConfig();
    }

    @Test
    void successfulWholeReloadCommandKeepsOriginalSingleSuccessMessage() {
        TianjiCoreCommand command = new TianjiCoreCommand(plugin);
        command.bootstrap();
        CommandSender sender = mock(CommandSender.class);
        command.handleReloadCommand(sender, "plugin");
        assertEquals(List.of("插件与模块配置已重载"), messages(sender, 1));
    }

    @Test
    void failedReloadCommandReportsReasonAsLiteralTextWithoutSuccessMessage() {
        TianjiCoreCommand command = new TianjiCoreCommand(plugin);
        command.bootstrap();
        enable("phantomspawnblocker");
        doThrow(new IllegalStateException("bad <red>value"))
                .when(pluginManager).registerEvents(any(Listener.class), eq(plugin));
        CommandSender sender = mock(CommandSender.class);

        command.handleReloadCommand(sender, "plugin");
        assertEquals(List.of("重载失败:", "phantomspawnblocker: IllegalStateException: bad <red>value"),
                messages(sender, 2));
    }

    @Test
    void statusCommandDisplaysActualStateInsteadOfUnappliedConfig() {
        TianjiCoreCommand command = new TianjiCoreCommand(plugin);
        command.bootstrap();
        enable("phantomspawnblocker");
        command.handleReloadCommand(mock(CommandSender.class), "phantom");
        enable("endermanmushroombugfix");
        CommandSender sender = mock(CommandSender.class);
        command.handleStatusCommand(sender);

        assertEquals(List.of("模块运行状态:", "首次进服消息 (firstjoinmessage): 关闭",
                "配方修复 (recipebugfix): 关闭", "阻止幻翼生成 (phantomspawnblocker): 开启",
                "禁止末影人搬动方块 (endermanmushroombugfix): 关闭"), messages(sender, 5));
    }

    private void enable(String key) {
        config.set("modules." + key + ".enabled", true);
    }

    private List<Boolean> states() {
        return manager.getModuleInfos().stream().map(TianjiCoreModuleManager.ModuleInfo::enabled).toList();
    }

    private List<String> messages(CommandSender sender, int count) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(sender, times(count)).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }
}

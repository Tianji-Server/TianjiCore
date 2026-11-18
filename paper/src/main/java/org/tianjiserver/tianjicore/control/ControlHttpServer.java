package org.tianjiserver.tianjicore.control;

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.tianjiserver.tianjicore.TianjiCore;
import org.tianjiserver.tianjicore.TianjiCoreModuleHelper;
import org.tianjiserver.tianjicore.common.protocol.ControlProtocol;
import org.tianjiserver.tianjicore.common.protocol.ErrorResponse;
import org.tianjiserver.tianjicore.common.protocol.ReloadFailureDto;
import org.tianjiserver.tianjicore.common.protocol.ReloadRequest;
import org.tianjiserver.tianjicore.common.protocol.ReloadResponse;
import org.tianjiserver.tianjicore.common.protocol.ReloadStatus;
import org.tianjiserver.tianjicore.common.protocol.StatusResponse;
import org.tianjiserver.tianjicore.common.protocol.ToggleRequest;
import org.tianjiserver.tianjicore.common.protocol.ToggleResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Paper 端内置轻量 HTTP 控制服务端。
 * 接收来自 Velocity 代理端的鉴权请求，并在 Bukkit 主线程执行模块状态查询、开关与重载操作。
 */
public class ControlHttpServer {

    private static final long MAIN_THREAD_TIMEOUT_SECONDS = 5L;

    private final TianjiCore plugin;
    private final TianjiCoreModuleHelper moduleHelper;

    private HttpServer server;
    private ExecutorService executor;
    private volatile ServerSettings activeSettings;

    public ControlHttpServer(TianjiCore plugin, TianjiCoreModuleHelper moduleHelper) {
        this.plugin = plugin;
        this.moduleHelper = moduleHelper;
    }

    /**
     * 按当前配置启动控制 HTTP 服务端。
     */
    public synchronized void start() {
        ServerSettings settings = loadOrInitializeSettings();
        this.activeSettings = settings;

        if (!settings.enabled()) {
            plugin.getLogger().info("HTTP 控制服务端未启用 (control-server.enabled=false)");
            return;
        }
        if (!isValidPort(settings.port())) {
            plugin.getLogger().severe("HTTP 控制服务端端口无效: " + settings.port() + "，未启动监听");
            return;
        }

        try {
            ActiveListener listener = createAndStartListener(settings);
            this.server = listener.server();
            this.executor = listener.executor();
            plugin.getLogger().info("HTTP 控制服务端已监听 " + settings.host() + ":" + settings.port());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "HTTP 控制服务端启动失败", exception);
        }
    }

    /**
     * 停止控制 HTTP 服务端并释放监听端口。
     */
    public synchronized void stop() {
        shutdownListener(this.server, this.executor);
        this.server = null;
        this.executor = null;
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!requireMethodAndAuth(exchange, "GET")) {
            return;
        }
        try {
            StatusResponse response = runOnMainThread(moduleHelper::getStatus);
            sendJson(exchange, 200, response);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "处理 status 控制请求失败", exception);
            sendJson(exchange, 500, new ErrorResponse("服务器内部执行失败: " + exception.getMessage()));
        }
    }

    private void handleToggle(HttpExchange exchange) throws IOException {
        if (!requireMethodAndAuth(exchange, "POST")) {
            return;
        }
        ToggleRequest request = readJsonBody(exchange, ToggleRequest.class);
        if (request == null) {
            return;
        }
        if (request.module() == null || request.module().isBlank()) {
            sendJson(exchange, 400, new ErrorResponse("缺少 module 参数"));
            return;
        }

        try {
            ToggleResponse response = runOnMainThread(() -> moduleHelper.toggleModule(request.module()));
            sendJson(exchange, 200, response);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "处理 toggle 控制请求失败", exception);
            sendJson(exchange, 500, new ErrorResponse("服务器内部执行失败: " + exception.getMessage()));
        }
    }

    private void handleReload(HttpExchange exchange) throws IOException {
        if (!requireMethodAndAuth(exchange, "POST")) {
            return;
        }
        ReloadRequest request = readJsonBody(exchange, ReloadRequest.class);
        if (request == null) {
            return;
        }
        if (request.target() == null || request.target().isBlank()) {
            sendJson(exchange, 400, new ErrorResponse("缺少 target 参数"));
            return;
        }

        ServerSettings settingsBeforeReload = this.activeSettings;
        try {
            ReloadExecution execution = runOnMainThread(() -> {
                ReloadResponse reloadResponse = moduleHelper.reloadModule(request.target());
                ServerSettings latest = reloadResponse.status() == ReloadStatus.UNKNOWN_MODULE
                        ? settingsBeforeReload
                        : loadOrInitializeSettings();
                return new ReloadExecution(reloadResponse, latest);
            });

            ReloadResponse response = execution.response();
            Runnable postSendAction = null;
            if (response.status() != ReloadStatus.UNKNOWN_MODULE
                    && !Objects.equals(settingsBeforeReload, execution.latestSettings())) {
                ListenerUpdateResult updateResult = prepareListenerUpdate(settingsBeforeReload, execution.latestSettings());
                postSendAction = updateResult.postSendAction();
                if (updateResult.failureReason() != null) {
                    response = appendControlServerFailure(response, updateResult.failureReason());
                }
            }

            // 完成响应写入后再执行旧监听关闭等后续动作，避免断开当前 HTTP 连接。
            sendJson(exchange, 200, response);
            if (postSendAction != null) {
                Thread.ofVirtual().name("tianjicore-http-restart").start(postSendAction);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "处理 reload 控制请求失败", exception);
            sendJson(exchange, 500, new ErrorResponse("服务器内部执行失败: " + exception.getMessage()));
        }
    }

    private synchronized ListenerUpdateResult prepareListenerUpdate(
            ServerSettings previousSettings,
            ServerSettings latestSettings
    ) {
        if (!plugin.isEnabled()) {
            return new ListenerUpdateResult(null, null);
        }
        if (!latestSettings.enabled()) {
            this.activeSettings = latestSettings;
            plugin.getLogger().info("检测到 control-server.enabled=false，正在关闭 HTTP 控制服务端...");
            return new ListenerUpdateResult(null, this::stop);
        }
        if (!isValidPort(latestSettings.port())) {
            String reason = "HTTP 控制端口无效: " + latestSettings.port() + "，已保留原监听";
            plugin.getLogger().severe(reason);
            return new ListenerUpdateResult(reason, null);
        }
        if (this.server != null
                && Objects.equals(previousSettings.host(), latestSettings.host())
                && previousSettings.port() == latestSettings.port()) {
            this.activeSettings = latestSettings;
            plugin.getLogger().info("已更新 HTTP 控制服务端鉴权配置");
            return new ListenerUpdateResult(null, null);
        }
        if (this.server == null || previousSettings.port() != latestSettings.port()) {
            try {
                ActiveListener newListener = createAndStartListener(latestSettings);
                HttpServer oldServer = this.server;
                ExecutorService oldExecutor = this.executor;
                this.server = newListener.server();
                this.executor = newListener.executor();
                this.activeSettings = latestSettings;
                plugin.getLogger().info("HTTP 控制服务端已切换监听至 "
                        + latestSettings.host() + ":" + latestSettings.port());
                Runnable closeOld = oldServer == null && oldExecutor == null
                        ? null
                        : () -> shutdownListener(oldServer, oldExecutor);
                return new ListenerUpdateResult(null, closeOld);
            } catch (IOException exception) {
                String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                String reason = "新 HTTP 监听启动失败 (" + latestSettings.host() + ":" + latestSettings.port()
                        + "): " + detail + "，已保留原监听";
                plugin.getLogger().log(Level.SEVERE, reason, exception);
                return new ListenerUpdateResult(reason, null);
            }
        }

        // 端口不变但监听地址变更时无法预绑定同一端口，响应发送后再切换并在失败时回滚原监听。
        return new ListenerUpdateResult(null, () -> switchHostSamePort(previousSettings, latestSettings));
    }

    private synchronized void switchHostSamePort(ServerSettings previousSettings, ServerSettings targetSettings) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getLogger().info("检测到 control-server 监听地址变更，正在重启 HTTP 控制服务端...");
        stop();
        try {
            ActiveListener listener = createAndStartListener(targetSettings);
            this.server = listener.server();
            this.executor = listener.executor();
            this.activeSettings = targetSettings;
            plugin.getLogger().info("HTTP 控制服务端已监听 " + targetSettings.host() + ":" + targetSettings.port());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "新 HTTP 监听启动失败，正在回滚原监听", exception);
            try {
                ActiveListener rollback = createAndStartListener(previousSettings);
                this.server = rollback.server();
                this.executor = rollback.executor();
                this.activeSettings = previousSettings;
                plugin.getLogger().warning("已回滚至原 HTTP 监听 "
                        + previousSettings.host() + ":" + previousSettings.port());
            } catch (IOException rollbackException) {
                plugin.getLogger().log(Level.SEVERE, "回滚原 HTTP 监听也失败，远程控制已断开", rollbackException);
            }
        }
    }

    private ActiveListener createAndStartListener(ServerSettings settings) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(settings.host(), settings.port()), 0);
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            httpServer.setExecutor(virtualExecutor);
            httpServer.createContext(ControlProtocol.API_STATUS_PATH, this::handleStatus);
            httpServer.createContext(ControlProtocol.API_TOGGLE_PATH, this::handleToggle);
            httpServer.createContext(ControlProtocol.API_RELOAD_PATH, this::handleReload);
            httpServer.start();
            return new ActiveListener(httpServer, virtualExecutor);
        } catch (Throwable throwable) {
            shutdownListener(httpServer, virtualExecutor);
            throw throwable;
        }
    }

    private static void shutdownListener(HttpServer targetServer, ExecutorService targetExecutor) {
        if (targetServer != null) {
            targetServer.stop(0);
        }
        if (targetExecutor != null) {
            targetExecutor.close();
        }
    }

    private static ReloadResponse appendControlServerFailure(ReloadResponse response, String reason) {
        List<ReloadFailureDto> failures = new ArrayList<>(response.failures());
        failures.add(new ReloadFailureDto("control-server", reason));
        return new ReloadResponse(ReloadStatus.FAILED, response.moduleInfo(), failures,
                response.moduleKeys(), response.reloadPluginTarget());
    }

    private static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }

    private boolean requireMethodAndAuth(HttpExchange exchange, String expectedMethod) throws IOException {
        if (!expectedMethod.equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new ErrorResponse("Method Not Allowed"));
            return false;
        }
        ServerSettings current = this.activeSettings;
        String authHeader = exchange.getRequestHeaders().getFirst(ControlProtocol.AUTHORIZATION_HEADER);
        if (current == null || !ControlProtocol.isAuthorized(authHeader, current.token())) {
            sendJson(exchange, 401, new ErrorResponse("Unauthorized"));
            return false;
        }
        return true;
    }

    private <T> T readJsonBody(HttpExchange exchange, Class<T> bodyType) throws IOException {
        byte[] rawBody = readLimitedBytes(exchange.getRequestBody(), ControlProtocol.MAX_REQUEST_BODY_BYTES);
        if (rawBody == null) {
            sendJson(exchange, 413, new ErrorResponse("请求体过大"));
            return null;
        }
        String json = new String(rawBody, StandardCharsets.UTF_8);
        try {
            T parsed = ControlProtocol.gson().fromJson(json, bodyType);
            if (parsed == null) {
                sendJson(exchange, 400, new ErrorResponse("请求体不能为空"));
                return null;
            }
            return parsed;
        } catch (JsonSyntaxException exception) {
            sendJson(exchange, 400, new ErrorResponse("无效的 JSON 请求体"));
            return null;
        }
    }

    private <T> T runOnMainThread(Callable<T> task) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            return task.call();
        }
        return Bukkit.getScheduler()
                .callSyncMethod(plugin, task)
                .get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private ServerSettings loadOrInitializeSettings() {
        ServerSettings settings = readSettingsFromConfig();
        if (settings.enabled() && settings.token().isBlank()) {
            String generatedToken = ControlProtocol.generateSecureToken();
            plugin.getConfig().set("control-server.token", generatedToken);
            plugin.saveConfig();
            plugin.getLogger().info("已为 HTTP 控制服务端自动生成密钥并写入 config.yml (control-server.token)");
            return new ServerSettings(true, settings.host(), settings.port(), generatedToken);
        }
        return settings;
    }

    private ServerSettings readSettingsFromConfig() {
        boolean enabled = plugin.getConfig().getBoolean("control-server.enabled", true);
        String host = plugin.getConfig().getString("control-server.host", ControlProtocol.DEFAULT_HOST);
        if (host == null || host.isBlank()) {
            host = ControlProtocol.DEFAULT_HOST;
        }
        int port = plugin.getConfig().getInt("control-server.port", ControlProtocol.DEFAULT_PORT);
        String token = plugin.getConfig().getString("control-server.token", "");
        return new ServerSettings(enabled, host.trim(), port, token == null ? "" : token.trim());
    }

    private static byte[] readLimitedBytes(InputStream input, int maxBytes) throws IOException {
        byte[] buffer = input.readNBytes(maxBytes + 1);
        if (buffer.length > maxBytes) {
            return null;
        }
        return buffer;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] body = ControlProtocol.gson().toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", ControlProtocol.CONTENT_TYPE_JSON);
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        } finally {
            exchange.close();
        }
    }

    private record ServerSettings(boolean enabled, String host, int port, String token) {
    }

    private record ActiveListener(HttpServer server, ExecutorService executor) {
    }

    private record ReloadExecution(ReloadResponse response, ServerSettings latestSettings) {
    }

    private record ListenerUpdateResult(String failureReason, Runnable postSendAction) {
    }
}

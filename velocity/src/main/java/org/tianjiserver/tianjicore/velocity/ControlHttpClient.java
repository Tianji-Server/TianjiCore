package org.tianjiserver.tianjicore.velocity;

import com.google.gson.JsonSyntaxException;
import org.tianjiserver.tianjicore.common.protocol.ControlProtocol;
import org.tianjiserver.tianjicore.common.protocol.ErrorResponse;
import org.tianjiserver.tianjicore.common.protocol.ModuleInfoDto;
import org.tianjiserver.tianjicore.common.protocol.ReloadRequest;
import org.tianjiserver.tianjicore.common.protocol.ReloadResponse;
import org.tianjiserver.tianjicore.common.protocol.StatusResponse;
import org.tianjiserver.tianjicore.common.protocol.ToggleRequest;
import org.tianjiserver.tianjicore.common.protocol.ToggleResponse;
import org.tianjiserver.tianjicore.velocity.VelocityConfig.BackendServerConfig;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Velocity 端异步 HTTP 控制客户端。
 * 负责向一个或多个 Paper 后端控制服务端并行发送状态查询、开关与重载请求，并缓存模块元信息用于命令补全。
 */
public class ControlHttpClient implements AutoCloseable {

    private final HttpClient httpClient;

    private volatile List<String> cachedModuleKeys = ControlProtocol.DEFAULT_MODULE_KEYS;
    private volatile List<String> cachedToggleableKeys = ControlProtocol.DEFAULT_TOGGLEABLE_MODULE_KEYS;
    private volatile String cachedReloadPluginTarget = ControlProtocol.RELOAD_PLUGIN_TARGET;

    public ControlHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * 并行查询目标子服模块状态。
     */
    public CompletableFuture<List<ServerCallResult<StatusResponse>>> fetchStatus(
            List<BackendServerConfig> targets,
            int timeoutMs
    ) {
        return dispatchAll(targets, server -> sendRequest(
                server,
                "GET",
                ControlProtocol.API_STATUS_PATH,
                null,
                timeoutMs,
                StatusResponse.class
        ).thenApply(result -> {
            if (result.isSuccess()) {
                updateMetadataFromStatus(result.payload());
            }
            return result;
        }));
    }

    /**
     * 并行向目标子服发送模块开关请求。
     */
    public CompletableFuture<List<ServerCallResult<ToggleResponse>>> toggleModule(
            List<BackendServerConfig> targets,
            String moduleInput,
            int timeoutMs
    ) {
        return dispatchAll(targets, server -> sendRequest(
                server,
                "POST",
                ControlProtocol.API_TOGGLE_PATH,
                new ToggleRequest(moduleInput),
                timeoutMs,
                ToggleResponse.class
        ).thenApply(result -> {
            if (result.isSuccess() && !result.payload().toggleableKeys().isEmpty()) {
                this.cachedToggleableKeys = result.payload().toggleableKeys();
            }
            return result;
        }));
    }

    /**
     * 并行向目标子服发送配置/模块重载请求。
     */
    public CompletableFuture<List<ServerCallResult<ReloadResponse>>> reloadTarget(
            List<BackendServerConfig> targets,
            String targetInput,
            int timeoutMs
    ) {
        return dispatchAll(targets, server -> sendRequest(
                server,
                "POST",
                ControlProtocol.API_RELOAD_PATH,
                new ReloadRequest(targetInput),
                timeoutMs,
                ReloadResponse.class
        ).thenApply(result -> {
            if (result.isSuccess()) {
                ReloadResponse payload = result.payload();
                if (!payload.moduleKeys().isEmpty()) {
                    this.cachedModuleKeys = payload.moduleKeys();
                }
                if (payload.reloadPluginTarget() != null && !payload.reloadPluginTarget().isBlank()) {
                    this.cachedReloadPluginTarget = payload.reloadPluginTarget();
                }
            }
            return result;
        }));
    }

    public List<String> getKnownModuleKeys() {
        return cachedModuleKeys;
    }

    public List<String> getKnownToggleableKeys() {
        return cachedToggleableKeys;
    }

    public String getKnownReloadPluginTarget() {
        return cachedReloadPluginTarget;
    }

    @Override
    public void close() {
        httpClient.close();
    }

    private void updateMetadataFromStatus(StatusResponse status) {
        if (!status.modules().isEmpty()) {
            this.cachedModuleKeys = status.modules().stream()
                    .map(ModuleInfoDto::key)
                    .toList();
            this.cachedToggleableKeys = status.modules().stream()
                    .filter(ModuleInfoDto::toggleable)
                    .map(ModuleInfoDto::key)
                    .toList();
        }
        if (status.reloadPluginTarget() != null && !status.reloadPluginTarget().isBlank()) {
            this.cachedReloadPluginTarget = status.reloadPluginTarget();
        }
    }

    private <T> CompletableFuture<List<ServerCallResult<T>>> dispatchAll(
            List<BackendServerConfig> targets,
            java.util.function.Function<BackendServerConfig, CompletableFuture<ServerCallResult<T>>> caller
    ) {
        List<CompletableFuture<ServerCallResult<T>>> futures = targets.stream()
                .map(caller)
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    private <T> CompletableFuture<ServerCallResult<T>> sendRequest(
            BackendServerConfig server,
            String method,
            String apiPath,
            Object requestBody,
            int timeoutMs,
            Class<T> responseType
    ) {
        if (server.token() == null || server.token().isBlank()) {
            return CompletableFuture.completedFuture(
                    ServerCallResult.failure(server.name(), "未配置控制密钥 (token)")
            );
        }

        URI targetUri = server.baseUri().resolve(apiPath);
        HttpRequest.Builder builder = HttpRequest.newBuilder(targetUri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header(ControlProtocol.AUTHORIZATION_HEADER, ControlProtocol.BEARER_PREFIX + server.token());

        if ("GET".equals(method)) {
            builder.GET();
        } else {
            String jsonBody = ControlProtocol.gson().toJson(requestBody);
            builder.header("Content-Type", ControlProtocol.CONTENT_TYPE_JSON)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        }

        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        return ServerCallResult.failure(server.name(), describeThrowable(throwable));
                    }
                    return parseHttpResponse(server.name(), response, responseType);
                });
    }

    private <T> ServerCallResult<T> parseHttpResponse(
            String serverName,
            HttpResponse<String> response,
            Class<T> responseType
    ) {
        int code = response.statusCode();
        String body = response.body();

        if (code == 200) {
            try {
                T parsed = ControlProtocol.gson().fromJson(body, responseType);
                if (parsed == null) {
                    return ServerCallResult.failure(serverName, "后端返回空响应体");
                }
                return ServerCallResult.success(serverName, parsed);
            } catch (JsonSyntaxException exception) {
                return ServerCallResult.failure(serverName, "后端响应 JSON 解析失败");
            }
        }

        if (code == 401) {
            return ServerCallResult.failure(serverName, "鉴权失败 (401)，请检查两端 token 配置是否一致");
        }

        String errorDetail = extractErrorDetail(body);
        return ServerCallResult.failure(
                serverName,
                errorDetail == null ? "HTTP 状态码 " + code : "HTTP " + code + ": " + errorDetail
        );
    }

    private static String extractErrorDetail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            ErrorResponse errorResponse = ControlProtocol.gson().fromJson(body, ErrorResponse.class);
            if (errorResponse != null && errorResponse.error() != null && !errorResponse.error().isBlank()) {
                return errorResponse.error();
            }
        } catch (JsonSyntaxException ignored) {
            // 非 JSON 错误体直接回退展示状态码。
        }
        return null;
    }

    private static String describeThrowable(Throwable throwable) {
        Throwable root = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;

        if (root instanceof HttpTimeoutException) {
            return "请求超时";
        }
        if (root instanceof ConnectException) {
            return "无法连接到控制服务端（请确认子服在线且已启用 control-server）";
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    /**
     * 单个子服调用结果包装。
     */
    public record ServerCallResult<T>(String serverName, T payload, String error) {

        public boolean isSuccess() {
            return error == null && payload != null;
        }

        public static <T> ServerCallResult<T> success(String serverName, T payload) {
            return new ServerCallResult<>(serverName, payload, null);
        }

        public static <T> ServerCallResult<T> failure(String serverName, String error) {
            return new ServerCallResult<>(serverName, null, error);
        }
    }
}

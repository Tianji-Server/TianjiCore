package org.tianjiserver.tianjicore.common.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * TianjiCore 代理端（Velocity）与服务端（Paper）之间的控制协议常量与基础工具。
 */
public final class ControlProtocol {

    public static final String API_STATUS_PATH = "/api/v1/status";
    public static final String API_TOGGLE_PATH = "/api/v1/toggle";
    public static final String API_RELOAD_PATH = "/api/v1/reload";

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    public static final int MAX_REQUEST_BODY_BYTES = 8192;
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 28765;
    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 5000;

    public static final String ALL_SERVERS_TARGET = "all";
    public static final String RELOAD_PLUGIN_TARGET = "plugin";
    public static final List<String> DEFAULT_RELOAD_PLUGIN_ALIASES = List.of("plugin", "core", "all", "tianjicore");
    public static final String RELOAD_PROXY_TARGET = "proxy";
    public static final List<String> RELOAD_PROXY_ALIASES = List.of("proxy", "velocity");
    public static final List<String> DEFAULT_MODULE_KEYS = List.of(
            "firstjoinmessage",
            "recipebugfix",
            "phantomspawnblocker",
            "endermanmushroombugfix",
            "fumo"
    );
    public static final List<String> DEFAULT_TOGGLEABLE_MODULE_KEYS = List.of(
            "firstjoinmessage",
            "phantomspawnblocker",
            "fumo"
    );

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ControlProtocol() {
    }

    /**
     * 获取协议共享的 Gson 实例。
     */
    public static Gson gson() {
        return GSON;
    }

    /**
     * 生成高熵随机控制密钥（Base64URL 无填充）。
     */
    public static String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 校验 HTTP Authorization 头中的 Bearer Token。
     */
    public static boolean isAuthorized(String authorizationHeader, String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank() || authorizationHeader == null) {
            return false;
        }
        if (!authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }
        String providedToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return constantTimeEquals(expectedToken, providedToken);
    }

    /**
     * 使用常量时间比较字符串，避免密钥比对的时序侧信道。
     */
    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}

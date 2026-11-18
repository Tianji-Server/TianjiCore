package org.tianjiserver.tianjicore.velocity;

import org.slf4j.Logger;
import org.tianjiserver.tianjicore.common.protocol.ControlProtocol;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Velocity 端插件配置管理器。
 * 负责读取、校验与热重载 config.yml 中的后端服务器连接信息与请求超时设置。
 */
public class VelocityConfig {

    private static final int MIN_TIMEOUT_MS = 500;

    private final Path configPath;
    private final Logger logger;
    private volatile ConfigSnapshot snapshot = new ConfigSnapshot(
            ControlProtocol.DEFAULT_REQUEST_TIMEOUT_MS,
            Map.of()
    );

    public VelocityConfig(Path dataDirectory, Logger logger) {
        this.configPath = dataDirectory.resolve("config.yml");
        this.logger = logger;
    }

    /**
     * 启动时初始化默认配置文件并加载。
     */
    public void bootstrap() {
        try {
            ensureDefaultConfig();
            reload();
        } catch (Exception exception) {
            logger.error("加载 Velocity 插件配置失败，将使用空配置启动: {}", exception.getMessage(), exception);
        }
    }

    /**
     * 重新读取并校验 config.yml；失败时保留当前有效配置并抛出异常。
     */
    public synchronized void reload() throws IOException {
        ensureDefaultConfig();
        String content = Files.readString(configPath, StandardCharsets.UTF_8);

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object loaded = yaml.load(content);
        if (loaded == null) {
            this.snapshot = new ConfigSnapshot(ControlProtocol.DEFAULT_REQUEST_TIMEOUT_MS, Map.of());
            return;
        }
        if (!(loaded instanceof Map<?, ?> rootMap)) {
            throw new IllegalArgumentException("config.yml 根节点必须为映射结构");
        }

        int timeoutMs = parseTimeoutMs(rootMap.get("request-timeout-ms"));
        String defaultToken = asTrimmedString(rootMap.get("default-token"));
        Map<String, BackendServerConfig> servers = parseServers(rootMap.get("servers"), defaultToken);

        this.snapshot = new ConfigSnapshot(timeoutMs, Map.copyOf(servers));
    }

    /**
     * 获取当前生效的配置快照。
     */
    public ConfigSnapshot getSnapshot() {
        return snapshot;
    }

    private void ensureDefaultConfig() throws IOException {
        Files.createDirectories(configPath.getParent());
        if (Files.exists(configPath)) {
            return;
        }
        try (InputStream resource = VelocityConfig.class.getClassLoader().getResourceAsStream("config.yml")) {
            if (resource == null) {
                throw new IOException("插件包内缺少默认 config.yml");
            }
            Files.copy(resource, configPath);
            logger.info("已生成默认配置文件: {}", configPath);
        }
    }

    private static int parseTimeoutMs(Object rawTimeout) {
        if (rawTimeout == null) {
            return ControlProtocol.DEFAULT_REQUEST_TIMEOUT_MS;
        }
        int parsed;
        if (rawTimeout instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(rawTimeout.toString().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("request-timeout-ms 必须为整数: " + rawTimeout);
            }
        }
        return Math.max(MIN_TIMEOUT_MS, parsed);
    }

    private static Map<String, BackendServerConfig> parseServers(Object rawServers, String defaultToken) {
        if (rawServers == null) {
            return Map.of();
        }
        if (!(rawServers instanceof Map<?, ?> serversMap)) {
            throw new IllegalArgumentException("servers 配置项必须为映射结构");
        }

        Map<String, BackendServerConfig> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : serversMap.entrySet()) {
            String serverName = asTrimmedString(entry.getKey());
            if (serverName.isEmpty()) {
                throw new IllegalArgumentException("servers 中存在空服务器名称");
            }
            if (!(entry.getValue() instanceof Map<?, ?> serverSection)) {
                throw new IllegalArgumentException("servers." + serverName + " 必须为映射结构");
            }

            String rawUrl = asTrimmedString(serverSection.get("url"));
            if (rawUrl.isEmpty()) {
                throw new IllegalArgumentException("servers." + serverName + ".url 不能为空");
            }
            URI baseUri = parseBaseUri(serverName, rawUrl);

            String serverToken = asTrimmedString(serverSection.get("token"));
            String effectiveToken = serverToken.isEmpty() ? defaultToken : serverToken;

            String normalizedName = serverName.toLowerCase(Locale.ROOT);
            result.put(normalizedName, new BackendServerConfig(serverName, normalizedName, baseUri, effectiveToken));
        }
        return result;
    }

    private static URI parseBaseUri(String serverName, String rawUrl) {
        String normalized = rawUrl.endsWith("/") ? rawUrl.substring(0, rawUrl.length() - 1) : rawUrl;
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("servers." + serverName + ".url 格式无效: " + rawUrl, exception);
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("servers." + serverName + ".url 必须为有效的 http/https 地址: " + rawUrl);
        }
        return uri;
    }

    private static String asTrimmedString(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /**
     * 单个受管 Paper 子服的连接配置。
     */
    public record BackendServerConfig(String name, String normalizedName, URI baseUri, String token) {
    }

    /**
     * 不可变配置快照。
     */
    public record ConfigSnapshot(int requestTimeoutMs, Map<String, BackendServerConfig> servers) {

        /**
         * 根据命令中的 server 参数解析目标子服列表；省略或传入 all 时返回全部子服。
         */
        public List<BackendServerConfig> resolveTargets(String rawServerArg) {
            if (rawServerArg == null || rawServerArg.isBlank()
                    || rawServerArg.trim().equalsIgnoreCase(ControlProtocol.ALL_SERVERS_TARGET)) {
                return List.copyOf(servers.values());
            }
            BackendServerConfig matched = servers.get(rawServerArg.trim().toLowerCase(Locale.ROOT));
            return matched == null ? List.of() : List.of(matched);
        }

        /**
         * 返回按配置顺序排列的受管子服名称。
         */
        public List<String> serverNames() {
            return servers.values().stream().map(BackendServerConfig::name).toList();
        }
    }
}

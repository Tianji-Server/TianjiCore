package org.tianjiserver.tianjicore.common.protocol;

import java.util.List;

/**
 * 重载接口响应 DTO。
 */
public record ReloadResponse(
        ReloadStatus status,
        ModuleInfoDto moduleInfo,
        List<ReloadFailureDto> failures,
        List<String> moduleKeys,
        String reloadPluginTarget
) {
    public ReloadResponse {
        failures = failures == null ? List.of() : List.copyOf(failures);
        moduleKeys = moduleKeys == null ? List.of() : List.copyOf(moduleKeys);
    }
}

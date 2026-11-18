package org.tianjiserver.tianjicore.common.protocol;

import java.util.List;

/**
 * 状态查询接口响应 DTO。
 */
public record StatusResponse(List<ModuleInfoDto> modules, String reloadPluginTarget) {
    public StatusResponse {
        modules = modules == null ? List.of() : List.copyOf(modules);
    }
}

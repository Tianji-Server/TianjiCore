package org.tianjiserver.tianjicore.common.protocol;

import java.util.List;

/**
 * 模块开关接口响应 DTO。
 */
public record ToggleResponse(ToggleStatus status, ModuleInfoDto moduleInfo, List<String> toggleableKeys) {
    public ToggleResponse {
        toggleableKeys = toggleableKeys == null ? List.of() : List.copyOf(toggleableKeys);
    }
}

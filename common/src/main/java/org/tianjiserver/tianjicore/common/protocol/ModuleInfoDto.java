package org.tianjiserver.tianjicore.common.protocol;

/**
 * 模块运行状态快照 DTO。
 */
public record ModuleInfoDto(String key, String displayName, boolean enabled, boolean toggleable) {
}

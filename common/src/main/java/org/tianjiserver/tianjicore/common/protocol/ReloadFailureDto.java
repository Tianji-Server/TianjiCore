package org.tianjiserver.tianjicore.common.protocol;

/**
 * 重载失败详情 DTO。
 */
public record ReloadFailureDto(String target, String reason) {
}

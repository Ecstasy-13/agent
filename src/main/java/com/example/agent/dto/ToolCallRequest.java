package com.example.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 工具调用（Function Calling）请求体。
 */
public record ToolCallRequest(
        @NotBlank(message = "消息不能为空") String message
) {
}

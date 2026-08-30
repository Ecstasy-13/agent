package com.example.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * MCP 工具调用请求体。
 *
 * <p>{@code arguments} 为 JSON 字符串，可为空（无参工具）。
 */
public record McpCallRequest(
        @NotBlank(message = "服务器名称不能为空") String server,
        @NotBlank(message = "工具名不能为空") String tool,
        String arguments
) {
}

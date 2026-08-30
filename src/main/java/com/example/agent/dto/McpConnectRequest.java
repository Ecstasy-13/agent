package com.example.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * MCP 服务器连接请求体。
 */
public record McpConnectRequest(
        @NotBlank(message = "服务器名称不能为空") String name,
        String endpoint
) {
}

package com.example.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 对话请求体。
 *
 * <p>对应需求文档 6.1 Controller 接口：
 * <pre>
 * POST /agent/chat
 * { "userId": "001", "message": "介绍一下Spring Boot" }
 * </pre>
 */
public record ChatRequest(
        @NotBlank(message = "userId 不能为空") String userId,
        @NotBlank(message = "message 不能为空") String message
) {
}

package com.example.agent.dto;

/**
 * 对话响应体。
 *
 * <p>对应需求文档 6.1：
 * <pre>
 * { "answer": "Spring Boot..." }
 * </pre>
 */
public record ChatResponse(String answer) {
}

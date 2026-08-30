package com.example.agent.model;

/**
 * 大模型返回的工具调用指令（Function Calling）。
 *
 * <p>对应 OpenAI / Qwen 兼容接口响应中的 {@code tool_calls} 字段，例如：
 * <pre>
 * {
 *   "id": "call_123",
 *   "type": "function",
 *   "function": { "name": "query_order", "arguments": "{\"orderId\":\"10086\"}" }
 * }
 * </pre>
 * 注意 {@code arguments} 是一个 JSON 字符串（不是对象），需要二次解析。
 */
public record ToolCall(String id, String type, Function function) {

    public record Function(String name, String arguments) {
    }
}

package com.example.agent.llm;

import java.util.Map;

/**
 * 工具的声明（发送给大模型的 {@code tools} 列表元素）。
 *
 * <p>结构对齐 OpenAI / Qwen 兼容接口：
 * <pre>
 * {
 *   "type": "function",
 *   "function": {
 *     "name": "query_order",
 *     "description": "查询订单状态",
 *     "parameters": { ... JSON Schema ... }
 *   }
 * }
 * </pre>
 */
public record ToolDefinition(String type, Function function) {

    public record Function(String name, String description, Map<String, Object> parameters) {
    }

    public static ToolDefinition of(String name, String description, Map<String, Object> parameters) {
        return new ToolDefinition("function", new Function(name, description, parameters));
    }
}

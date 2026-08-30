package com.example.agent.llm;

import com.example.agent.model.Message;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 通义千问 Chat Completions 请求体。
 *
 * <p>DashScope 提供 OpenAI 兼容接口，请求结构对齐 OpenAI ChatCompletion。
 * 当 {@code tools} 非空时启用 Function Calling，{@code toolChoice="auto"} 让模型自行决定是否调工具。
 */
public record QwenChatRequest(
        String model,
        List<Message> messages,
        double temperature,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ToolDefinition> tools,
        @JsonInclude(JsonInclude.Include.NON_NULL) String toolChoice
) {

    /** 普通对话（不带工具）的便捷构造器 */
    public QwenChatRequest(String model, List<Message> messages, double temperature) {
        this(model, messages, temperature, null, null);
    }
}

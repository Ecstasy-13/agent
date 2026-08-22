package com.example.agent.llm;

import com.example.agent.model.Message;

import java.util.List;

/**
 * 通义千问 Chat Completions 请求体。
 *
 * <p>DashScope 提供 OpenAI 兼容接口，请求结构对齐 OpenAI ChatCompletion。
 */
public record QwenChatRequest(String model, List<Message> messages, double temperature) {
}

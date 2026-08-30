package com.example.agent.llm;

import com.example.agent.model.ToolCall;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 通义千问 Chat Completions 响应体。
 *
 * <p>只映射业务所需字段，其余字段由 Jackson 自动忽略。
 * 启用 Function Calling 时，模型的回复可能不包含 {@code content}，
 * 而是通过 {@code message.tool_calls} 返回要调用的工具。
 */
public record QwenChatResponse(List<Choice> choices, QwenUsage usage) {

    public record Choice(QwenMessage message, @JsonProperty("finish_reason") String finishReason) {
    }

    public record QwenMessage(
            String role,
            String content,
            @JsonProperty("tool_calls") List<ToolCall> toolCalls
    ) {
    }

    public record QwenUsage(@JsonProperty("prompt_tokens") Integer promptTokens,
                            @JsonProperty("completion_tokens") Integer completionTokens,
                            @JsonProperty("total_tokens") Integer totalTokens) {
    }

    /** 取出第一条回复消息（可能为 null） */
    public QwenMessage firstMessage() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).message();
    }

    /** 便捷方法：取出第一条回复的文本内容 */
    public String firstContent() {
        QwenMessage message = firstMessage();
        return message == null ? null : message.content();
    }

    /** 便捷方法：取出第一条回复里的工具调用列表 */
    public List<ToolCall> firstToolCalls() {
        QwenMessage message = firstMessage();
        return message == null ? null : message.toolCalls();
    }
}

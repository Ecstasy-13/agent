package com.example.agent.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 通义千问 Chat Completions 响应体。
 *
 * <p>只映射业务所需字段，其余字段由 Jackson 自动忽略。
 */
public record QwenChatResponse(List<Choice> choices, QwenUsage usage) {

    public record Choice(QwenMessage message, @JsonProperty("finish_reason") String finishReason) {
    }

    public record QwenMessage(String role, String content) {
    }

    public record QwenUsage(@JsonProperty("prompt_tokens") Integer promptTokens,
                            @JsonProperty("completion_tokens") Integer completionTokens,
                            @JsonProperty("total_tokens") Integer totalTokens) {
    }

    /**
     * 便捷方法：取出第一条回复的文本内容。
     */
    public String firstContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Choice choice = choices.get(0);
        return choice.message() == null ? null : choice.message().content();
    }
}

package com.example.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 知识库提问请求体。
 */
public record KnowledgeAskRequest(
        @NotBlank(message = "问题不能为空") String question
) {
}

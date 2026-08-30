package com.example.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 知识库文档上传请求体。
 */
public record KnowledgeUploadRequest(
        @NotBlank(message = "文档名不能为空") String name,
        @NotBlank(message = "文档内容不能为空") String content
) {
}

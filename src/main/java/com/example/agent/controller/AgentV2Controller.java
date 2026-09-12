package com.example.agent.controller;

import com.example.agent.agent.model.AgentResponse;
import com.example.agent.application.AgentApplicationService;
import com.example.agent.dto.AgentChatRequest;
import com.example.agent.dto.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent V2 HTTP Controller。
 *
 * <p>Controller 的职责应该非常简单：
 *
 * HTTP Request
 *      ↓
 * 参数校验
 *      ↓
 * ApplicationService
 *      ↓
 * HTTP Response
 *
 * <p>Controller 不应该知道：
 *
 * Qwen
 * Spring AI
 * Memory 怎么存
 * Prompt 怎么构造
 * Agent Runtime 怎么执行
 */
@RestController
@RequestMapping("/api/v2/agent")
public class AgentV2Controller {

    /**
     * Agent 应用服务。
     */
    private final AgentApplicationService applicationService;

    public AgentV2Controller(AgentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 发起一次 Agent Chat。
     *
     * @Valid 会触发 AgentChatRequest 中：
     *
     * @NotBlank
     *
     * 等 Bean Validation。
     */
    @PostMapping("/chat")
    public ApiResponse<AgentResponse> chat(@Valid @RequestBody AgentChatRequest request) {
        AgentResponse response = applicationService
                .chat(request.userId(), request.conversationId(), request.message());
        return ApiResponse.ok(response);
    }

    /**
     * 清除某个用户某个 Conversation
     * 的短期聊天上下文。
     *
     * <p>这里只清 Conversation Memory，
     * 不清除用户 Long Memory。
     */
    @DeleteMapping("/users/{userId}/conversations/{conversationId}")
    public ApiResponse<Void> clearConversation(@PathVariable String userId, @PathVariable String conversationId) {

        applicationService.clearConversation(userId, conversationId);

        return ApiResponse.ok(null);
    }
}

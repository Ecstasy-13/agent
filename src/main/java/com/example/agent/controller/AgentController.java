package com.example.agent.controller;

import com.example.agent.agent.SimpleAgent;
import com.example.agent.dto.ApiResponse;
import com.example.agent.dto.ChatRequest;
import com.example.agent.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 对话接口。
 *
 * <p>对应需求文档 6.1 Controller 模块：
 * <pre>
 * POST /agent/chat
 * 请求：{ "userId": "001", "message": "介绍一下Spring Boot" }
 * 响应：{ "code": 200, "message": "success", "data": { "answer": "..." } }
 * </pre>
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final SimpleAgent agent;

    public AgentController(SimpleAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String answer = agent.chat(request.userId(), request.message());
        return ApiResponse.ok(new ChatResponse(answer));
    }
}

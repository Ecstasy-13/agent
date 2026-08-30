package com.example.agent.controller;

import com.example.agent.agent.SimpleAgent;
import com.example.agent.dto.ApiResponse;
import com.example.agent.dto.ChatRequest;
import com.example.agent.dto.ChatResponse;
import com.example.agent.dto.MemoryItem;
import com.example.agent.memory.LongMemoryService;
import com.example.agent.memory.ShortMemory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 对话接口。
 *
 * <p>对应需求文档 6.1 Controller 模块：
 * <pre>
 * POST   /agent/chat             —— 对话
 * GET    /agent/memory/{userId}  —— 查询用户长期记忆画像（供前端记忆面板展示）
 * DELETE /agent/session/{userId} —— 清空短期会话（开始新对话，保留长期画像）
 * DELETE /agent/memory/{userId}  —— 清空全部记忆（短期会话 + 长期画像）
 * </pre>
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final SimpleAgent agent;
    private final ShortMemory shortMemory;
    private final LongMemoryService longMemoryService;

    public AgentController(SimpleAgent agent,
                           ShortMemory shortMemory,
                           LongMemoryService longMemoryService) {
        this.agent = agent;
        this.shortMemory = shortMemory;
        this.longMemoryService = longMemoryService;
    }

    /** 对话：用户提问 -> Agent 回复 */
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String answer = agent.chat(request.userId(), request.message());
        return ApiResponse.ok(new ChatResponse(answer));
    }

    /** 查询用户长期记忆画像（键值对列表） */
    @GetMapping("/memory/{userId}")
    public ApiResponse<List<MemoryItem>> getMemory(@PathVariable String userId) {
        return ApiResponse.ok(longMemoryService.getUserMemories(userId));
    }

    /** 清空短期会话（开始新对话，长期画像保留） */
    @DeleteMapping("/session/{userId}")
    public ApiResponse<Void> clearSession(@PathVariable String userId) {
        shortMemory.clear(userId);
        return ApiResponse.ok(null);
    }

    /** 清空用户全部记忆（短期会话 + 长期画像） */
    @DeleteMapping("/memory/{userId}")
    public ApiResponse<Void> clearMemory(@PathVariable String userId) {
        shortMemory.clear(userId);
        longMemoryService.clearUserMemory(userId);
        return ApiResponse.ok(null);
    }
}

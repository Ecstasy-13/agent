package com.example.agent.application;

import com.example.agent.agent.model.AgentRequest;
import com.example.agent.agent.model.AgentResponse;
import com.example.agent.agent.runtime.AgentRuntime;
import com.example.agent.memory.ConversationMemory;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Agent 应用服务。
 *
 * <p>这一层位于：
 * <p>
 * Controller
 * ↓
 * ApplicationService
 * ↓
 * AgentRuntime
 *
 * <p>ApplicationService 解决的是：
 * <p>
 * “应用业务如何发起一次 Agent Run”
 * <p>
 * 而 AgentRuntime 解决的是：
 * <p>
 * “Agent 内部到底如何运行”
 *
 * <p>目前 V1 这一层还比较薄，
 * 以后会逐渐加入：
 * <p>
 * 当前登录用户
 * Conversation 创建
 * 权限
 * Quota
 * Agent 类型选择
 * 等应用逻辑。
 */
@Service
public class AgentApplicationService {

    /**
     * Agent 执行引擎。
     */
    private final AgentRuntime agentRuntime;

    /**
     * Conversation Memory。
     * <p>
     * 当前主要供 clearConversation() 使用。
     */
    private final ConversationMemory conversationMemory;

    public AgentApplicationService(AgentRuntime agentRuntime, ConversationMemory conversationMemory) {

        this.agentRuntime = agentRuntime;

        this.conversationMemory = conversationMemory;
    }

    /**
     * 发起聊天。
     *
     * @param userId         当前用户
     * @param conversationId 当前会话。
     *                       <p>
     *                       第一次聊天允许传 null。
     * @param message        当前用户输入
     */
    public AgentResponse chat(String userId, String conversationId, String message) {

        /*
         * 如果前端第一次聊天，
         * 还没有 conversationId，
         * 就由后端生成。
         */
        String actualConversationId = normalizeConversationId(conversationId);

        /*
         * HTTP DTO 到这里被转换成
         * Agent Runtime 自己的领域请求。
         */
        AgentRequest request = new AgentRequest(userId, actualConversationId, message);

        /*
         * ApplicationService 不关心
         * Runtime 内部执行细节。
         */
        return agentRuntime.run(request);
    }

    /**
     * 清理指定 Conversation 的短期记忆。
     */
    public void clearConversation(String userId, String conversationId) {

        conversationMemory.clear(userId, conversationId);
    }

    /**
     * 如果客户端还没有 Conversation，
     * 后端自动创建。
     */
    private String normalizeConversationId(String conversationId) {

        if (conversationId != null && !conversationId.isBlank()) {

            return conversationId;
        }

        return "conv-" + UUID.randomUUID();
    }
}

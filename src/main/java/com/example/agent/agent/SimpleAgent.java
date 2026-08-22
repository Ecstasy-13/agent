package com.example.agent.agent;

import com.example.agent.llm.QwenClient;
import com.example.agent.memory.LongMemoryService;
import com.example.agent.memory.ShortMemory;
import com.example.agent.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 核心控制类，负责编排一次完整对话流程。
 *
 * <p>对应需求文档 4.2 / 6.2 Agent 模块核心类 {@code SimpleAgent}。
 * 流程：接收用户请求 -> 构造 Prompt -> 调用大模型 -> 管理上下文 -> 返回结果。
 */
@Service
public class SimpleAgent {

    private static final Logger log = LoggerFactory.getLogger(SimpleAgent.class);

    /** 系统角色提示词，定义 Agent 的定位与行为 */
    private static final String SYSTEM_PROMPT = """
            你是一个智能助理 Agent，能够进行自然语言对话，并会根据对话上下文给出准确、有帮助的回答。
            回答请使用简洁、清晰的语言。
            """;

    private final QwenClient qwenClient;
    private final ShortMemory shortMemory;
    private final LongMemoryService longMemoryService;

    public SimpleAgent(QwenClient qwenClient,
                       ShortMemory shortMemory,
                       LongMemoryService longMemoryService) {
        this.qwenClient = qwenClient;
        this.shortMemory = shortMemory;
        this.longMemoryService = longMemoryService;
    }

    /**
     * 处理一轮用户对话，返回 Agent 回复。
     *
     * @param userId  用户标识（用于区分不同用户的会话与画像）
     * @param message 用户输入
     * @return Agent 回复文本
     */
    public String chat(String userId, String message) {
        // 1. 记录用户消息到短期记忆
        shortMemory.add(userId, Message.user(message));

        // 2. 构造 Prompt：system 提示 + 用户长期画像 + 会话历史
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(buildSystemPrompt(userId)));
        messages.addAll(shortMemory.getHistory(userId));

        // 3. 调用大模型
        String answer = qwenClient.chat(messages);

        // 4. 记录 Agent 回复到短期记忆
        shortMemory.add(userId, Message.assistant(answer));

        // 5. 异步抽取并保存长期记忆
        longMemoryService.extractAndSave(userId, message);

        log.info("Agent 回复完成，userId={}", userId);
        return answer;
    }

    /**
     * 构造系统提示词：基础角色说明 + 已知用户画像。
     */
    private String buildSystemPrompt(String userId) {
        String profile = longMemoryService.getUserProfile(userId);
        if (profile == null || profile.isBlank()) {
            return SYSTEM_PROMPT;
        }
        return SYSTEM_PROMPT + "\n\n已知用户信息：" + profile
                + "\n请结合上述用户信息，提供个性化回答。";
    }
}

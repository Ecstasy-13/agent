package com.example.agent.prompt;

import com.example.agent.model.Message;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent Prompt Factory。
 *
 * <p>V2 开始，
 * PromptFactory 不再负责：
 * <p>
 * History Window
 * <p>
 * 那属于 ContextWindowService 的职责。
 *
 * <p>PromptFactory 现在只负责：
 * <p>
 * “Agent System Prompt 应该是什么？”
 */
@Component
public class AgentPromptFactory {
    private static final String BASE_SYSTEM_PROMPT = """
            你是企业智能客服 Agent。

            你的目标是准确、清晰地帮助用户解决问题。

            要求：
            1. 不确定的信息不要编造；
            2. 缺少必要信息时，应明确说明；
            3. 回答优先简洁、清晰；
            4. 不要声称执行了实际上没有执行的操作。
            """;
    /**
     * 创建本轮 System Message。
     *
     * @param userProfile User 级 Long Memory。
     */
    public Message buildSystemMessage(String userProfile) {
        return Message.system(buildSystemPrompt(userProfile));
    }

    private String buildSystemPrompt(String userProfile) {
        if (userProfile == null || userProfile.isBlank()) {
            return BASE_SYSTEM_PROMPT;
        }
        return BASE_SYSTEM_PROMPT
                + """

                                
                以下是系统已经记录的用户长期信息：
                """
                + userProfile
                + """

                                
                可以在确有帮助时结合这些信息回答，
                但不要主动暴露与当前问题无关的用户信息。
                """;
    }
}

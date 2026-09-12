package com.example.agent.prompt;

import com.example.agent.model.Message;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent Prompt / Context 构造器。
 *
 * <p>职责：
 * <p>
 * 把 Runtime 已经准备好的：
 * <p>
 * 用户长期画像
 * +
 * Conversation 历史
 * +
 * 当前用户输入
 * <p>
 * 转换成最终给 LLM 的 Message List。
 *
 * <p>注意这个类：
 * <p>
 * 不查询数据库；
 * 不查询 Redis；
 * 不调用 LLM。
 * <p>
 * 它只负责：
 * <p>
 * “拼上下文”。
 */
@Component
public class AgentPromptFactory {

    /**
     * Agent 基础 System Prompt。
     * <p>
     * V1 暂时写在代码里。
     * <p>
     * 后面可以考虑：
     * 配置化、版本管理、Prompt Template。
     */
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
     * 构造本轮完整 LLM 上下文。
     *
     * @param userProfile    LongMemoryService 查询得到的长期用户画像。
     * @param history        当前 Conversation 已经存在的历史消息。
     * @param currentMessage 当前用户这一轮新输入。
     */
    public List<Message> build(String userProfile, List<Message> history, String currentMessage) {

        List<Message> messages = new ArrayList<>();

        /*
         * 第一条永远放 System Message。
         *
         * System Message 负责定义：
         *
         * Agent 身份
         * 行为规则
         * 用户长期信息
         */
        messages.add(Message.system(buildSystemPrompt(userProfile)));

        /*
         * 然后追加 Conversation History。
         *
         * 例如：
         *
         * USER: 我叫小明
         * ASSISTANT: 你好小明
         * USER: 我学 Java
         * ASSISTANT: ...
         */
        messages.addAll(history);

        /*
         * 最后追加这一轮新的 User Message。
         *
         * 注意当前消息目前还没有真正保存进 Memory。
         *
         * 等 LLM 成功返回以后，
         * Runtime 再保存 User + Assistant 完整 Turn。
         */
        messages.add(Message.user(currentMessage));

        /*
         * 返回不可修改列表。
         */
        return List.copyOf(messages);
    }

    /**
     * 创建 System Prompt。
     *
     * <p>如果没有 Long Memory，
     * 直接使用基础 Prompt。
     *
     * <p>如果存在 Long Memory，
     * 再将用户画像加入 Prompt。
     */
    private String buildSystemPrompt(String userProfile) {

        if (userProfile == null || userProfile.isBlank()) {

            return BASE_SYSTEM_PROMPT;
        }

        return BASE_SYSTEM_PROMPT + """

                                
                以下是系统已经记录的用户长期信息：
                """ + userProfile + """

                                
                可以在确有帮助时结合这些信息回答，
                但不要主动暴露与当前问题无关的用户信息。
                """;
    }
}

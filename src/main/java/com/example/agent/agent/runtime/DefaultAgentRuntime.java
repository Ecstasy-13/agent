package com.example.agent.agent.runtime;

import com.example.agent.agent.context.ContextWindow;
import com.example.agent.agent.context.ContextWindowService;
import com.example.agent.agent.model.AgentRequest;
import com.example.agent.agent.model.AgentResponse;
import com.example.agent.agent.model.AgentStepType;
import com.example.agent.llm.ModelCallResult;
import com.example.agent.llm.ModelService;
import com.example.agent.memory.ConversationMemory;
import com.example.agent.memory.LongMemoryService;
import com.example.agent.model.Message;
import com.example.agent.prompt.AgentPromptFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AgentRuntime 的默认实现。
 *
 * <p>这个类负责真正编排一次 Agent Run。
 *
 * <p>V1 完整执行链：
 *
 * <pre>
 * AgentRequest
 *      ↓
 * 创建 AgentRunContext
 *      ↓
 * MEMORY_LOAD
 *      ↓
 * CONTEXT_BUILD
 *      ↓
 * LLM_CALL
 *      ↓
 * MEMORY_SAVE
 *      ↓
 * AgentResponse
 * </pre>
 *
 * <p>注意：
 * Runtime 负责“编排”，
 * 而不是亲自实现所有能力。
 * <p>
 * 它不会：
 * <p>
 * 自己查 MySQL；
 * 自己操作 Redis；
 * 自己调用 DashScope HTTP；
 * 自己拼 System Prompt。
 * <p>
 * 它依赖其他组件完成这些事情。
 */
@Service
public class DefaultAgentRuntime implements AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentRuntime.class);

    /**
     * Conversation 级短期记忆。
     * <p>
     * Runtime 不知道具体实现是：
     * JVM 还是 Redis。
     */
    private final ConversationMemory conversationMemory;

    /**
     * User 级长期记忆服务。
     * <p>
     * 当前底层还是 MySQL。
     */
    private final LongMemoryService longMemoryService;

    /**
     * 专门负责构造 LLM Context。
     */
    private final AgentPromptFactory promptFactory;

    private final ContextWindowService contextWindowService;

    /**
     * 大模型访问统一抽象。
     * <p>
     * Runtime 不知道实际是 Qwen。
     */
    private final ModelService modelService;

    /**
     * 使用构造器注入依赖。
     * <p>
     * 不推荐：
     *
     * @Autowired private XxxService service;
     * <p>
     * 构造器注入：
     * <p>
     * 1. 依赖更明确；
     * 2. 字段可以 final；
     * 3. 更方便单元测试。
     */
    public DefaultAgentRuntime(
            ConversationMemory conversationMemory,
            LongMemoryService longMemoryService,
            AgentPromptFactory promptFactory,
            ContextWindowService contextWindowService,
            ModelService modelService) {

        this.conversationMemory = conversationMemory;

        this.longMemoryService = longMemoryService;

        this.promptFactory = promptFactory;

        this.contextWindowService = contextWindowService;

        this.modelService = modelService;
    }

    /**
     * 执行一次完整 Agent Run。
     */
    @Override
    public AgentResponse run(AgentRequest request) {

        /*
         * ==================================================
         * 0. 创建本次 Agent Run Context
         * ==================================================
         *
         * Context 会：
         *
         * 生成 runId
         * 记录开始时间
         * 保存 AgentStep
         * 保存 Usage
         */
        AgentRunContext context = AgentRunContext.start(request);

        log.info("Agent run started, runId={}, userId={}, conversationId={}", context.getRunId(), request.userId(), request.conversationId());

        /*
         * ==================================================
         * Step 1：MEMORY_LOAD
         * ==================================================
         */

        long stepStart = System.nanoTime();

        /*
         * 获取当前 Conversation 的聊天历史。
         *
         * 注意：
         * 是 conversationId 级别，
         * 而不是整个 user 的全部历史。
         */
        List<Message> history = conversationMemory.getHistory(request.userId(), request.conversationId());

        /*
         * 获取 User 级长期画像。
         *
         * Long Memory 与 Conversation 无关，
         * 同一个用户不同 Conversation
         * 都可以共享长期画像。
         */
        String userProfile = longMemoryService.getUserProfile(request.userId());

        /*
         * MEMORY_LOAD 完成后记录 Step。
         *
         * 注意：
         * 我们只记录数量等轻量 metadata，
         * 不直接把所有聊天内容放进 Trace。
         */
        context.addStep(AgentStepType.MEMORY_LOAD, "load-memory", elapsedMs(stepStart), Map.of("historyMessageCount", history.size(),

                "hasLongTermProfile", userProfile != null && !userProfile.isBlank()));

        /*
         * ==================================================
         * Step 2：CONTEXT_BUILD
         * ==================================================
         *
         * V2 最大变化：
         *
         * Redis History
         * 不再直接全部发送给 LLM。
         *
         * 必须经过 Token-aware Context Window。
         */

        stepStart = System.nanoTime();

//        List<Message> promptMessages = promptFactory.build(userProfile, history, request.message());
        Message systemMessage = promptFactory.buildSystemMessage(userProfile);
        /*
         * 当前用户 Message。
         *
         * 注意：
         * 当前消息暂时还没有存进 Redis，
         * Model 成功以后再保存完整 Turn。
         */
        Message currentUserMessage = Message.user(request.message());
        /*
         * 根据：
         *
         * Token Budget
         * +
         * Recent History
         * +
         * Complete Turn
         *
         * 选择最终上下文。
         */
        ContextWindow contextWindow =
                contextWindowService.build(
                        systemMessage,
                        history,
                        currentUserMessage
                );
        List<Message> promptMessages = contextWindow.messages();

        context.addStep(AgentStepType.CONTEXT_BUILD, "build-context", elapsedMs(stepStart),
                Map.of(
                        /*
                         * Redis 原本保存多少历史。
                         */
                        "totalHistoryMessages",
                        contextWindow.totalHistoryMessages(),
                        /*
                         * 本轮实际使用多少历史。
                         */
                        "selectedHistoryMessages",
                        contextWindow.selectedHistoryMessages(),
                        /*
                         * 因 Token Budget 丢弃多少。
                         */
                        "droppedHistoryMessages",
                        contextWindow.droppedHistoryMessages(),
                        /*
                         * 最终 Context 预估 Token。
                         */
                        "estimatedPromptTokens",
                        contextWindow.estimatedTokens(),
                        /*
                         * 最终真正发送的 Message 数。
                         *
                         * 包含：
                         * System + History + Current User。
                         */
                        "promptMessageCount",promptMessages.size()
                )
        );

        /*
         * ==================================================
         * Step 3：LLM_CALL
         * ==================================================
         */

        stepStart = System.nanoTime();

        /*
         * 调用大模型。
         *
         * Runtime 只认识 ModelService。
         *
         * 实际底层：
         *
         * ModelService
         *      ↓
         * SpringAiModelService
         *      ↓
         * ChatClient
         *      ↓
         * DashScopeChatModel
         *      ↓
         * Qwen
         */
        ModelCallResult modelResult = modelService.chat(promptMessages);

        /*
         * 保存 Token Usage。
         */
        context.setUsage(modelResult.usage());

        context.addStep(AgentStepType.LLM_CALL, "chat-model", elapsedMs(stepStart), Map.of(
                /*
                 * 暂时记录回答字符数。
                 *
                 * 不直接把完整 answer 放入 attributes，
                 * 避免 Trace 中存储大量敏感内容。
                 */
                "answerLength", modelResult.content().length()));

        /*
         * ==================================================
         * Step 4：MEMORY_SAVE
         * ==================================================
         */

        stepStart = System.nanoTime();

        /*
         * 为什么现在才保存 User Message？
         *
         * 因为我们希望 V1 ConversationMemory
         * 只保存完整成功 Turn。
         *
         * 也就是：
         *
         * USER
         * +
         * ASSISTANT
         *
         * 如果模型调用失败，
         * 这里根本不会执行。
         */
        conversationMemory.addAll(request.userId(), request.conversationId(), List.of(Message.user(request.message()),

                Message.assistant(modelResult.content())));

        /*
         * 触发 Long Memory 抽取。
         *
         * LongMemoryService.extractAndSave()
         * 本身使用 @Async，
         * 所以长期记忆抽取失败或比较慢时，
         * 不应该阻塞主回答。
         */
        longMemoryService.extractAndSave(request.userId(), request.message());

        context.addStep(AgentStepType.MEMORY_SAVE, "save-memory", elapsedMs(stepStart), Map.of("savedMessages", 2,

                "longTermExtractionScheduled", true));

        /*
         * ==================================================
         * 5. Agent Run 完成
         * ==================================================
         *
         * 把可变的 AgentRunContext
         * 转成最终不可变 AgentResponse。
         */
        AgentResponse response = context.complete(modelResult.content());

        log.info("Agent run completed, runId={}, conversationId={}, durationMs={}", response.runId(), response.conversationId(), response.totalDurationMs());

        return response;
    }

    /**
     * 根据一个 nanoTime 起点计算耗时。
     *
     * @param startNanos Step 开始时调用 System.nanoTime()
     * @return 已经过的毫秒数
     */
    private long elapsedMs(long startNanos) {

        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}

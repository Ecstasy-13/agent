package com.example.agent.agent.model;

import java.util.List;

/**
 * 一次 Agent Run 执行完成后的标准返回结果。
 *
 * <p>它与普通 ChatResponse 最大的区别是：
 *
 * 普通聊天只关心：
 *
 * answer
 *
 * Agent Runtime 还关心：
 *
 * runId
 * conversationId
 * steps
 * token usage
 * latency
 *
 * @param runId
 *        本次 Agent Run 的唯一 ID。
 *
 *        每发送一次用户消息，
 *        都会生成一个新的 runId。
 *
 *        例如：
 *
 *        conversation = conv-001
 *
 *        第一次提问：
 *        runId = run-a
 *
 *        第二次提问：
 *        runId = run-b
 *
 *        第三次提问：
 *        runId = run-c
 *
 * @param conversationId
 *        当前 Run 所属的 Conversation。
 *
 *        多次 Run 可以属于同一个 Conversation。
 *
 * @param answer
 *        Agent 最终返回给用户的自然语言答案。
 *
 *        V1 就是模型输出。
 *
 *        后面 Tool Agent 中，
 *        它会是 Agent Loop 最后一轮产生的 Final Answer。
 *
 * @param steps
 *        本次 Agent Run 的执行步骤列表。
 *
 *        用于描述本轮 Agent 到底做过什么。
 *
 * @param usage
 *        本次执行产生的 LLM Token 使用情况。
 *
 *        后面会用于：
 *        Cost、Quota、Metrics、Evaluation。
 *
 * @param totalDurationMs
 *        整个 Agent Run 从开始到结束的总耗时。
 *
 *        注意：
 *        它不是单独的 LLM latency。
 *
 *        它包括：
 *        Memory + Context + LLM + Save 等整个流程。
 */
public record AgentResponse(

        String runId,

        String conversationId,

        String answer,

        List<AgentStep> steps,

        AgentUsage usage,

        long totalDurationMs

) {

    /**
     * 确保 steps 是不可修改列表。
     *
     * <p>如果直接保存调用方传入的 ArrayList，
     * AgentResponse 创建以后，
     * 外部仍然可能修改它。
     */
    public AgentResponse {

        steps = steps == null
                ? List.of()
                : List.copyOf(steps);

        /*
         * usage 理论上 Runtime 总会传入。
         *
         * 这里再做一次防御式处理，
         * 避免 JSON 响应里 usage 整体为 null。
         */
        usage = usage == null
                ? AgentUsage.empty()
                : usage;
    }
}

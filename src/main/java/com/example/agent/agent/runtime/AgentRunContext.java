package com.example.agent.agent.runtime;

import com.example.agent.agent.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一次 Agent Run 的运行时上下文。
 *
 * <p>AgentRequest / AgentResponse 属于不可变的数据载体。
 * <p>
 * 但 Agent 在执行过程中，需要不断积累状态，例如：
 *
 * <pre>
 * 创建 runId
 *      ↓
 * 加入 MEMORY_LOAD Step
 *      ↓
 * 加入 CONTEXT_BUILD Step
 *      ↓
 * 保存 Usage
 *      ↓
 * 加入 LLM_CALL Step
 *      ↓
 * 加入 MEMORY_SAVE Step
 *      ↓
 * complete()
 * </pre>
 * <p>
 * 因此这里使用普通 class，
 * 而不是 record。
 *
 * <p>以后 Tool Calling 阶段，
 * AgentRunContext 还可能增加：
 * <p>
 * currentRound
 * toolCalls
 * retrievedDocuments
 * tokenBudget
 * 等运行时数据。
 */

public class AgentRunContext {

    /**
     * 本次 Agent Run 的唯一 ID。
     * <p>
     * 每调用一次 AgentRuntime.run()，
     * 都生成一个新的 runId。
     */
    private final String runId;

    /**
     * 本次运行原始请求。
     * <p>
     * 包含：
     * userId
     * conversationId
     * message
     */
    private final AgentRequest request;

    /**
     * Agent Run 开始时的单调时钟值。
     * <p>
     * 注意这里不用 System.currentTimeMillis()。
     * <p>
     * System.currentTimeMillis()
     * 更适合表示：
     * <p>
     * “现在是什么时间？”
     * <p>
     * System.nanoTime()
     * 更适合测量：
     * <p>
     * “一段代码执行了多久？”
     */
    private final long startNanos;

    /**
     * 当前 Run 已经执行完成的 Step。
     * <p>
     * Agent 每完成一个阶段，
     * 就向这里追加 AgentStep。
     */
    private final List<AgentStep> steps = new ArrayList<>();

    /**
     * 当前 Run 的 Token 使用信息。
     * <p>
     * 初始时模型还没有调用，
     * 所以使用 empty()。
     */
    private AgentUsage usage = AgentUsage.empty();

    /**
     * 构造器设为 private。
     * <p>
     * 外部统一通过：
     * <p>
     * AgentRunContext.start(request)
     * <p>
     * 创建 Context。
     */
    private AgentRunContext(AgentRequest request) {

        /*
         * V1 使用 UUID 即可。
         *
         * 以后也可以换 UUIDv7、
         * Snowflake、TraceId 等。
         */
        this.runId = "run-" + UUID.randomUUID();

        this.request = request;

        this.startNanos = System.nanoTime();
    }

    /**
     * 开始一次新的 Agent Run。
     */
    public static AgentRunContext start(AgentRequest request) {
        return new AgentRunContext(request);
    }

    /**
     * 向当前 Run 添加一个执行步骤。
     *
     * @param type       Step 标准类型
     * @param name       Step 具体名称
     * @param durationMs Step 耗时
     * @param attributes Step 附加信息
     */
    public void addStep(AgentStepType type, String name, long durationMs, Map<String, Object> attributes) {

        /*
         * Step 编号根据当前列表大小自动生成。
         *
         * 第一个：
         * size = 0
         * index = 1
         *
         * 第二个：
         * size = 1
         * index = 2
         */
        AgentStep step = new AgentStep(steps.size() + 1, type, name, durationMs, attributes);

        steps.add(step);
    }

    /**
     * 保存本次 Run 的模型 Token Usage。
     */
    public void setUsage(AgentUsage usage) {
        this.usage = usage == null ? AgentUsage.empty() : usage;
    }

    public String getRunId() {
        return runId;
    }

    public AgentRequest getRequest() {
        return request;
    }

    /**
     * 不直接：
     * <p>
     * return steps;
     * <p>
     * 避免外部拿到内部 ArrayList 后修改 Context。
     */
    public List<AgentStep> getSteps() {
        return List.copyOf(steps);
    }

    public AgentUsage getUsage() {
        return usage;
    }

    /**
     * 返回整个 Agent Run 当前已经运行的时间。
     */
    public long getTotalDurationMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * 完成 Agent Run。
     *
     * <p>把运行过程中的可变 Context
     * 转换成最终不可变 AgentResponse。
     *
     * @param answer Agent 最终答案
     */
    public AgentResponse complete(String answer) {

        return new AgentResponse(runId, request.conversationId(), answer, getSteps(), usage, getTotalDurationMs());
    }

}

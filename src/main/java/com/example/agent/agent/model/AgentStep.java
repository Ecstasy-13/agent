package com.example.agent.agent.model;

import java.util.Map;

/**
 * 描述一次 Agent Run 中的某一个执行步骤。
 *
 * <p>例如一次 Run：
 *
 * <pre>
 * Step 1  MEMORY_LOAD
 * Step 2  CONTEXT_BUILD
 * Step 3  LLM_CALL
 * Step 4  MEMORY_SAVE
 * </pre>
 *
 * <p>AgentStep 的目的不是保存完整业务数据，
 * 而是提供一份轻量级“执行轨迹”。
 * <p>
 * 后面可以进一步用于：
 * <p>
 * Trace
 * Debug
 * Observability
 * Evaluation
 * 前端执行过程展示
 *
 * @param index      当前步骤在一次 Agent Run 中的顺序。
 *                   <p>
 *                   从 1 开始。
 *                   <p>
 *                   比如：
 *                   1 = MEMORY_LOAD
 *                   2 = CONTEXT_BUILD
 *                   3 = LLM_CALL
 * @param type       步骤的标准类型。
 *                   <p>
 *                   使用 enum 而不是 String，
 *                   可以避免：
 *                   <p>
 *                   "LLM"
 *                   "llm"
 *                   "LLM_CALL"
 *                   <p>
 *                   这种命名不统一的问题。
 * @param name       更具体的人类可读步骤名称。
 *                   <p>
 *                   type 描述“属于什么类型”，
 *                   name 描述“这一具体步骤叫什么”。
 *                   <p>
 *                   比如：
 *                   <p>
 *                   type = MEMORY_LOAD
 *                   name = "load-memory"
 * @param durationMs 当前步骤执行耗时，单位毫秒。
 *                   <p>
 *                   后面可以用来分析：
 *                   LLM 慢？
 *                   RAG 慢？
 *                   Tool 慢？
 *                   MCP 慢？
 * @param attributes 当前步骤附带的一些轻量元数据。
 *                   <p>
 *                   不同 Step 可以记录不同信息。
 *                   <p>
 *                   例如 MEMORY_LOAD：
 *                   <p>
 *                   historyMessageCount = 4
 *                   hasLongTermProfile = true
 *                   <p>
 *                   例如以后 TOOL_CALL：
 *                   <p>
 *                   toolName = query_order
 *                   success = true
 *                   <p>
 *                   使用 Map 的原因是：
 *                   不同 Step 的元数据结构差异比较大，
 *                   V1 没必要给每种 Step 单独创建 DTO。
 */
public record AgentStep(

        int index,

        AgentStepType type,

        String name,

        long durationMs,

        Map<String, Object> attributes

) {

    /**
     * Record 的紧凑构造器。
     *
     * <p>这里主要处理 attributes。
     */
    public AgentStep {

        /**
         * 如果调用方没有 attributes，
         * 就统一转换为空 Map。
         *
         * 避免其他地方出现：
         *
         * if (step.attributes() != null)
         **/
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);

        /*
         * Map.copyOf 的另一个作用是：
         *
         * 把外面传进来的 Map 复制成不可修改 Map。
         *
         * 防止：
         *
         * 创建 AgentStep 后，
         * 外部又偷偷修改 attributes。
         *
         * 这样 AgentStep 更符合 immutable value object
         * 的设计。
         */
    }
}

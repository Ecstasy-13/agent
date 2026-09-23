package com.example.agent.agent.model;

/**
 * 一次 Agent Run 中的大模型 Token 使用情况。
 *
 * <p>目前 V1 一次 Agent Run 只调用一次 LLM，
 * 所以这里记录的就是这一轮模型调用的 Usage。
 *
 * <p>后面实现 Tool Calling Agent Loop 后，
 * 一次 Agent Run 可能调用模型多次：
 *
 * <pre>
 * Run
 *  │
 *  ├── LLM Call 1
 *  ├── Tool Call
 *  ├── LLM Call 2
 *  ├── Tool Call
 *  └── LLM Call 3
 * </pre>
 *
 * 到那个阶段我们会把多次模型调用产生的 token
 * 进行累计。
 *
 * @param promptTokens
 *        输入 Token 数。
 *
 *        包括：
 *        System Prompt、
 *        历史消息、
 *        当前用户消息等发送给模型的内容。
 *
 * @param completionTokens
 *        模型输出所消耗的 Token 数。
 *
 *        简单理解就是模型生成答案用了多少 Token。
 *
 * @param totalTokens
 *        总 Token 数。
 *
 *        一般近似等于：
 *
 *        promptTokens + completionTokens
 *
 *        这个指标以后可以用于：
 *        成本统计、配额限制、监控、Evaluation。
 */
public record AgentUsage(

        /*
         * 为什么使用 Integer 而不是 int？
         *
         * 因为某些模型供应商可能没有返回 Token Usage。
         *
         * Integer 可以表示 null：
         *
         * null = 不知道 / Provider 没有提供
         *
         * 而 int 默认只能是 0：
         *
         * 0 = 明确知道使用了 0 个 Token
         *
         * 两者语义是不一样的。
         */
        Integer promptTokens,

        Integer completionTokens,

        Integer totalTokens

) {

    /**
     * 创建一个“当前没有 Usage 信息”的对象。
     *
     * <p>比在业务代码中到处 new AgentUsage(null, null, null)
     * 更清晰。
     */
    public static AgentUsage empty() {

        return new AgentUsage(
                null,
                null,
                null
        );
    }

    /**
     * 把当前 Usage 与另一次调用的 Usage 相加。
     *
     * <p>任意一方某个字段为 {@code null}（Provider 未返回该指标）时，
     * 按 0 处理，避免整体变成 null。
     */
    public AgentUsage plus(AgentUsage other) {
        if (other == null) {
            return this;
        }
        return new AgentUsage(
                nullToZero(promptTokens) + nullToZero(other.promptTokens),
                nullToZero(completionTokens) + nullToZero(other.completionTokens),
                nullToZero(totalTokens) + nullToZero(other.totalTokens)
        );
    }

    private static int nullToZero(Integer v) {
        return v == null ? 0 : v;
    }
}

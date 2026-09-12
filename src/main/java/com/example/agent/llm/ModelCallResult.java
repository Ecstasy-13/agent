package com.example.agent.llm;

import com.example.agent.agent.model.AgentUsage;

/**
 * ModelService 调用一次大模型后的标准返回结果。
 *
 * <p>为什么不能只返回 String？
 *
 * 因为模型调用不仅产生：
 *
 * content
 *
 * 还会产生：
 *
 * token usage
 *
 * 后面甚至可能增加：
 *
 * finishReason
 * modelName
 * responseId
 * toolCalls
 *
 * @param content
 *        模型最终生成的文本内容
 *
 * @param usage
 *        本次模型调用的 Token Usage
 */
public record ModelCallResult(

        String content,

        AgentUsage usage

) {
}

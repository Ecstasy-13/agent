package com.example.agent.llm;

import com.example.agent.agent.model.AgentUsage;
import com.example.agent.model.ToolCall;

import java.util.List;

/**
 * ModelService 调用一次大模型后的标准返回结果。
         *
         * @param content   模型生成的文本内容。当本轮模型只返回工具调用、
        *                  还未给出最终答案时，这里可能为 {@code null}。
        * @param usage     本次模型调用的 Token Usage
        * @param toolCalls 模型请求调用的工具列表；没有工具调用时为 {@code null} 或空列表
        */
public record ModelCallResult(

        String content,

        AgentUsage usage,

        List<ToolCall> toolCalls

) {

    /**
     * 兼容旧代码的工厂方法：不涉及工具调用的普通结果。
     *
     * <p>项目里原来所有 {@code new ModelCallResult(content, usage)} 的调用点
     * （例如 {@code LongMemoryService}）统一改成
     * {@code ModelCallResult.textOnly(content, usage)}，
     * 不需要感知新增的 {@code toolCalls} 字段。
     */
    public static ModelCallResult textOnly(String content, AgentUsage usage) {
        return new ModelCallResult(content, usage, null);
    }

    /** 本轮是否包含工具调用请求 */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
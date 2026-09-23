package com.example.agent.llm;

import com.example.agent.model.Message;

import java.util.List;

/**
        * 大模型访问层统一接口。
        *
        * <p>AgentRuntime 只依赖 ModelService，不直接依赖 Qwen / DashScope / Spring AI ChatClient。
        *
        * <p>V2 新增 {@link #chatWithTools}：
        * 支持传入工具声明，模型可能返回最终文本，也可能返回 {@code toolCalls}
        * （模型想调用哪些工具），由调用方（{@code DefaultAgentRuntime}）决定
        * 何时执行工具、何时继续下一轮。
        */
public interface ModelService {

    /**
     * 调用一次 Chat Model（不带工具）。
     *
     * @param messages 完整模型上下文
     * @return 模型生成内容以及 Token Usage
     */
    ModelCallResult chat(List<Message> messages);

    /**
     * 调用一次 Chat Model，并声明本轮可用的工具。
     *
     * <p>与 {@link #chat} 的区别：
     *
     * 返回结果的 {@code content} 可能为 {@code null}
     * （模型只想调用工具，还没给出最终文本），
     * {@code toolCalls} 可能非空（模型想调用哪些工具）。
     *
     * <p>本方法只负责"调用一次模型、透传结果"，
     * 不负责执行工具、不负责多轮循环——
     * 这些由调用方（{@code DefaultAgentRuntime}）负责，
     * 目的是让 Runtime 能够精确记录每一轮 Trace。
     *
     * @param messages 当前上下文
     * @param tools    本轮可用的工具声明；为 {@code null} 或空列表时行为等价于 {@link #chat}
     */
    ModelCallResult chatWithTools(List<Message> messages, List<ToolDefinition> tools);
}
package com.example.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 一条对话消息。
 *
 * <p>对应需求文档 4.3 短期记忆的数据结构，同时复用于调用大模型时的消息体。
 * 除普通消息外，还支持 Function Calling 所需的两种特殊消息：
 * <ul>
 *   <li>工具调用消息：role=assistant，携带 toolCalls（模型要调用的工具）；</li>
 *   <li>工具结果消息：role=tool，携带 toolCallId + 工具执行结果。</li>
 * </ul>
 */
public record Message(
        String role,
        @JsonInclude(JsonInclude.Include.NON_NULL) String content,
        @JsonProperty("tool_calls") @JsonInclude(JsonInclude.Include.NON_NULL) List<ToolCall> toolCalls,
        @JsonProperty("tool_call_id") @JsonInclude(JsonInclude.Include.NON_NULL) String toolCallId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String name
) {

    public static Message user(String content) {
        return new Message("user", content, null, null, null);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content, null, null, null);
    }

    public static Message system(String content) {
        return new Message("system", content, null, null, null);
    }

    /** 工具结果消息：role=tool */
    public static Message tool(String toolCallId, String content) {
        return new Message("tool", content, null, toolCallId, null);
    }

    /** 工具调用消息：role=assistant，携带模型要调用的工具列表 */
    public static Message assistantToolCall(List<ToolCall> toolCalls) {
        return new Message("assistant", null, toolCalls, null, null);
    }
}

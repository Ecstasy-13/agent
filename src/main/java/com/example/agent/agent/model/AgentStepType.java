package com.example.agent.agent.model;

/**
 * Agent 一次运行过程中可能出现的“步骤类型”。
 *
 * <p>为什么需要 Step？
 *
 * 因为 Agent 与普通 Chat 最大的区别之一是：
 *
 * 普通 Chat：
 *
 * User → LLM → Answer
 *
 * Agent：
 *
 * User
 *  ↓
 * Memory
 *  ↓
 * Retrieval
 *  ↓
 * LLM
 *  ↓
 * Tool
 *  ↓
 * LLM
 *  ↓
 * Answer
 *
 * 所以我们需要记录：
 *
 * “Agent 到底执行了什么？”
 *
 * V1 暂时只有下面四种，
 * 不提前加入尚未实现的 RAG / TOOL / MCP 等类型。
 */
public enum AgentStepType {

    /**
     * 加载记忆。
     *
     * 包括：
     * 1. conversation 短期聊天历史；
     * 2. user 长期用户画像。
     */
    MEMORY_LOAD,

    /**
     * 构造模型上下文。
     *
     * 将：
     *
     * System Prompt
     * + Long Memory
     * + Conversation History
     * + Current Message
     *
     * 组织成最终发送给 LLM 的消息列表。
     */
    CONTEXT_BUILD,

    /**
     * 调用大语言模型。
     *
     * V1 每个 Run 只有一次 LLM_CALL。
     *
     * 后面实现 Tool Loop 后，
     * 一个 Run 内可能出现多个 LLM_CALL。
     */
    LLM_CALL,

    /**
     * 执行一次工具调用。
     *
     * <p>包括：
     * 业务工具（query_order / get_weather / calculator）
     * 以及知识库检索工具（search_knowledge_base）。
     */
    TOOL_CALL,

    /**
     * 保存本轮对话产生的记忆。
     *
     * V1 主要包括：
     *
     * User Message
     * +
     * Assistant Message
     *
     * 同时触发长期记忆抽取。
     */
    MEMORY_SAVE
}

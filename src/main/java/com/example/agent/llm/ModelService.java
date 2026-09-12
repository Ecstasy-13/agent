package com.example.agent.llm;

import com.example.agent.model.Message;

import java.util.List;

/**
 * 大模型访问层统一接口。
 *
 * <p>AgentRuntime 只依赖 ModelService，
 * 不直接依赖：
 *
 * Qwen
 * DashScope
 * OpenAI
 * Ollama
 * Spring AI ChatClient
 *
 * <p>这样可以做到：
 *
 * AgentRuntime
 *      ↓
 * ModelService
 *      ↓
 * SpringAiModelService
 *      ↓
 * Spring AI
 *      ↓
 * Qwen
 */
public interface ModelService {

    /**
     * 调用一次 Chat Model。
     *
     * @param messages
     *        完整模型上下文。
     *
     *        一般包含：
     *        System Message
     *        History
     *        Current User Message
     *
     * @return
     *        模型生成内容以及 Token Usage
     */
    ModelCallResult chat(List<Message> messages);
}

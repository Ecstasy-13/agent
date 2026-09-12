package com.example.agent.agent.runtime;

import com.example.agent.agent.model.AgentRequest;
import com.example.agent.agent.model.AgentResponse;

/**
 * Agent 运行时的统一抽象。
 *
 * <p>对于调用方而言，不关心 Agent 内部到底：
 *
 * 调了几次 LLM，
 * 做没做 RAG，
 * 调没调用 Tool，
 * 有没有 MCP，
 * 有没有 HITL。
 *
 * 调用方只需要：
 *
 * <pre>
 * AgentRequest
 *      ↓
 * runtime.run()
 *      ↓
 * AgentResponse
 * </pre>
 *
 * <p>这也是整个 V2 架构最重要的稳定接口之一。
 */

public interface AgentRuntime {

    /**
     * 执行一次完整的 Agent Run。
     *
     * @param request
     *        本次 Agent 运行请求
     *
     * @return
     *        完整 Agent 执行结果
     */
    AgentResponse run(AgentRequest request);

}

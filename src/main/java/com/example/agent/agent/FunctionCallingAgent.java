package com.example.agent.agent;

import com.example.agent.llm.QwenChatResponse;
import com.example.agent.llm.QwenClient;
import com.example.agent.llm.ToolDefinition;
import com.example.agent.model.Message;
import com.example.agent.model.ToolCall;
import com.example.agent.tool.ToolCallingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Function Calling 智能体（第四阶段）。
 *
 * <p>对应需求文档 4.6。实现「工具调用循环」：
 * <pre>
 * 用户提问 -> 模型判断是否调用工具 -> 是：执行工具并回传结果 -> 模型继续 -> 输出最终回答
 *                                      └────────── 循环（最多 5 轮）──────────┘
 * </pre>
 *
 * <p>依赖 {@link QwenClient#chatRaw} 读取模型返回的 {@code tool_calls}，
 * 通过 {@link ToolCallingService} 执行工具，再用 role=tool 消息回传结果。
 */
@Service
public class FunctionCallingAgent {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallingAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是一个具备工具调用能力的智能助理。当用户的问题需要查询订单、天气或进行计算时，
            请调用相应工具获取结果，再结合工具结果给出回答。不要编造工具没有返回的信息。
            """;

    /** 最多循环轮次，防止模型反复调用工具导致死循环 */
    private static final int MAX_ROUNDS = 5;

    private final QwenClient qwenClient;
    private final ToolCallingService toolCallingService;

    public FunctionCallingAgent(QwenClient qwenClient, ToolCallingService toolCallingService) {
        this.qwenClient = qwenClient;
        this.toolCallingService = toolCallingService;
    }

    /**
     * 运行一轮带工具调用的对话。
     *
     * @param message 用户输入
     * @return 最终回答 + 工具调用过程（供前端展示调用轨迹）
     */
    public Result run(String message) {
        List<ToolDefinition> tools = toolCallingService.toolDefinitions();
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(SYSTEM_PROMPT));
        messages.add(Message.user(message));

        List<Step> steps = new ArrayList<>();

        for (int round = 0; round < MAX_ROUNDS; round++) {
            QwenChatResponse response = qwenClient.chatRaw(messages, tools);
            QwenChatResponse.QwenMessage reply = response == null ? null : response.firstMessage();
            if (reply == null) {
                break;
            }

            List<ToolCall> toolCalls = reply.toolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                // 没有工具调用，返回普通文本回复
                return new Result(reply.content() == null ? "" : reply.content(), steps);
            }

            // 记录模型要调用的工具（role=assistant + tool_calls）
            messages.add(Message.assistantToolCall(toolCalls));
            for (ToolCall call : toolCalls) {
                String name = call.function() == null ? "" : call.function().name();
                String arguments = call.function() == null ? "" : call.function().arguments();
                String result = toolCallingService.execute(name, arguments);
                steps.add(new Step(name, arguments, result));
                // 回传工具结果（role=tool + tool_call_id）
                messages.add(Message.tool(call.id(), result));
                log.info("Function Calling 第 {} 轮，调用工具 {}，结果 {}", round + 1, name, result);
            }
        }

        return new Result("工具调用轮次过多，已终止。请简化问题后重试。", steps);
    }

    // ---------- 数据结构 ----------

    /** 一次工具调用步骤 */
    public record Step(String toolName, String arguments, String result) {
    }

    /** 运行结果：最终回答 + 工具调用轨迹 */
    public record Result(String answer, List<Step> steps) {
    }
}

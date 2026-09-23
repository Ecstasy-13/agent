package com.example.agent.agent.context;

import com.example.agent.config.AgentProperties;
import com.example.agent.exception.AgentBusinessException;
import com.example.agent.exception.ErrorCode;
import com.example.agent.model.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent Context Window 管理器。
 *
 * <p>职责：
 *
 * 根据 Token Budget，
 * 从 Redis 中保存的大量 History 中，
 * 选择本轮真正发送给 LLM 的历史。
 *
 * <p>核心原则：
 *
 * 1. System Message 一定保留；
 * 2. Current User Message 一定保留；
 * 3. 历史优先保留“最近的”；
 * 4. 尽量保留完整 Turn；
 * 5. 不能超过 Context Token Budget。
 */
@Service
public class ContextWindowService {

    /**
     * Token估算器
     */
    private final TokenCounter tokenCounter;

    /**
     * 应用层最大输入Token
     */
    private final int maxInputTokens;

    /**
     * 给模型回答预留
     */
    private final int reservedOutputTokens;

    /**
     * Tokenizer 差异等安全余量
     */
    private final int safetyMarginTokens;

    public ContextWindowService(TokenCounter tokenCounter, AgentProperties agentProperties) {
        this.tokenCounter = tokenCounter;
        this.maxInputTokens = agentProperties.getContext().getMaxInputTokens();
        this.reservedOutputTokens = agentProperties.getContext().getReservedOutputTokens();
        this.safetyMarginTokens = agentProperties.getContext().getSafetyMarginTokens();
    }

    /**
     * 构造本轮最终 Context Window。
     *
     * @param systemMessage
     *        本轮 System Message。
     *
     * @param history
     *        Redis 中读取出来的 Conversation 历史。
     *
     * @param currentUserMessage
     *        用户当前输入。
     */
    public ContextWindow build(Message systemMessage, List<Message> history, Message currentUserMessage) {
        List<Message> safeHistory = history == null ? List.of() : history;
        /*
         * ----------------------------------------
         * 1. 计算一定必须出现的内容
         * ----------------------------------------
         *
         * System
         * +
         * Current User
         */
        int fixedTokens = tokenCounter.count(systemMessage) + tokenCounter.count(currentUserMessage);
        /*
         * ----------------------------------------
         * 2. 计算真正可以留给 History 的预算
         * ----------------------------------------
         *
         * 例如：
         *
         * maxInputTokens        10000
         * reservedOutput       -2000
         * safetyMargin          -500
         * fixed                 -300
         *
         * History Budget
         *                      =7200
         */
        int historyBudget = maxInputTokens - reservedOutputTokens - safetyMarginTokens - fixedTokens;
        /*
         * 如果 System + 当前用户消息
         * 自己就已经超过预算，
         * 此时删历史已经没意义。
         *
         * 应该直接报错，
         * 而不是继续把一个明显超预算的 Prompt
         * 发给 LLM。
         */
        if (historyBudget < 0) {
            throw new AgentBusinessException(ErrorCode.CONTEXT_BUDGET_EXCEEDED, "fixedTokens=" + fixedTokens + ", maxInputTokens=" + maxInputTokens
            );
        }
        /*
         * ----------------------------------------
         * 3. 把历史按 Turn 分组
         * ----------------------------------------
         *
         * 为什么不能简单倒序一条一条取？
         *
         * 假设：
         *
         * USER A
         * ASSISTANT A
         * USER B
         * ASSISTANT B
         *
         * Token 截断如果只留下：
         *
         * ASSISTANT A
         * USER B
         * ASSISTANT B
         *
         * 那么第一个 Assistant
         * 已经没有对应 User 了。
         *
         * 所以尽量按照完整 Turn 截断。
         */
        List<List<Message>> turns = groupByTurns(safeHistory);
        /*
         * 被选中的 Turn。
         */
        List<List<Message>> selectedTurns = new ArrayList<>();
        int usedHistoryTokens = 0;
        /*
         * ----------------------------------------
         * 4. 从最新 Turn 开始向前选择
         * ----------------------------------------
         * Conversation Memory：
         * old → new
         * Context 选择：
         * 从 new 往 old 扫描。
         */
        for (int index = turns.size() - 1; index >= 0; index--) {
            List<Message> turn = turns.get(index);
            int turnTokens = tokenCounter.count(turn);
            /*
             * 当前 Turn 整体加入以后仍未超预算。
             */
            if (usedHistoryTokens + turnTokens <= historyBudget) {
                /*
                 * 因为我们正在倒序扫描，
                 * 但最后仍然需要 old → new，
                 * 所以插到列表最前面。
                 */
                selectedTurns.add(0, turn);
                usedHistoryTokens += turnTokens;
            } else {
                /*
                 * 一旦某个更近的 Turn 都放不下，
                 * 就停止继续向更老历史查找。
                 *
                 * 为什么不“跳过这个大的 Turn，
                 * 再找更早的小 Turn”？
                 *
                 * 因为我们希望保留：
                 *
                 * 连续的最近上下文。
                 *
                 * 而不是拼成碎片：
                 *
                 * Turn 1
                 * Turn 9
                 * Turn 10
                 */
                break;
            }
        }
        List<Message> selectedHistory = selectedTurns.stream().flatMap(List::stream).toList();

        /*
         * ----------------------------------------
         * 6. 组装完整 Prompt
         * ----------------------------------------
         */
        List<Message> finalMessages = new ArrayList<>();
        finalMessages.add(systemMessage);
        finalMessages.addAll(selectedHistory);
        finalMessages.add(currentUserMessage);
        /*
         * 再估算一次最终 Prompt。
         */
        int estimatedTokens = tokenCounter.count(finalMessages);
        int droppedMessages = safeHistory.size() - selectedHistory.size();

        return new ContextWindow(
                finalMessages,
                estimatedTokens,
                safeHistory.size(),
                selectedHistory.size(),
                droppedMessages
        );
    }

    /**
     * 将 Conversation History 按 User Turn 分组。
     *
     * <p>V1/V2 当前 Memory 正常形式：
     *
     * USER
     * ASSISTANT
     * USER
     * ASSISTANT
     *
     * 得到：
     *
     * Turn 1:
     * USER
     * ASSISTANT
     *
     * Turn 2:
     * USER
     * ASSISTANT
     *
     * <p>以后 Tool Calling：
     *
     * USER
     * ASSISTANT(tool_call)
     * TOOL
     * ASSISTANT
     *
     * 也仍然可以归属于同一个 User Turn。
     */
    private List<List<Message>> groupByTurns(List<Message> history) {
        List<List<Message>> turns = new ArrayList<>();
        List<Message> currentTurn = null;
        for (Message message : history) {
            /*
             * 遇到 User Message，
             * 表示新 Turn 开始。
             */
            if ("user".equals(message.role())) {
                currentTurn = new ArrayList<>();
                turns.add(currentTurn);
                currentTurn.add(message);
                continue;
            }
            /*
             * Assistant / Tool 等消息
             * 都属于最近一个 User Turn。
             *
             * 如果历史异常，
             * 一开始就是 Assistant，
             * V2 选择忽略孤立消息。
             */
            if (currentTurn != null) {
                currentTurn.add(message);
            }
        }

        return turns;
    }


}

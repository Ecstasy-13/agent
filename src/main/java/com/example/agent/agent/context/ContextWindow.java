package com.example.agent.agent.context;

import com.example.agent.model.Message;

import java.util.List;

/**
 * 一次 Agent Run 最终选择出来的 LLM Context Window。
 *
 * <p>它描述的不只是：
 *
 * “最后用了哪些 Message”
 *
 * 还描述：
 *
 * “原来有多少历史”
 * “保留了多少”
 * “丢弃了多少”
 * “估算用了多少 Token”
 *
 * @param messages
 *        最终真正发送给 LLM 的完整 Message List。
 *
 *        顺序通常是：
 *
 *        System
 *        +
 *        Selected History
 *        +
 *        Current User
 *
 * @param estimatedTokens
 *        整个 messages 的预估 Token 数。
 *
 *        注意：
 *        这是 Context Budget 的估算值，
 *        不是模型实际 Usage。
 *
 * @param totalHistoryMessages
 *        Redis 中原本读取出来的历史消息数。
 *
 * @param selectedHistoryMessages
 *        最终真正选择进入 Context 的历史消息数。
 *
 * @param droppedHistoryMessages
 *        因为 Token Budget 不足，
 *        被丢弃的较旧历史消息数。
 *
 *        一般：
 *
 *        totalHistoryMessages
 *        =
 *        selectedHistoryMessages
 *        +
 *        droppedHistoryMessages
 */
public record ContextWindow(
        List<Message> messages,

        int estimatedTokens,

        int totalHistoryMessages,

        int selectedHistoryMessages,

        int droppedHistoryMessages
) {
    /**
     * ContextWindow 创建后不允许外部
     * 修改内部 Message List。
     */
    public ContextWindow {
        messages = messages == null
                ? List.of()
                : List.copyOf(messages);
    }
}

package com.example.agent.agent.context;

import com.example.agent.model.Message;

import java.util.List;

/**
 * Token 数量估算器。
 *
 * <p>为什么自己定义接口？
 *
 * 因为不同模型使用的 tokenizer
 * 并不完全一样。
 *
 * 例如：
 *
 * Qwen
 * GPT
 * Claude
 *
 * Token 切分方式可能不同。
 *
 * <p>所以 Agent Context 不应该直接绑定
 * 某一个具体 Tokenizer。
 */
public interface TokenCounter {

    /**
     * 估算一段普通文本大约需要多少 Token。
     */
    int count(String text);

    /**
     * 估算一条 Message 使用多少 Token。
     *
     * <p>除了 content，
     * Chat Message 本身通常还有：
     *
     * role
     * message boundary
     * protocol formatting
     *
     * 等额外开销。
     *
     * V2 暂时人为预留 4 Token。
     */
    default int count(Message message) {
        if (message == null) {
            return 0;
        }
        int contentTokens = count(message.content());
        int messageOverheadTokens = 4;
        return contentTokens + messageOverheadTokens;
    }

    /**
     * 估算一组消息的 Token。
     */
    default int count(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream().mapToInt(this::count).sum();
    }

}

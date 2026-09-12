package com.example.agent.memory;

import com.example.agent.model.Message;

import java.util.List;

/**
 * Conversation 级短期记忆统一接口。
 *
 * <p>注意：
 *
 * LongMemoryService 是：
 *
 * user 级别。
 *
 * ConversationMemory 是：
 *
 * conversation 级别。
 *
 * <pre>
 * User
 *  │
 *  ├── Long Memory
 *  │
 *  ├── Conversation A
 *  │      └── ConversationMemory
 *  │
 *  └── Conversation B
 *         └── ConversationMemory
 * </pre>
 *
 * <p>接口不关心数据到底存在：
 *
 * JVM
 * Redis
 * MySQL
 *
 * 这属于具体实现负责的问题。
 */
public interface ConversationMemory {

    /**
     * 获取一个 Conversation 已经存在的历史消息。
     *
     * @param userId
     *        用户 ID
     *
     * @param conversationId
     *        会话 ID
     */
    List<Message> getHistory(String userId, String conversationId);

    /**
     * 向 Conversation 中添加一条消息。
     */
    void add(String userId, String conversationId, Message message);

    /**
     * 一次添加多条消息。
     *
     * <p>V1 中主要用于一次保存完整 Turn：
     *
     * User Message
     * +
     * Assistant Message
     *
     * <p>定义成 default 方法，
     * 具体实现如果没有特殊要求，
     * 可以直接复用这个逻辑。
     */
    default void addAll(String userId, String conversationId, List<Message> messages) {
        for (Message message : messages) {
            add(userId, conversationId, message);
        }
    }

    /**
     * 清空指定 Conversation 的短期历史。
     *
     * 注意不是清除整个 user 的所有聊天。
     */
    void clear(String userId, String conversationId);
}

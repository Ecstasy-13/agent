package com.example.agent.memory;

import com.example.agent.config.AgentProperties;
import com.example.agent.model.Message;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConversationMemory 的 JVM 内存实现。
 *
 * <p>注意：
 * 这个实现是 V1 过渡版本。
 *
 * 后面我们会实现：
 *
 * RedisConversationMemory
 *
 * 然后替换掉当前实现。
 *
 * <p>但 AgentRuntime 不需要修改，
 * 因为 Runtime 依赖的是：
 *
 * ConversationMemory
 *
 * 而不是：
 *
 * InMemoryConversationMemory。
 */

public class InMemoryConversationMemory implements ConversationMemory {

    /**
     * 每个 Conversation 最多保留多少条消息。
     *
     * 从 application.yml：
     *
     * agent.memory.short-term-size
     *
     * 读取。
     */
    private final int maxSize;

    /**
     * JVM 内存中的 Conversation Storage。
     *
     * Key：
     * ConversationKey(userId, conversationId)
     *
     * Value：
     * 该 Conversation 的消息队列。
     *
     * ConcurrentHashMap 保证 Map 级别基本并发安全。
     *
     * 但是里面的 ArrayDeque 本身不是线程安全的，
     * 所以下面 add/get 时还会 synchronized(deque)。
     */
    private final ConcurrentHashMap<ConversationKey, Deque<Message>> conversations = new ConcurrentHashMap<>();

    public InMemoryConversationMemory(AgentProperties properties) {
        this.maxSize = properties
                        .getMemory()
                .getMaxStoredMessages();
    }

    /**
     * 获取指定 Conversation 的聊天历史。
     */
    @Override
    public List<Message> getHistory(String userId, String conversationId) {

        ConversationKey key = new ConversationKey(userId, conversationId);

        Deque<Message> deque = conversations.get(key);

        /*
         * 没聊过意味着没有历史，
         * 返回空列表而不是 null。
         */
        if (deque == null) {
            return List.of();
        }

        /*
         * ArrayDeque 不是线程安全容器。
         *
         * 读取期间与 add() 保持相同锁对象，
         * 避免一边复制、一边修改。
         */
        synchronized (deque) {

            /*
             * 返回副本。
             *
             * 绝对不要：
             *
             * return deque;
             *
             * 否则业务层可能直接修改内存内部数据。
             */
            return new ArrayList<>(deque);
        }
    }

    /**
     * 向一个 Conversation 添加消息。
     */
    @Override
    public void add(String userId, String conversationId, Message message) {

        ConversationKey key =
                new ConversationKey(
                        userId,
                        conversationId
                );

        /*
         * 如果 Conversation 第一次出现，
         * 创建新的 ArrayDeque。
         *
         * 如果已经存在，
         * 返回原来的 deque。
         */
        Deque<Message> deque = conversations.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {

            /*
             * 新消息总是追加到队尾：
             *
             * oldest → newest
             */
            deque.addLast(message);

            /*
             * 超过窗口大小时，
             * 从最旧消息开始删除。
             *
             * 例如 maxSize = 3：
             *
             * [1,2,3]
             *
             * 加 4：
             *
             * [1,2,3,4]
             *
             * 删除最前面的 1：
             *
             * [2,3,4]
             */
            while (deque.size() > maxSize) {
                deque.removeFirst();
            }
        }
    }

    /**
     * 清空指定 Conversation。
     */
    @Override
    public void clear(String userId, String conversationId) {
        conversations.remove(
                new ConversationKey(
                        userId,
                        conversationId
                )
        );
    }

    /**
     * Conversation Memory 的联合 Key。
     *
     * <p>为什么不用单独 conversationId？
     *
     * 因为我们希望数据逻辑上同时绑定：
     *
     * userId
     * +
     * conversationId
     *
     * 后面 Redis 中也会形成类似：
     *
     * agent:conversation:user-001:conv-001
     *
     * @param userId
     *        Conversation 所属用户
     *
     * @param conversationId
     *        Conversation 唯一 ID
     */
    private record ConversationKey(String userId, String conversationId) {
    }
}

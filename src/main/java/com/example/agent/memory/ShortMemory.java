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
 * 短期记忆：保存当前会话上下文，使 Agent 能够理解连续对话。
 *
 * <p>对应需求文档 4.3：
 * <ul>
 *   <li>第一阶段：使用 Java 内存（{@link ConcurrentHashMap} + 有界队列）</li>
 *   <li>后续：可替换为 Redis</li>
 * </ul>
 */
@Component
public class ShortMemory {

    private final int maxSize;

    /** 每个用户一个会话窗口，key = userId，value = 该用户最近的消息队列 */
    private final ConcurrentHashMap<String, Deque<Message>> sessions = new ConcurrentHashMap<>();

    public ShortMemory(AgentProperties properties) {
        this.maxSize = properties.getMemory().getShortTermSize();
    }

    /**
     * 追加一条消息到某用户会话。超出窗口大小时丢弃最旧消息。
     */
    public void add(String userId, Message message) {
        Deque<Message> deque = sessions.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(message);
            while (deque.size() > maxSize) {
                deque.removeFirst();
            }
        }
    }

    /**
     * 返回某用户当前会话历史（按时间顺序），用于构造 Prompt。
     */
    public List<Message> getHistory(String userId) {
        Deque<Message> deque = sessions.get(userId);
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            return new ArrayList<>(deque);
        }
    }

    /**
     * 清空某用户会话（开始新会话）。
     */
    public void clear(String userId) {
        sessions.remove(userId);
    }
}

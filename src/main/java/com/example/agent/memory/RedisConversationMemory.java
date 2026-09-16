package com.example.agent.memory;

import com.example.agent.config.AgentProperties;
import com.example.agent.model.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Redis 的 ConversationMemory 实现。
 *
 * <p>V1 使用的是：
 *
 * InMemoryConversationMemory
 *
 * 数据只存在当前 JVM：
 *
 * 应用一重启
 *     ↓
 * 所有 Conversation 丢失
 *
 * V2 改成 Redis：
 *
 * Agent Instance
 *      ↓
 * Redis
 *
 * 因此即使应用重启，
 * Conversation Memory 仍然可以继续存在。
 *
 * <p>Redis 数据结构使用 LIST：
 *
 * Key:
 *
 * agent:memory:conversation:{userId}:{conversationId}
 *
 * Value:
 *
 * JSON Message
 * JSON Message
 * JSON Message
 *
 * <p>消息按照 oldest → newest 排列。
 */
@Component
public class RedisConversationMemory implements ConversationMemory{

    /**
     * Spring Data Redis 提供的字符串 Redis Client。
     *
     * <p>为什么用 StringRedisTemplate
     * 而不是 RedisTemplate<String, Message>？
     *
     * 我们希望 Redis 中的数据格式显式可读，
     * 自己用 Jackson 序列化成 JSON。
     *
     * 这样进入 redis-cli 时，
     * 可以直接看到 JSON，
     * 也避免默认 Java 序列化产生二进制内容。
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * Jackson JSON Serializer。
     *
     * 负责：
     *
     * Message
     *   ↔
     * JSON
     */
    private final ObjectMapper objectMapper;

    /**
     * Redis Key 前缀。
     */
    private final String keyPrefix;

    /**
     * Conversation TTL。
     */
    private final Duration ttl;

    /**
     * Redis 中一个 Conversation
     * 最大保存消息数。
     */
    private final int maxStoredMessages;

    public RedisConversationMemory(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, AgentProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = properties.getMemory().getRedisKeyPrefix();
        this.ttl = properties.getMemory().getTtl();
        this.maxStoredMessages = properties.getMemory().getMaxStoredMessages();
    }

    /**
     * 获取一个 Conversation 的全部近期历史。
     *
     * <p>注意：
     *
     * 这里读取的是 Redis 当前保存的历史，
     * 并不意味着这些历史最终都会发送给 LLM。
     *
     * 真正发给 LLM 的消息，
     * 还会经过 ContextWindowService。
     */
    @Override
    public List<Message> getHistory(String userId, String conversationId) {
        String key = buildKey(userId, conversationId);
        /*
         * Redis：
         * LRANGE key 0 -1
         * 表示读取 List 中所有元素。
         */
        List<String> jsonMessage = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonMessage == null || jsonMessage.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>(jsonMessage.size());
        // json -> message
        for (String json : jsonMessage) {
            messages.add(deserialize(json));
        }
        /*
         * 返回不可修改列表，
         * 防止调用方直接篡改 Memory 结果。
         */
        return List.copyOf(messages);
    }

    /**
     * 保存一条 Message。
     *
     * <p>直接复用 addAll，
     * 保证单条/多条写入行为一致。
     */
    @Override
    public void add(String userId, String conversationId, Message message) {
        addAll(userId, conversationId, List.of(message));
    }

    /**
     * 一次保存多条消息。
     *
     * <p>Runtime 当前最常见的是一次保存：
     *
     * USER
     * +
     * ASSISTANT
     *
     * 一个完整 Turn。
     */
    @Override
    public void addAll(String userId, String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String key = buildKey(userId, conversationId);
        List<String> values = messages.stream().map(this::serialize).toList();
        /*
         * RPUSH：
         * 将新消息追加到 List 尾部。
         * 所以 Redis 中始终保持：
         * oldest → newest
         */
        redisTemplate.opsForList().rightPushAll(key, values);
        /*
         * 控制 Redis Storage Size。
         * Redis：
         * LTRIM key -100 -1
         * 表示：
         * 只保留最后 100 条消息。
         */
        redisTemplate.opsForList().trim(key, -maxStoredMessages, -1);
        /*
         * 每次成功聊天都会刷新 TTL。
         *
         * 假设 ttl = 7 days：
         *
         * 用户持续聊天
         *      ↓
         * Key 持续存活
         *
         * 连续 7 天没有聊天
         *      ↓
         * Conversation 自动释放
         */
        redisTemplate.expire(key, ttl);
    }

    @Override
    public void clear(String userId, String conversationId) {
        String key = buildKey(userId, conversationId);
        redisTemplate.delete(key);
    }

    private String buildKey(String userId, String conversationId) {
        return keyPrefix + ":" + userId + ":" + conversationId;
    }

    /**
     * Message → JSON。
     */
    private String serialize(Message message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            /*
             * Memory 序列化失败属于基础设施错误。
             *
             * 不能悄悄忽略，
             * 否则用户以为保存成功，
             * 实际上下文已经丢失。
             */
            throw new IllegalStateException("Conversation Message Redis 序列化失败",e);
        }
    }

    /**
     * JSON → Message。
     */
    private Message deserialize(String json) {
        try {
            return objectMapper.readValue(json, Message.class);
        } catch (JsonProcessingException e) {
            /*
             * Redis 中的数据如果无法反序列化，
             * 应该明确报错。
             *
             * 不应该简单跳过坏数据，
             * 否则 Conversation Context
             * 会悄悄发生变化。
             */
            throw new IllegalStateException("Conversation Message Redis 反序列化失败",e);
        }
    }

}

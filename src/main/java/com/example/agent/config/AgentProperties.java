package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.tools.Tool;
import java.time.Duration;

/**
 * Agent 行为配置。
 *
 * <p>对应 application.yml 中的 {@code agent.*} 配置项。
 */
@ConfigurationProperties(prefix = "agent")
@Component
public class AgentProperties {

    /**
     * Conversation Memory 配置。
     */
    private Memory memory = new Memory();

    /**
     * Agent Context Window 配置。
     */
    private Context context = new Context();

    /**
     * Tool Calling Agent Loop 配置。
     */
    private Tool tool = new Tool();

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Tool getTool() { return tool; }

    public void setTool(Tool tool) { this.tool = tool; }

    /**
     * Conversation Memory 配置。
     */
    public static class Memory {

        /**
         * Redis Key 前缀。
         * <p>
         * 最终 Key 大概是：
         * <p>
         * agent:memory:conversation:user-001:conv-001
         */
        private String redisKeyPrefix = "agent:memory:conversation";

        /**
         * Conversation Memory TTL。
         * <p>
         * 例如：
         * <p>
         * 7d
         * 24h
         * 30m
         */
        private Duration ttl = Duration.ofDays(7);

        /**
         * Redis 中一个 Conversation
         * 最多保存多少条消息。
         * <p>
         * 注意这是 Redis Storage Limit，
         * 不是 LLM Context Limit。
         */
        private int maxStoredMessages = 100;

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public int getMaxStoredMessages() {
            return maxStoredMessages;
        }

        public void setMaxStoredMessages(int maxStoredMessages) {
            this.maxStoredMessages = maxStoredMessages;
        }
    }

    /**
     * Context Window 配置。
     *
     * <p>负责限制一次真正发送给 LLM 的上下文大小。
     */
    public static class Context {

        /**
         * 本应用希望控制的最大输入 Token。
         * <p>
         * 注意：
         * 这是应用层预算，
         * 不代表模型厂商公布的真正最大窗口。
         */
        private int maxInputTokens = 10000;

        /**
         * 给模型回答预留 Token。
         */
        private int reservedOutputTokens = 2000;

        /**
         * Token 估算安全余量。
         */
        private int safetyMarginTokens = 500;

        public int getMaxInputTokens() {
            return maxInputTokens;
        }

        public void setMaxInputTokens(int maxInputTokens) {
            this.maxInputTokens = maxInputTokens;
        }

        public int getReservedOutputTokens() {
            return reservedOutputTokens;
        }

        public void setReservedOutputTokens(int reservedOutputTokens) {
            this.reservedOutputTokens = reservedOutputTokens;
        }

        public int getSafetyMarginTokens() {
            return safetyMarginTokens;
        }

        public void setSafetyMarginTokens(int safetyMarginTokens) {
            this.safetyMarginTokens = safetyMarginTokens;
        }
    }


    /**
     * Tool Calling Agent Loop 配置。
     */
    public static class Tool {

        /**
         * 一次 Agent Run 中，Tool Loop 最大允许的模型调用轮次。
         * <p>
         * 防止模型陷入"反复调用工具但始终不给最终答案"的死循环，
         * 超过这个轮次会抛出 {@code TOOL_ROUNDS_EXCEEDED} 业务异常。
         */
        private int maxRounds = 5;

        public int getMaxRounds() {
            return maxRounds;
        }

        public void setMaxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
        }
    }
}

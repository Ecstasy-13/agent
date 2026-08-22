package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 行为配置。
 *
 * <p>对应 application.yml 中的 {@code agent.*} 配置项。
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** 记忆相关配置 */
    private Memory memory = new Memory();

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public static class Memory {

        /** 短期记忆（单会话）保留的最大消息条数 */
        private int shortTermSize = 20;

        /** 是否启用长期记忆自动抽取 */
        private boolean extractEnabled = true;

        public int getShortTermSize() {
            return shortTermSize;
        }

        public void setShortTermSize(int shortTermSize) {
            this.shortTermSize = shortTermSize;
        }

        public boolean isExtractEnabled() {
            return extractEnabled;
        }

        public void setExtractEnabled(boolean extractEnabled) {
            this.extractEnabled = extractEnabled;
        }
    }
}

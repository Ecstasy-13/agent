package com.example.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Java 智能 Agent 系统 启动类。
 *
 * <p>系统架构（对应需求文档第 3 章）：
 * <pre>
 * 用户 -> Web/API 接口 -> Spring Boot 服务 -> Agent 核心
 *                                          |-- Memory 模块（短期 / 长期记忆）
 *                                          |-- LLM 模块（通义千问 Qwen）
 *                                          （后续扩展：RAG / Tool Calling / MCP）
 * </pre>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}

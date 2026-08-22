package com.example.agent.mcp;

/**
 * MCP（Model Context Protocol）扩展服务（第五阶段，高级版本）。
 *
 * <p>对应需求文档 4.7。目标：通过 MCP 协议连接外部资源，让 Agent 拥有标准化的工具扩展能力。
 *
 * <p>支持资源：数据库 / 文件 / API 服务 / 企业工具。
 */
public class McpService {

    /**
     * 连接外部 MCP 资源（待实现）。
     */
    public void connect(String endpoint) {
        throw new UnsupportedOperationException("MCP 扩展为高级版本功能，尚未实现。");
    }
}

package com.example.agent.controller;

import com.example.agent.dto.ApiResponse;
import com.example.agent.dto.McpCallRequest;
import com.example.agent.dto.McpConnectRequest;
import com.example.agent.mcp.McpService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MCP（Model Context Protocol）扩展接口。
 *
 * <p>对应需求文档 4.7 / 6.6：
 * <pre>
 * POST /agent/mcp/connect  —— 连接 MCP 服务器
 * GET  /agent/mcp/servers  —— 列出已连接的服务器
 * GET  /agent/mcp/tools    —— 列出所有工具
 * POST /agent/mcp/call     —— 调用指定工具
 * </pre>
 */
@RestController
@RequestMapping("/agent/mcp")
public class McpController {

    private final McpService mcpService;

    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    /** 连接 MCP 服务器 */
    @PostMapping("/connect")
    public ApiResponse<Map<String, Object>> connect(@Valid @RequestBody McpConnectRequest request) {
        return ApiResponse.ok(mcpService.connect(request.name(), request.endpoint()));
    }

    /** 列出已连接的服务器 */
    @GetMapping("/servers")
    public ApiResponse<List<Map<String, Object>>> servers() {
        return ApiResponse.ok(mcpService.listServers());
    }

    /** 列出所有工具 */
    @GetMapping("/tools")
    public ApiResponse<List<Map<String, Object>>> tools() {
        return ApiResponse.ok(mcpService.listTools());
    }

    /** 调用工具 */
    @PostMapping("/call")
    public ApiResponse<Map<String, Object>> call(@Valid @RequestBody McpCallRequest request) {
        String result = mcpService.call(request.server(), request.tool(), request.arguments());
        return ApiResponse.ok(Map.of(
                "server", request.server(),
                "tool", request.tool(),
                "result", result
        ));
    }
}

package com.example.agent.mcp;

import com.example.agent.llm.ToolDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP（Model Context Protocol）扩展服务（第五阶段）。
 *
 * <p>对应需求文档 4.7。标准 MCP 通过 {@code initialize}/{@code tools/list}/{@code tools/call}
 * 等协议消息连接外部工具服务器。本实现为「轻量自包含」版本，不引入独立 MCP 客户端，
 * 而是用 {@link McpServer} 模拟一个 MCP 服务器：连接 -> 发现工具 -> 调用工具。
 *
 * <p>内置一个「local-tools」模拟服务器，并提供 {@link #connect(String, String)} 供扩展
 * 更多模拟资源。生产环境可替换为官方 MCP Java SDK，对接数据库 / 文件 / API / 企业工具。
 */
@Service
public class McpService {

    private static final Logger log = LoggerFactory.getLogger(McpService.class);

    private final ObjectMapper objectMapper;
    private final Map<String, McpServer> servers = new LinkedHashMap<>();

    public McpService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        seedLocalServer();
    }

    /** 内置模拟 MCP 服务器（本地工具集） */
    private void seedLocalServer() {
        Map<String, McpTool> tools = new LinkedHashMap<>();
        tools.put("list_files", new McpTool(
                ToolDefinition.of("list_files", "列出指定目录下的文件",
                        schema(Map.of("path", Map.of("type", "string", "description", "目录路径")),
                                List.of("path"))),
                args -> "目录 " + str(args, "path") + " 下文件（模拟）：README.md、pom.xml、src/"
        ));
        tools.put("read_file", new McpTool(
                ToolDefinition.of("read_file", "读取指定文件内容",
                        schema(Map.of("path", Map.of("type", "string", "description", "文件路径")),
                                List.of("path"))),
                args -> "（模拟文件内容）" + str(args, "path") + " 的内容为：Hello Agent!"
        ));
        tools.put("get_time", new McpTool(
                ToolDefinition.of("get_time", "获取当前系统时间",
                        schema(Map.of(), List.of())),
                args -> "当前时间：" + LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        ));
        servers.put("local-tools", new McpServer("local-tools", "内置模拟 MCP 服务器（本地工具集）", tools));
    }

    /**
     * 连接一个 MCP 服务器。
     *
     * <p>真实实现会向 {@code endpoint} 发起 {@code initialize} 握手并拉取工具列表；
     * 此处仅登记连接，并附带一个 {@code echo} 示例工具以演示调用链路。
     */
    public Map<String, Object> connect(String name, String endpoint) {
        if (servers.containsKey(name)) {
            return Map.of("name", name, "endpoint", servers.get(name).endpoint(),
                    "connected", false, "note", "服务器已连接");
        }
        Map<String, McpTool> tools = new LinkedHashMap<>();
        tools.put("echo", new McpTool(
                ToolDefinition.of("echo", "原样返回输入内容",
                        schema(Map.of("message", Map.of("type", "string", "description", "要回显的内容")),
                                List.of("message"))),
                args -> "echo: " + str(args, "message")
        ));
        servers.put(name, new McpServer(name, endpoint, tools));
        log.info("MCP 服务器已连接，name={}, endpoint={}", name, endpoint);
        return Map.of("name", name, "endpoint", endpoint, "connected", true);
    }

    /** 列出已连接的服务器 */
    public List<Map<String, Object>> listServers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (McpServer s : servers.values()) {
            result.add(Map.of(
                    "name", s.name(),
                    "endpoint", s.endpoint(),
                    "toolCount", s.tools().size()));
        }
        return result;
    }

    /** 列出所有服务器上的全部工具 */
    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (McpServer s : servers.values()) {
            for (McpTool t : s.tools().values()) {
                result.add(Map.of(
                        "server", s.name(),
                        "name", t.definition().function().name(),
                        "description", t.definition().function().description()));
            }
        }
        return result;
    }

    /** 调用指定服务器上的工具 */
    public String call(String server, String tool, String argumentsJson) {
        McpServer srv = servers.get(server);
        if (srv == null) {
            return "未连接 MCP 服务器：" + server;
        }
        McpTool t = srv.tools().get(tool);
        if (t == null) {
            return "服务器 " + server + " 不存在工具：" + tool;
        }
        Map<String, Object> args = parseArgs(argumentsJson);
        try {
            String result = t.executor().execute(args);
            log.info("MCP 工具调用，server={}, tool={}, args={} -> {}", server, tool, args, result);
            return result;
        } catch (Exception e) {
            return "工具执行失败：" + e.getMessage();
        }
    }

    // ---------- 内部辅助 ----------

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);
        return parameters;
    }

    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("MCP 参数解析失败：{}", e.getMessage());
            return Map.of();
        }
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    // ---------- 数据结构 ----------

    /** 一个 MCP 服务器：名称 + 端点 + 工具集 */
    private record McpServer(String name, String endpoint, Map<String, McpTool> tools) {
    }

    /** 一个 MCP 工具 = 声明 + 执行逻辑 */
    private record McpTool(ToolDefinition definition, ToolExecutor executor) {
    }

    /** 工具执行逻辑：入参 -> 结果文本 */
    @FunctionalInterface
    public interface ToolExecutor {
        String execute(Map<String, Object> args);
    }
}

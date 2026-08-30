package com.example.agent.controller;

import com.example.agent.agent.FunctionCallingAgent;
import com.example.agent.dto.ApiResponse;
import com.example.agent.dto.ToolCallRequest;
import com.example.agent.tool.ToolCallingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用（Function Calling）接口。
 *
 * <p>对应需求文档 4.6 / 6.4：
 * <pre>
 * POST /agent/tool/call   —— 带工具调用的对话（返回最终回答 + 调用轨迹）
 * GET  /agent/tool/list   —— 列出当前已注册的工具
 * </pre>
 */
@RestController
@RequestMapping("/agent/tool")
public class ToolController {

    private final FunctionCallingAgent functionCallingAgent;
    private final ToolCallingService toolCallingService;

    public ToolController(FunctionCallingAgent functionCallingAgent,
                          ToolCallingService toolCallingService) {
        this.functionCallingAgent = functionCallingAgent;
        this.toolCallingService = toolCallingService;
    }

    /** 带工具调用的对话 */
    @PostMapping("/call")
    public ApiResponse<Map<String, Object>> call(@Valid @RequestBody ToolCallRequest request) {
        FunctionCallingAgent.Result result = functionCallingAgent.run(request.message());
        List<Map<String, Object>> steps = result.steps().stream()
//                .map(s -> Map.of(
//                        "tool", s.toolName(),
//                        "arguments", s.arguments(),
//                        "result", s.result()))
                .map(s -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("tool", s.toolName());
                    map.put("arguments", s.arguments());
                    map.put("result", s.result());
                    return map;
                })
                .toList();
        return ApiResponse.ok(Map.of(
                "answer", result.answer(),
                "steps", steps
        ));
    }

    /** 列出已注册的工具 */
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(toolCallingService.listTools());
    }
}

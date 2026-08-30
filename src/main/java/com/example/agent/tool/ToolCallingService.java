package com.example.agent.tool;

import com.example.agent.llm.ToolDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool Calling 工具调用服务（第四阶段）。
 *
 * <p>对应需求文档 4.6。维护一个「工具注册表」，把工具的名称、描述、JSON Schema 声明
 * 以及具体执行逻辑集中管理，供 {@code FunctionCallingAgent} 在对话循环中调用。
 *
 * <p>当前为「轻量自包含」实现，内置三个示例工具（查询订单 / 查询天气 / 四则运算），
 * 生产环境可在此注册表上继续接入数据库查询、HTTP 调用等真实工具。
 */
@Service
public class ToolCallingService {

    private static final Logger log = LoggerFactory.getLogger(ToolCallingService.class);

    private final ObjectMapper objectMapper;
    private final Map<String, Tool> registry = new LinkedHashMap<>();

    public ToolCallingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        // 示例工具 1：查询订单状态
        register("query_order", "根据订单编号查询订单状态",
                Map.of("orderId", Map.of("type", "string", "description", "订单编号")),
                List.of("orderId"),
                args -> {
                    String orderId = str(args, "orderId");
                    return "订单 " + orderId + " 当前状态：已发货（示例模拟数据，实际应查询订单数据库）";
                });

        // 示例工具 2：查询天气
        register("get_weather", "查询指定城市的天气",
                Map.of("city", Map.of("type", "string", "description", "城市名，如 北京")),
                List.of("city"),
                args -> {
                    String city = str(args, "city");
                    return city + " 今天晴，气温 25℃，空气质量优（示例模拟数据）";
                });

        // 示例工具 3：四则运算
        register("calculator", "计算四则运算表达式，支持 + - * / 和括号",
                Map.of("expression", Map.of("type", "string", "description", "数学表达式，如 (3+5)*2")),
                List.of("expression"),
                args -> {
                    String expression = str(args, "expression");
                    double value = new ExprParser(expression).parse();
                    return "计算结果：" + trim(value);
                });
    }

    /** 返回全部工具的声明（供大模型 {@code tools} 字段使用） */
    public List<ToolDefinition> toolDefinitions() {
        return registry.values().stream().map(Tool::definition).toList();
    }

    /** 返回工具名称 + 描述列表（供前端展示） */
    public List<Map<String, Object>> listTools() {
        return registry.values().stream()
//                .map(t -> Map.of(
//                        "name", t.definition().function().name(),
//                        "description", t.definition().function().description()))
                .map(t -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", t.definition().function().name());
                    map.put("description", t.definition().function().description());
                    return map;
                })
                .toList();
    }

    /**
     * 执行指定工具。
     *
     * @param name          工具名
     * @param argumentsJson 模型给出的参数（JSON 字符串）
     * @return 工具执行结果文本（会作为 role=tool 消息回传给模型）
     */
    public String execute(String name, String argumentsJson) {
        Tool tool = registry.get(name);
        if (tool == null) {
            return "未知工具：" + name;
        }
        Map<String, Object> args;
        try {
            args = (argumentsJson == null || argumentsJson.isBlank())
                    ? Map.of()
                    : objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return "工具参数解析失败：" + e.getMessage();
        }
        try {
            String result = tool.executor().execute(args);
            log.info("工具执行，name={}, args={} -> {}", name, args, result);
            return result;
        } catch (Exception e) {
            return "工具执行失败：" + e.getMessage();
        }
    }

    // ---------- 内部辅助 ----------

    private void register(String name, String description,
                          Map<String, Object> properties, List<String> required,
                          ToolExecutor executor) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);
        registry.put(name, new Tool(ToolDefinition.of(name, description, parameters), executor));
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static String trim(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(Math.round(v * 10000) / 10000.0);
    }

    // ---------- 数据结构 ----------

    /** 一个工具 = 声明 + 执行逻辑 */
    private record Tool(ToolDefinition definition, ToolExecutor executor) {
    }

    /** 工具执行逻辑：入参 -> 结果文本 */
    @FunctionalInterface
    public interface ToolExecutor {
        String execute(Map<String, Object> args);
    }

    /**
     * 极简四则运算求值器（递归下降，无外部依赖），
     * 支持 + - * /、括号与一元正负号。
     */
    private static final class ExprParser {
        private final String s;
        private int pos;

        ExprParser(String s) {
            this.s = s == null ? "" : s;
        }

        double parse() {
            double v = expr();
            skipWs();
            if (pos < s.length()) {
                throw new IllegalArgumentException("表达式存在多余字符：" + s.charAt(pos));
            }
            return v;
        }

        private double expr() {
            double v = term();
            while (true) {
                skipWs();
                if (match('+')) {
                    v += term();
                } else if (match('-')) {
                    v -= term();
                } else {
                    break;
                }
            }
            return v;
        }

        private double term() {
            double v = factor();
            while (true) {
                skipWs();
                if (match('*')) {
                    v *= factor();
                } else if (match('/')) {
                    v /= factor();
                } else {
                    break;
                }
            }
            return v;
        }

        private double factor() {
            skipWs();
            if (match('-')) {
                return -factor();
            }
            if (match('+')) {
                return factor();
            }
            if (match('(')) {
                double v = expr();
                skipWs();
                expect(')');
                return v;
            }
            return number();
        }

        private double number() {
            skipWs();
            int start = pos;
            while (pos < s.length()
                    && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("无效数字，位置 " + pos);
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private boolean match(char c) {
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            skipWs();
            if (!match(c)) {
                throw new IllegalArgumentException("期望字符 '" + c + "'，位置 " + pos);
            }
        }
    }
}

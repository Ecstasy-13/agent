package com.example.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agent.entity.Order;
import com.example.agent.knowledge.KnowledgeService;
import com.example.agent.llm.ToolDefinition;
import com.example.agent.repository.OrderMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 知识库检索工具默认返回片段数 */
    private static final int KNOWLEDGE_TOP_K = 3;

    private final KnowledgeService knowledgeService;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, Tool> registry = new LinkedHashMap<>();

    public ToolCallingService(ObjectMapper objectMapper, OrderMapper orderMapper, KnowledgeService knowledgeService) {
        this.objectMapper = objectMapper;
        this.orderMapper = orderMapper;
        this.knowledgeService = knowledgeService;

        // 示例工具 1：查询订单状态
        register("query_order", "根据订单编号查询订单状态",
                Map.of("orderId", Map.of("type", "string", "description", "订单编号")),
                List.of("orderId"),
                args -> queryOrder(str(args, "orderId")));

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
        /**
         * 工具 4：知识库检索（Agentic RAG）。
         *
         * 与传统 RAG"每次提问都先检索"不同，
         * 这里把检索能力包装成一个工具，
         * 由模型自己判断"这个问题是否需要参考已上传的资料"。
         *
         * 例如闲聊类问题模型会直接回答，不调用这个工具；
         * 涉及企业内部文档、产品细节等问题，模型会主动调用。
         */
        register("search_knowledge_base",
                "当用户的问题可能需要参考已上传的知识库文档才能准确回答时调用此工具，"
                        + "输入查询内容，返回最相关的资料片段。如果问题是常识性的、不需要查阅资料，不要调用此工具。",
                Map.of("query", Map.of("type", "string", "description", "要检索的问题或关键词")),
                List.of("query"),
                args -> searchKnowledgeBase(str(args, "query")));
    }

    /**
     * 查询订单的真实实现。
     *
     * <p>注意防御式编程：
     * 1. 查不到时返回一句人话，而不是抛异常打断整个对话；
     * 2. 不直接把 orderId 拼接成 SQL，全部走 MyBatis-Plus 参数化查询，杜绝 SQL 注入风险。
     */
    private String queryOrder(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return "未提供订单编号，无法查询";
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            return "未找到订单 " + orderNo + "，请确认订单编号是否正确";
        }
        return "订单 " + order.getOrderNo() + "：" + order.getProductName()
                + "，金额 " + order.getAmount() + " 元，当前状态：" + order.statusText();
    }

    /**
     * 知识库检索的真实实现。
     *
     * <p>命中为空时要返回一句明确的话，而不是空字符串，
     * 避免模型收到空结果后产生"资料检索成功但内容为空"的误解。
     */
    private String searchKnowledgeBase(String query) {
        if (query == null || query.isBlank()) {
            return "未提供检索关键词";
        }
        List<KnowledgeService.Hit> hits = knowledgeService.search(query, KNOWLEDGE_TOP_K);
        if (hits.isEmpty()) {
            return "知识库中没有找到与「" + query + "」相关的内容";
        }
        return hits.stream()
                .map(h -> "【来源：" + h.documentName() + "】" + h.text())
                .collect(Collectors.joining("\n\n"));
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
            log.info("工具执行，name={}, args={} -> {}", name, args, result.length() > 200 ? result.substring(0, 200) + "..." : result);
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

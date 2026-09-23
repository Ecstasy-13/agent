package com.example.agent.llm;

import com.example.agent.agent.model.AgentUsage;
import com.example.agent.model.Message;

import com.example.agent.model.ToolCall;
import com.example.agent.tool.ToolCallingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 基于 Spring AI 实现的 ModelService。
 *
 * <p>这个类的核心职责：
 * <p>
 * 1. 把项目自己的 Message 转换成 Spring AI Message；
 * 2. 使用 ChatClient / ChatModel 调用模型，并把结果转换回自己的 ModelCallResult；
 * 3. V2 新增：把项目自己的 {@link ToolDefinition} 转换成 Spring AI 的
 *    {@link ToolCallback}，并采用 Spring AI 官方的"用户控制式工具调用"
 *    （{@code internalToolExecutionEnabled(false)}），只调用一次模型、
 *    把 tool_calls 原样透传出去，不在这一层做多轮循环。
 * <p>
 * AgentRuntime 本身完全不知道底层使用的是 DashScope，也不知道底层用的是
 * Spring AI 的哪一种工具调用机制。
 */
@Service
public class SpringAiModelService implements ModelService {

    /**
     * Spring AI 提供的高级 Chat API。
     * <p>
     * ChatClient 底层最终会调用
     * Spring AI Alibaba 提供的 DashScopeChatModel。
     */
    private final ChatClient chatClient;

    /**
     * ChatModel 是比 ChatClient 更底层的 API。
     *
     * <p>chatWithTools 需要拿到完整的 {@link ChatResponse}
     * 来判断 {@code hasToolCalls()}，ChatClient 的高层 API
     * 不方便做到"只声明工具、但不让框架自动执行"，
     * 所以这里额外注入 ChatModel。
     * <p>
     * Spring AI Alibaba 会自动配置好 DashScopeChatModel 作为 ChatModel 实现，
     * 不需要我们手动创建。
     */
    private final ChatModel chatModel;

    /**
     * 工具的真正执行入口。
     *
     * <p>{@code ToolCallingService} 只依赖 {@code OrderMapper}、
     * {@code KnowledgeService}、{@code ObjectMapper}，不依赖
     * {@code ModelService}，两者之间不存在循环依赖，
     * 所以这里直接用构造器注入即可，不需要绕开成 setter。
     */
    private final ToolCallingService toolCallingService;

    /**
     * Spring Boot / Spring AI 会自动提供
     * ChatClient.Builder Bean。
     * <p>
     * 我们在构造器中 build 一个 ChatClient。
     */
    public SpringAiModelService(ChatClient.Builder builder,
                                ChatModel chatModel,
                                ToolCallingService toolCallingService) {
        this.chatClient = builder.build();
        this.chatModel = chatModel;
        this.toolCallingService = toolCallingService;
    }

    /**
     * 调用一次大语言模型。
     */
    @Override
    public ModelCallResult chat(List<Message> messages) {

        /*
         * 当前 AgentRuntime 使用的是项目自己的 Message。
         * Spring AI 只能识别：
         * org.springframework.ai.chat.messages.Message
         * 因此这里做一次模型转换。
         */
        List<org.springframework.ai.chat.messages.Message> springMessages = messages.stream()
                .map(this::convertMessage).toList();

        /**
         * 使用 ChatClient 调用模型。
         *
         * 为什么这里不用：
         *
         * .call()
         * .content()
         *
         * 因为 content() 只拿最终文本。
         *
         * 我们还需要 Token Usage 等 metadata，
         * 因此获取完整 ChatResponse。
         */
        ChatResponse response = chatClient.prompt().messages(springMessages).call().chatResponse();

        /*
         * 模型或者 Provider 出现异常情况时，
         * 不允许返回一个“看似成功但实际上为空”的结果。
         */
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {

            throw new IllegalStateException("大模型返回内容为空");
        }

        /*
         * getOutput() 对应 AssistantMessage。
         *
         * getText() 获取模型生成的自然语言文本。
         */
        String content = response.getResult().getOutput().getText();

        if (content == null || content.isBlank()) {

            throw new IllegalStateException("大模型返回文本为空");
        }

//        /*
//         * 从完整 ChatResponse 中抽取 Token Usage。
//         */
//        AgentUsage agentUsage = extractUsage(response);

        /*
         * Spring AI 对象到这里结束。
         *
         * 返回给上层的又变回我们自己的
         * ModelCallResult。
         */

        return ModelCallResult.textOnly(content, extractUsage(response));
    }

    /**
     * 调用一次大语言模型，并声明本轮可用的工具。
     *
     * <p>关键设计：{@code internalToolExecutionEnabled(false)}，
     * 让 Spring AI 只负责"把工具声明发给模型、把模型返回的 tool_calls 解析出来"，
     * 不负责自动执行工具、不负责自动发起下一轮调用——
     * 这两件事交给 {@code DefaultAgentRuntime} 自己控制，
     * 这样才能精确记录每一轮的 {@code AgentStep}。
     */
    @Override
    public ModelCallResult chatWithTools(List<Message> messages, List<ToolDefinition> tools) {

        if (tools == null || tools.isEmpty()) {
            return chat(messages);
        }

        List<org.springframework.ai.chat.messages.Message> springMessages = messages.stream()
                .map(this::convertMessage).toList();

        List<ToolCallback> callbacks = tools.stream().map(this::toToolCallback).toList();

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(callbacks)
                .internalToolExecutionEnabled(false)
                .build();

        Prompt prompt = new Prompt(springMessages, chatOptions);

        ChatResponse response = chatModel.call(prompt);

        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("大模型返回内容为空");
        }

        AssistantMessage output = response.getResult().getOutput();
        AgentUsage usage = extractUsage(response);

        if (response.hasToolCalls()) {
            /*
             * 模型想调用工具，此时 output.getText() 通常为 null 或空，
             * 把 Spring AI 的 AssistantMessage.ToolCall 转换回
             * 项目自己的 ToolCall，交还给 DefaultAgentRuntime 处理。
             */
            List<ToolCall> toolCalls = output.getToolCalls().stream()
                    .map(tc -> new ToolCall(
                            tc.id(),
                            "function",
                            new ToolCall.Function(tc.name(), tc.arguments())
                    ))
                    .toList();
            return new ModelCallResult(output.getText(), usage, toolCalls);
        }

        String content = output.getText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("大模型返回文本为空");
        }
        return ModelCallResult.textOnly(content, usage);
    }

    /**
     * 把项目自己的 {@link ToolDefinition} 适配成 Spring AI 的 {@link ToolCallback}。
     *
     * <p>没有用 Spring AI 的 {@code @Tool} 注解方式或 {@code FunctionToolCallback}，
     * 而是直接实现 {@code ToolCallback} 接口，
     * 因为我们已经有现成的执行入口
     * {@code ToolCallingService.execute(name, argumentsJson)}，
     * 包一层适配器即可复用，不需要为每个工具单独写一个 Java 方法/Bean。
     */
    private ToolCallback toToolCallback(ToolDefinition definition) {

        String toolName = definition.function().name();
        String toolDescription = definition.function().description();
        String inputSchemaJson = toJsonSchema(definition.function().parameters());

        org.springframework.ai.tool.definition.ToolDefinition springToolDefinition =
                org.springframework.ai.tool.definition.ToolDefinition.builder()
                        .name(toolName)
                        .description(toolDescription)
                        .inputSchema(inputSchemaJson)
                        .build();

        return new DelegatingToolCallback(springToolDefinition, this.toolCallingService);
    }

    /**
     * {@link ToolCallback} 的自定义实现：把 Spring AI 的工具调用请求
     * 转发给 {@code ToolCallingService.execute(...)}。
     */
    private static final class DelegatingToolCallback implements ToolCallback {

        private final org.springframework.ai.tool.definition.ToolDefinition definition;
        private final ToolCallingService toolCallingService;

        DelegatingToolCallback(org.springframework.ai.tool.definition.ToolDefinition definition,
                               ToolCallingService toolCallingService) {
            this.definition = definition;
            this.toolCallingService = toolCallingService;
        }

        @Override
        public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            return toolCallingService.execute(definition.name(), toolInput);
        }

        @Override
        public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
            return call(toolInput);
        }
    }


    /**
            * 把项目自己 {@code Map<String, Object>} 形式的 JSON Schema
     * 转换成字符串，供 Spring AI 的 {@code ToolDefinition.inputSchema} 使用。
            */
    private String toJsonSchema(Map<String, Object> parameters) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(parameters);
        } catch (Exception e) {
            throw new IllegalStateException("工具参数 Schema 序列化失败", e);
        }
    }

    /**
     * 将项目自己的 Message
     * 转成 Spring AI 的 Message。
     */
    private org.springframework.ai.chat.messages.Message convertMessage(Message message) {

        return switch (message.role()) {

            case "system" -> new SystemMessage(message.content());

            case "user" -> new UserMessage(message.content());

            case "assistant" -> {
                if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                    List<AssistantMessage.ToolCall> springToolCalls = message.toolCalls().stream()
                            .map(tc -> new AssistantMessage.ToolCall(
                                    tc.id(),
                                    "function",
                                    tc.function().name(),
                                    tc.function().arguments()
                            ))
                            .toList();
                    yield new AssistantMessage(message.content(), Map.of(), springToolCalls);
                }
                yield new AssistantMessage(message.content());
            }

            case "tool" -> {
                /*
                 * 项目自己的 Message.tool(toolCallId, content) 只保存了 toolCallId，
                 * 没有保存工具名（name），Spring AI 的 ToolResponse 需要 name 字段，
                 * 这里用空字符串占位——绝大多数模型只依赖 tool_call_id 做匹配，
                 * name 字段留空不影响 Qwen 兼容接口的正确性。
                 */
                ToolResponseMessage.ToolResponse toolResponse =
                        new ToolResponseMessage.ToolResponse(message.toolCallId(), "", message.content());
                yield new ToolResponseMessage(List.of(toolResponse), Map.of());
            }

            default -> throw new IllegalArgumentException("不支持的消息类型: " + message.role());
        };
    }


    /**
     * 将 Spring AI ChatResponse 中的 Token Usage
     * 转换成项目自己的 AgentUsage。
     */
    private AgentUsage extractUsage(ChatResponse response) {

        /*
         * 理论上 Spring AI 通常会提供 metadata。
         *
         * 但模型 Provider 是否真的返回 Usage
         * 不能完全由我们保证。
         */
        if (response.getMetadata() == null) {
            return AgentUsage.empty();
        }

        Usage usage = response.getMetadata().getUsage();

        if (usage == null) {
            return AgentUsage.empty();
        }

        return new AgentUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }
}

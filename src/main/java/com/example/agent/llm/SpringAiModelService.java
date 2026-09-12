package com.example.agent.llm;

import com.example.agent.agent.model.AgentUsage;
import com.example.agent.model.Message;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基于 Spring AI 实现的 ModelService。
 *
 * <p>这个类的核心职责只有两个：
 * <p>
 * 1. 把项目自己的 Message 转换成 Spring AI Message；
 * 2. 使用 ChatClient 调用模型，并把结果转换回自己的 ModelCallResult。
 *
 * <p>也就是说它是一个 Adapter：
 *
 * <pre>
 * 项目领域模型
 *      ↓
 * SpringAiModelService
 *      ↓
 * Spring AI
 * </pre>
 * <p>
 * AgentRuntime 本身完全不知道底层使用的是 DashScope。
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
     * Spring Boot / Spring AI 会自动提供
     * ChatClient.Builder Bean。
     * <p>
     * 我们在构造器中 build 一个 ChatClient。
     */
    public SpringAiModelService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
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

        /*
         * 从完整 ChatResponse 中抽取 Token Usage。
         */
        AgentUsage agentUsage = extractUsage(response);

        /*
         * Spring AI 对象到这里结束。
         *
         * 返回给上层的又变回我们自己的
         * ModelCallResult。
         */

        return new ModelCallResult(content, agentUsage);
    }

    /**
     * 将项目自己的 Message
     * 转成 Spring AI 的 Message。
     */
    private org.springframework.ai.chat.messages.Message convertMessage(Message message) {

        String content = message.content();

        /*
         * V1 只支持：
         *
         * system
         * user
         * assistant
         *
         * 原项目 Message 中虽然已经支持 tool，
         * 但 V1 AgentRuntime 尚未实现 Tool Loop。
         *
         * 所以这里故意不提前支持。
         */
        return switch (message.role()) {

            case "system" -> new org.springframework.ai.chat.messages.SystemMessage(content);

            case "user" -> new org.springframework.ai.chat.messages.UserMessage(content);

            case "assistant" -> new org.springframework.ai.chat.messages.AssistantMessage(content);

            default -> throw new IllegalArgumentException("V1 AgentRuntime 暂不支持消息类型: " + message.role());
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

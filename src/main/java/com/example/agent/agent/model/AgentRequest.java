package com.example.agent.agent.model;

/**
 * 一次 Agent 运行请求。
 *
 * <p>注意：
 * AgentRequest 表示的是“Agent 内部执行请求”，
 * 它不是 HTTP Controller 直接接收的 DTO。
 *
 * <p>HTTP 请求会先进入 AgentChatRequest，
 * 然后由 ApplicationService 转换成 AgentRequest。
 *
 * <p>三个 ID / 字段的职责：
 *
 * <pre>
 * userId
 *   ↓
 * 标识“是谁”
 *
 * conversationId
 *   ↓
 * 标识“在哪一段会话中”
 *
 * message
 *   ↓
 * 标识“这一次用户说了什么”
 * </pre>
 *
 * @param userId
 *        用户唯一标识。
 *
 *        一个 user 可以拥有多个 conversation。
 *
 *        V1 暂时由客户端传入，
 *        后面接入 Spring Security 后，
 *        会改成从登录态 / SecurityContext 获取。
 *
 * @param conversationId
 *        会话唯一标识。
 *
 *        用于隔离同一个用户的不同聊天。
 *
 *        例如：
 *
 *        user-001
 *          ├── conv-java
 *          ├── conv-order
 *          └── conv-travel
 *
 *        三个 conversation 的短期上下文互不影响。
 *
 * @param message
 *        当前这一轮用户输入的原始文本。
 *
 *        注意：
 *        这里只保存“当前消息”，
 *        历史消息由 ConversationMemory 负责读取，
 *        不应该让 Controller 自己把历史消息传进来。
 */
public record AgentRequest(

        // 当前请求属于哪个用户。
        String userId,

        // 当前请求属于哪一段会话。
        String conversationId,

        // 用户当前这一轮输入的内容。
        String message

) {

    /**
     * Record 的紧凑构造器（Compact Constructor）。
     *
     * <p>Java 会自动生成真正的构造器：
     *
     * <pre>
     * new AgentRequest(userId, conversationId, message)
     * </pre>
     *
     * 这里主要用于统一做数据合法性校验。
     */
    public AgentRequest {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException(
                    "userId 不能为空"
            );
        }

        if (conversationId == null
                || conversationId.isBlank()) {

            throw new IllegalArgumentException(
                    "conversationId 不能为空"
            );
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "message 不能为空"
            );
        }
    }
}

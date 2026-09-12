package com.example.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v2/agent/chat
 * 对应的 HTTP Request Body。
 *
 * <p>注意它和 AgentRequest 不一样。
 *
 * AgentChatRequest：
 * 属于 API 层。
 *
 * AgentRequest：
 * 属于 Agent Runtime 层。
 *
 * @param userId
 *        当前请求用户 ID。
 *
 *        V1 暂时允许前端传。
 *
 *        后面加 Spring Security 后，
 *        会从登录身份获取，
 *        到时候应该从 DTO 删除这个字段。
 *
 * @param conversationId
 *        Conversation ID。
 *
 *        第一次聊天允许为空。
 *
 *        后端会自动生成。
 *
 *        第二次继续聊天时，
 *        前端应该把第一次返回的
 *        conversationId 带回来。
 *
 * @param message
 *        用户本轮输入。
 */
public record AgentChatRequest(

        @NotBlank(message = "userId 不能为空") String userId,

        /*
         * 不加 @NotBlank。
         *
         * 因为第一次聊天允许没有 conversationId。
         */
        String conversationId,

        @NotBlank(message = "message 不能为空") String message

) {
}

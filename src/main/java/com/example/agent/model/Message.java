package com.example.agent.model;

/**
 * 一条对话消息。
 *
 * <p>对应需求文档 4.3 短期记忆的数据结构：
 * <pre>
 * Message {
 *   role:    user / assistant / system
 *   content: 消息内容
 * }
 * </pre>
 * 该结构同时复用于调用大模型时的消息体。
 */
public record Message(String role, String content) {

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

    public static Message system(String content) {
        return new Message("system", content);
    }
}

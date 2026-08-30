package com.example.agent.dto;

/**
 * 长期记忆条目（键值对）。
 *
 * <p>用于前端「记忆面板」展示：一条长期记忆由「类型 + 内容」组成，
 * 例如 {@code key="技能", value="Java"}。
 */
public record MemoryItem(String key, String value) {
}

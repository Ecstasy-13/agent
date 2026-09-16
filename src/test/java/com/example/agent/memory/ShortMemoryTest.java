package com.example.agent.memory;

import com.example.agent.config.AgentProperties;
import com.example.agent.model.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 短期记忆窗口测试。
 */
class ShortMemoryTest {

//    @Test
//    void shouldKeepHistoryWithinMaxSize() {
//        AgentProperties properties = new AgentProperties();
//        properties.getMemory().setShortTermSize(4);
//
//        ShortMemory memory = new ShortMemory(properties);
//
//        for (int i = 1; i <= 6; i++) {
//            memory.add("u1", Message.user("消息" + i));
//        }
//
//        List<Message> history = memory.getHistory("u1");
//        assertThat(history).hasSize(4);
//        // 应保留最近的 4 条（消息3 ~ 消息6）
//        assertThat(history.get(0).content()).isEqualTo("消息3");
//        assertThat(history.get(3).content()).isEqualTo("消息6");
//    }
//
//    @Test
//    void shouldIsolateSessionsByUser() {
//        AgentProperties properties = new AgentProperties();
//        ShortMemory memory = new ShortMemory(properties);
//
//        memory.add("u1", Message.user("A"));
//        memory.add("u2", Message.user("B"));
//
//        assertThat(memory.getHistory("u1")).hasSize(1);
//        assertThat(memory.getHistory("u2")).hasSize(1);
//        assertThat(memory.getHistory("u3")).isEmpty();
//    }
}

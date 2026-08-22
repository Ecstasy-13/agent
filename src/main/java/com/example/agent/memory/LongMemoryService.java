package com.example.agent.memory;

import com.example.agent.config.AgentProperties;
import com.example.agent.entity.UserMemory;
import com.example.agent.llm.QwenClient;
import com.example.agent.model.Message;
import com.example.agent.repository.UserMemoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 长期记忆：保存用户长期信息，使 Agent 具备用户画像能力。
 *
 * <p>对应需求文档 4.4。核心思路：
 * <ol>
 *   <li>对话结束后，异步调用大模型从用户消息中抽取长期、稳定的个人事实；</li>
 *   <li>以「记忆类型 - 记忆内容」键值对形式保存到 MySQL 的 user_memory 表；</li>
 *   <li>下次对话构造 Prompt 时，把用户画像作为上下文注入。</li>
 * </ol>
 */
@Service
public class LongMemoryService {

    private static final Logger log = LoggerFactory.getLogger(LongMemoryService.class);

    /** 记忆抽取 Prompt：要求模型只输出 JSON 数组（{message} 为用户消息占位符） */
    private static final String EXTRACT_PROMPT = """
            你是用户长期记忆抽取助手。请从下面的用户消息中，抽取关于用户的长期、稳定个人信息（如姓名、职业、技能、方向、偏好、所在地等）。
            只抽取明确陈述的事实，不要猜测。
            以 JSON 数组格式返回，每个元素为 {"key": "信息类别", "value": "具体内容"}。
            如果没有可抽取的信息，返回空数组 []。
            只返回 JSON，不要输出任何其他内容。

            用户消息：
            {message}
            """;

    private final UserMemoryRepository repository;
    private final QwenClient qwenClient;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public LongMemoryService(UserMemoryRepository repository,
                             QwenClient qwenClient,
                             AgentProperties properties,
                             ObjectMapper objectMapper) {
        this.repository = repository;
        this.qwenClient = qwenClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取用户画像文本，用于注入 Prompt。
     *
     * <p>格式示例：{@code 姓名: 王浩；技能: Java；方向: 后端开发}
     */
    public String getUserProfile(String userId) {
        List<UserMemory> memories = repository.findByUserId(userId);
        if (memories == null || memories.isEmpty()) {
            return "";
        }
        return memories.stream()
                .map(m -> m.getMemoryKey() + ": " + m.getMemoryValue())
                .collect(Collectors.joining("；"));
    }

    /**
     * 异步抽取并保存用户长期记忆。抽取失败不影响主对话流程。
     */
    @Async
    public void extractAndSave(String userId, String userMessage) {
        if (!properties.getMemory().isExtractEnabled()) {
            return;
        }
        try {
            List<Fact> facts = extractFacts(userMessage);
            for (Fact fact : facts) {
                saveOrUpdate(userId, fact.key(), fact.value());
            }
            if (!facts.isEmpty()) {
                log.info("已抽取 {} 条长期记忆，userId={}", facts.size(), userId);
            }
        } catch (Exception e) {
            // 长期记忆抽取是增强能力，失败不应阻断对话
            log.warn("长期记忆抽取失败，userId={}, 原因: {}", userId, e.getMessage());
        }
    }

    /**
     * 手动保存一条长期记忆。
     */
    public void save(String userId, String key, String value) {
        saveOrUpdate(userId, key, value);
    }

    /**
     * 调用大模型抽取用户事实。
     */
    private List<Fact> extractFacts(String userMessage) throws JsonProcessingException {
        // 使用占位符替换而非 String.format，避免用户消息中的 % 导致格式化异常
        String prompt = EXTRACT_PROMPT.replace("{message}", userMessage);
        String raw = qwenClient.chat(List.of(Message.system(prompt)));

        // 大模型输出可能带额外说明，截取首尾方括号之间的 JSON 片段
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            return List.of();
        }
        String json = raw.substring(start, end + 1);

        JsonNode array = objectMapper.readTree(json);
        if (!array.isArray()) {
            return List.of();
        }
        List<Fact> facts = new java.util.ArrayList<>();
        for (JsonNode node : array) {
            String key = node.hasNonNull("key") ? node.get("key").asText().trim() : null;
            String value = node.hasNonNull("value") ? node.get("value").asText().trim() : null;
            if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
                facts.add(new Fact(key, value));
            }
        }
        return facts;
    }

    private void saveOrUpdate(String userId, String key, String value) {
        repository.findByUserIdAndMemoryKey(userId, key).ifPresentOrElse(
                existing -> {
                    existing.setMemoryValue(value);
                    repository.save(existing);
                },
                () -> repository.save(new UserMemory(userId, key, value))
        );
    }

    /** 抽取结果：一条「类型 - 内容」事实 */
    private record Fact(String key, String value) {
    }
}

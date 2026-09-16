package com.example.agent.memory;

import com.example.agent.config.AgentProperties;
import com.example.agent.dto.MemoryItem;
import com.example.agent.entity.UserMemory;
import com.example.agent.llm.ModelCallResult;
import com.example.agent.llm.ModelService;
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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 长期记忆服务。
 *
 * <p>负责：
 *
 * 1. 查询用户长期画像；
 * 2. 从聊天中抽取长期事实；
 * 3. 将长期事实保存 MySQL。
 *
 * <p>注意：
 * 这里不再直接依赖 QwenClient。
 *
 * LongMemoryService
 *      ↓
 * ModelService
 *      ↓
 * SpringAiModelService
 *
 * 从而避免长期记忆业务与 Qwen 厂商绑定。
 */
@Service
public class LongMemoryService {

    private static final Logger log = LoggerFactory.getLogger(LongMemoryService.class);

    /**
     * 长期事实抽取 Prompt。
     *
     * 注意这里不再把真实 userMessage
     * 直接拼进 System Prompt。
     *
     * System Message：
     * 定义任务规则。
     *
     * User Message：
     * 放待抽取的真实用户输入。
     */
    private static final String EXTRACT_PROMPT = """
            你是用户长期记忆抽取助手。

            请从用户消息中抽取长期、稳定的个人信息，
            例如姓名、职业、技能、方向、偏好、所在地等。

            规则：
            1. 只抽取用户明确陈述的信息；
            2. 不要推测；
            3. 临时状态不要记录为长期记忆；
            4. 只返回 JSON 数组；
            5. 每个元素格式：
               {"key": "信息类别", "value": "具体内容"}
            6. 没有长期信息时返回 []。

            不要输出 Markdown。
            不要输出解释。
            """;

    /**
     * MySQL Repository。
     */
    private final UserMemoryRepository repository;

    /**
     * 统一 LLM 调用接口。
     *
     * 不再使用 QwenClient。
     */
    private final ModelService modelService;

    /**
     * agent.memory.* 配置。
     */
    private final AgentProperties properties;

    /**
     * Jackson JSON 解析器。
     *
     * 将 LLM 返回的 JSON 数组
     * 解析成 Fact。
     */
    private final ObjectMapper objectMapper;

    public LongMemoryService(
            UserMemoryRepository repository,
            ModelService modelService,
            AgentProperties properties,
            ObjectMapper objectMapper
    ) {

        this.repository = repository;
        this.modelService = modelService;
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
     * 查询用户全部长期记忆（键值对），用于前端「记忆面板」展示。
     *
     * <p>返回空列表表示该用户暂无长期记忆。
     */
    public List<MemoryItem> getUserMemories(String userId) {
        List<UserMemory> memories = repository.findByUserId(userId);
        if (memories == null || memories.isEmpty()) {
            return List.of();
        }
        return memories.stream()
                .map(m -> new MemoryItem(m.getMemoryKey(), m.getMemoryValue()))
                .collect(Collectors.toList());
    }

    /**
     * 清空用户全部长期记忆。
     */
    public void clearUserMemory(String userId) {
        repository.deleteByUserId(userId);
    }

    /**
     * 异步抽取并保存用户长期记忆。抽取失败不影响主对话流程。
     */
    @Async
    public void extractAndSave(String userId, String userMessage) {
//        if (!properties.getMemory().isExtractEnabled()) {
//            return;
//        }
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
     * 调用大模型，从用户输入中抽取长期事实。
     */
    private List<Fact> extractFacts(String userMessage) throws JsonProcessingException {

        /*
         * System Message：
         * 告诉模型它需要干什么。
         *
         * User Message：
         * 放真正需要分析的数据。
         *
         * 比旧版把用户内容直接拼进 System Prompt
         * 更符合 Chat Message 的职责划分。
         */
        ModelCallResult result = modelService.chat(
                        List.of(Message.system(EXTRACT_PROMPT), Message.user(userMessage)));

        String raw = result.content();

        /*
         * 模型理论上应该只返回 JSON。
         *
         * 但 LLM 并不是完全确定性的，
         * 有时候可能产生：
         *
         * “结果如下：[...]”
         *
         * 所以这里保持旧项目中的防御式解析：
         * 找第一个 '[' 和最后一个 ']'。
         */
        int start =
                raw.indexOf('[');

        int end =
                raw.lastIndexOf(']');

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
            String key = node.hasNonNull("key")
                            ? node.get("key")
                            .asText()
                            .trim()
                            : null;

            String value = node.hasNonNull("value")
                            ? node.get("value")
                            .asText()
                            .trim()
                            : null;

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
//        Optional<UserMemory> optional = repository.findByUserIdAndMemoryKey(userId, key);
//        if (optional.isPresent()) {
//            UserMemory existing = optional.get();
//            existing.setMemoryValue(value);
//            repository.save(existing);
//        } else {
//            repository.save(new UserMemory(userId, key, value));
//        }
    }

    /**
     * LLM 抽取出来的一条长期事实。
     *
     * @param key
     *        事实类型。
     *
     *        例如：
     *        姓名
     *        职业
     *        技能
     *
     * @param value
     *        事实内容。
     *
     *        例如：
     *        小明
     *        Java开发
     *        Spring Boot
     */
    private record Fact(String key, String value) {
    }
}

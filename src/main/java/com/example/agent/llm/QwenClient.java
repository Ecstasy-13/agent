package com.example.agent.llm;

import com.example.agent.config.QwenProperties;
import com.example.agent.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 大模型客户端，负责调用阿里云通义千问（DashScope OpenAI 兼容接口）。
 *
 * <p>对应需求文档 6.3 LLM 模块核心类 {@code QwenClient}。除对话外，
 * 还支持 Function Calling（带 tools 的对话）与 Embedding（文本向量化，供 RAG 使用）。
 */
@Service
public class QwenClient {

    private static final Logger log = LoggerFactory.getLogger(QwenClient.class);

    private final RestTemplate restTemplate;
    private final QwenProperties properties;

    public QwenClient(RestTemplate restTemplate, QwenProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /** 发起一次普通对话，返回模型生成的文本。 */
    public String chat(List<Message> messages) {
        return chat(messages, null);
    }

    /** 带工具的对话：返回模型文本（若模型返回 tool_calls，由调用方自行处理）。 */
    public String chat(List<Message> messages, List<ToolDefinition> tools) {
        QwenChatResponse result = chatRaw(messages, tools);
        String content = result == null ? null : result.firstContent();
        if (content == null) {
            throw new IllegalStateException("大模型返回内容为空");
        }
        return content;
    }

    /**
     * 返回完整响应，供 Function Calling 循环读取 {@code tool_calls}。
     */
    public QwenChatResponse chatRaw(List<Message> messages, List<ToolDefinition> tools) {
        String url = properties.getBaseUrl() + "/chat/completions";
        QwenChatRequest body = new QwenChatRequest(
                properties.getModel(),
                messages,
                properties.getTemperature(),
                tools,
                "auto"
        );
        HttpEntity<QwenChatRequest> entity = new HttpEntity<>(body, jsonHeaders());
        log.info("调用大模型，model={}, 消息条数={}, 工具数={}",
                properties.getModel(), messages.size(), tools == null ? 0 : tools.size());

        ResponseEntity<QwenChatResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, QwenChatResponse.class);
        return response.getBody();
    }

    /** 文本向量化（Embedding），返回 float 向量。 */
    public float[] embed(String text) {
        String url = properties.getBaseUrl() + "/embeddings";
        Map<String, Object> body = Map.of(
                "model", properties.getEmbeddingModel(),
                "input", text
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());

        ResponseEntity<QwenEmbeddingResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, QwenEmbeddingResponse.class);
        QwenEmbeddingResponse result = response.getBody();
        List<Double> vec = result == null ? null : result.firstEmbedding();
        if (vec == null || vec.isEmpty()) {
            throw new IllegalStateException("Embedding 返回为空");
        }
        float[] out = new float[vec.size()];
        for (int i = 0; i < vec.size(); i++) {
            out[i] = vec.get(i).floatValue();
        }
        return out;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        return headers;
    }
}

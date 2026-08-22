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

/**
 * 大模型客户端，负责调用阿里云通义千问（DashScope OpenAI 兼容接口）。
 *
 * <p>对应需求文档 6.3 LLM 模块核心类 {@code QwenClient}。
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

    /**
     * 发起一次对话，返回模型生成的文本。
     *
     * @param messages 完整对话上下文（含 system 提示、历史消息、当前问题）
     * @return 模型回复文本
     */
    public String chat(List<Message> messages) {
        String url = properties.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        QwenChatRequest body = new QwenChatRequest(
                properties.getModel(),
                messages,
                properties.getTemperature()
        );

        HttpEntity<QwenChatRequest> entity = new HttpEntity<>(body, headers);
        log.info("调用大模型，model={}, 消息条数={}", properties.getModel(), messages.size());

        ResponseEntity<QwenChatResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, QwenChatResponse.class);

        QwenChatResponse result = response.getBody();
        String content = result == null ? null : result.firstContent();
        if (content == null) {
            throw new IllegalStateException("大模型返回内容为空");
        }
        return content;
    }
}

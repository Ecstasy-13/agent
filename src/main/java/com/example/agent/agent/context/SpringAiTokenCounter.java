package com.example.agent.agent.context;


import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring AI TokenCountEstimator
 * 实现的 TokenCounter。
 *
 * <p>当前使用 JTokkitTokenCountEstimator。
 *
 * Spring AI 默认实现使用 CL100K_BASE 编码。
 *
 * <p>必须注意：
 *
 * CL100K_BASE 是 OpenAI 系 tokenizer，
 * 并不是 Qwen 官方 tokenizer。
 *
 * 因此这里得到的是：
 *
 * “Context Budget 估算值”
 *
 * 而不是：
 *
 * “DashScope 精确计费 Token”。
 *
 * <p>真正准确的本次调用 Token Usage，
 * 仍然以模型返回的：
 *
 * ChatResponse.metadata.usage
 *
 * 为准。
 *
 * 这里的目的只是：
 *
 * 在发送请求之前，
 * 防止 Context 无限膨胀。
 */

@Component
public class SpringAiTokenCounter implements TokenCounter{

    /**
     * Spring AI Token Estimator。
     */
    private final TokenCountEstimator estimator;

    public SpringAiTokenCounter() {
        this.estimator = new JTokkitTokenCountEstimator();
    }

    /**
     * 估算文本 Token 数。
     */
    @Override
    public int count(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return estimator.estimate(text);
    }
}

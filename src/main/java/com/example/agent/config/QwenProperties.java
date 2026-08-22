package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通义千问（Qwen / DashScope）大模型配置。
 *
 * <p>对应 application.yml 中的 {@code qwen.*} 配置项。
 */
@ConfigurationProperties(prefix = "qwen")
public class QwenProperties {

    /** DashScope API Key */
    private String apiKey;

    /** OpenAI 兼容接口根地址 */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** 模型名称，如 qwen-plus / qwen-turbo / qwen-max */
    private String model = "qwen-plus";

    /** 采样温度，越大越随机 */
    private double temperature = 0.7;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}

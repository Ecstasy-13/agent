package com.example.agent.llm;

import java.util.List;

/**
 * 通义千问 Embedding 接口响应体。
 *
 * <p>DashScope OpenAI 兼容接口 {@code POST /embeddings} 的返回结构：
 * <pre>
 * { "data": [ { "embedding": [0.12, 0.34, ...] } ], ... }
 * </pre>
 * 只映射所需字段。
 */
public record QwenEmbeddingResponse(List<Data> data) {

    public record Data(List<Double> embedding) {
    }

    /** 取出第一条文本的向量 */
    public List<Double> firstEmbedding() {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return data.get(0).embedding();
    }
}

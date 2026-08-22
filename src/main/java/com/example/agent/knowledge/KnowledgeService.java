package com.example.agent.knowledge;

/**
 * RAG 知识库服务（第三阶段，后续版本）。
 *
 * <p>对应需求文档 4.5。目标：让 Agent 读取外部文档（PDF / Word / Markdown / TXT）
 * 并根据文档回答问题。
 *
 * <p>技术流程：
 * <pre>
 * 文档上传 -> 文本解析 -> 文本切分 -> Embedding 向量化 -> 保存向量数据库
 *   -> 用户提问 -> 相似度检索 -> LLM 生成回答
 * </pre>
 *
 * <p>技术选型：Spring AI Alibaba / LangChain4j + Milvus + Embedding 模型。
 */
public class KnowledgeService {

    /**
     * 文档上传与入库（待实现）。
     *
     * <p>步骤：解析文本 -> 切分 chunk -> 向量化 -> 写入向量数据库。
     */
    public void uploadDocument(String path) {
        throw new UnsupportedOperationException("RAG 知识库为后续版本功能，尚未实现。");
    }

    /**
     * 相似度检索（待实现）。
     *
     * <p>步骤：对用户提问向量化 -> 在向量数据库中做 TopK 相似度检索。
     */
    public void search(String query, int topK) {
        throw new UnsupportedOperationException("RAG 知识库为后续版本功能，尚未实现。");
    }
}

package com.example.agent.knowledge;

import com.example.agent.llm.QwenClient;
import com.example.agent.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RAG 知识库服务（第三阶段）。
 *
 * <p>对应需求文档 4.5。采用「轻量自包含」实现，不依赖外部向量库：
 * <pre>
 * 文档入库 -> 文本切分 -> Embedding 向量化（Qwen Embedding）-> 存入内存向量库
 * 提问 -> 向量化 -> 余弦相似度检索 TopK -> 拼接上下文 -> LLM 生成回答
 * </pre>
 *
 * <p>说明：真正的生产环境会把「内存向量库」替换为 Milvus 等向量数据库，
 * 这里用 {@link ConcurrentHashMap} 模拟，便于零依赖演示。
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    /** 单个 chunk 最大字符数 */
    private static final int CHUNK_SIZE = 800;

    /** 重叠部分     */
    private static final int CHUNK_OVERLAP = 150;


    /** 本地兜底向量维度（远程 Embedding 失败时使用） */
    private static final int VECTOR_DIM = 512;

    /** 检索返回的 TopK 数量 */
    private static final int TOP_K = 3;

    private final QwenClient qwenClient;
    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final AtomicInteger idSeq = new AtomicInteger(0);

    /** 默认最低相似度阈值，仅作为实验初始值，需要根据实际数据调整 */
    private static final double MIN_SCORE = 0.50;


    /** 远程 Embedding 是否可用（首次失败后回退到本地向量化） */
    private volatile boolean remoteEmbedding = true;

    public KnowledgeService(QwenClient qwenClient) {
        this.qwenClient = qwenClient;
    }

    /** 文档入库：切分 -> 向量化 -> 存储 */
    public Document addDocument(String name, String content) {
        List<Chunk> chunks = new ArrayList<>();
        for (String text : chunk(content)) {
            chunks.add(new Chunk(text, embed(text)));
        }
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("文档内容为空，无法入库");
        }
        String id = "doc-" + idSeq.incrementAndGet();
        Document doc = new Document(id, name, chunks);
        documents.put(id, doc);
        log.info("知识文档入库，id={}, name={}, chunks={}", id, name, chunks.size());
        return doc;
    }

    /** 列出全部文档（概要） */
    public List<Document> listDocuments() {
        return new ArrayList<>(documents.values());
    }

    /** 相似度检索，返回按相关度降序的 TopK 命中 */
    public List<Hit> search(String query, int topK) {
        // 1. 基本检查
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // 知识库为空，就没有必要调用 Embedding API
        if (documents.isEmpty()) {
            return List.of();
        }
        float[] queryVector = embed(query);
        List<Hit> hits = new ArrayList<>();
        for (Document doc : documents.values()) {
            List<Chunk> chunks = doc.chunks();
            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                Chunk chunk = chunks.get(chunkIndex);
                double score = cosine(queryVector, chunk.vector());
                hits.add(new Hit(doc.id(), doc.name(), chunkIndex, chunk.text(), score));
            }
//            for (Chunk c : doc.chunks()) {
//                hits.add(new Hit(doc.name(), c.text(), cosine(queryVector, c.vector())));
//            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        int debugLimit = Math.min(10, hits.size());
        for (int i = 0; i < debugLimit; i++) {
            Hit hit = hits.get(i);
            log.info(
                    "RAG candidate rank={}, docId={}, chunkIndex={}, score={}, text={}",
                    i + 1,
                    hit.documentId(),
                    hit.chunkIndex(),
                    String.format("%.4f", hit.score()),
                    preview(hit.text())
            );
        }

        // 6. Threshold 过滤 + TopK
        int safeTopK = Math.max(topK, 1);
        return hits.stream()
                .filter(hit -> hit.score() >= MIN_SCORE)
                .limit(safeTopK)
                .toList();
    }

    private String preview(String text) {

        String normalized = text
                .replace("\n", " ")
                .replace("\r", " ");

        int maxLength = 80;

        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, maxLength) + "...";
    }

    /** 提问：检索 + 生成 */
    public Answer ask(String question) {
        List<Hit> hits = search(question, TOP_K);
        if (hits.isEmpty()) {
            return new Answer("知识库为空或没有检索到相关内容，请先上传文档。", hits);
        }
        StringBuilder context = new StringBuilder();
        for (Hit h : hits) {
            context.append("【").append(h.documentName()).append("】\n").append(h.text()).append("\n\n");
        }
        String prompt = """
                你是一个知识库问答助手。请只根据下面提供的资料回答问题。
                如果资料中没有答案，请明确说"资料中没有相关信息"，不要编造。

                资料：
                %s

                问题：
                %s
                """.formatted(context.toString(), question);
        String answer = qwenClient.chat(List.of(Message.system(prompt)));
        return new Answer(answer, hits);
    }

    // ---------- 文本切分 ----------

    private List<String> chunk(String content) {
        List<String> result = new ArrayList<>();
        for (String para : content.split("\n+")) {
            String p = para.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (CHUNK_OVERLAP >= CHUNK_SIZE) {
                throw new IllegalArgumentException();
            }
            if (p.length() <= CHUNK_SIZE) {
                result.add(p);
            } else {
                for (int i = 0; i < p.length(); i += CHUNK_SIZE - CHUNK_OVERLAP) {
                    int end = Math.min(i + CHUNK_SIZE, p.length());
                    result.add(p.substring(i, end));
                    if (end == p.length()) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    // ---------- 向量化 ----------

    private float[] embed(String text) {
        float[] vector = qwenClient.embed(text);

        log.info(
                "embedding: text={}, dimension={}",
                text.substring(0, Math.min(20, text.length())),
                vector.length
        );

        if (remoteEmbedding) {
            try {
                return qwenClient.embed(text);
            } catch (Exception e) {
                log.warn("远程 Embedding 调用失败，回退到本地哈希向量化：{}", e.getMessage());
                remoteEmbedding = false;
            }
        }
        return localVector(text);
    }

    /** 本地兜底向量化：基于字符哈希的 bag-of-chars（无外部依赖） */
    private float[] localVector(String text) {
        float[] v = new float[VECTOR_DIM];
        String s = text.toLowerCase();
        for (int i = 0; i < s.length(); i++) {
            int prev = i > 0 ? s.charAt(i - 1) : 0;
            int idx = (s.charAt(i) * 31 + prev) & (VECTOR_DIM - 1);
            v[idx] += 1.0f;
        }
        normalize(v);
        return v;
    }

    private void normalize(float[] v) {
        double norm = 0;
        for (float x : v) {
            norm += (double) x * x;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return;
        }
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
    }

    private double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // ---------- 数据结构 ----------

    /** 入库文档 */
    public record Document(String id, String name, List<Chunk> chunks) {
        public int chunkCount() {
            return chunks.size();
        }
    }

    /** 检索命中结果 */
    public record Hit(
            String documentId, String documentName, int chunkIndex,String text, double score) {
    }

    /** 提问结果：回答 + 命中的来源片段 */
    public record Answer(String answer, List<Hit> hits) {
    }

    /** 一个文本块及其向量 */
    private record Chunk(String text, float[] vector) {
    }
}

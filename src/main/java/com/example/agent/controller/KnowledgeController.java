package com.example.agent.controller;

import com.example.agent.dto.ApiResponse;
import com.example.agent.dto.KnowledgeAskRequest;
import com.example.agent.dto.KnowledgeUploadRequest;
import com.example.agent.knowledge.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 知识库接口。
 *
 * <p>对应需求文档 4.5 / 6.5：
 * <pre>
 * POST /agent/knowledge/upload    文档入库（切分 + 向量化）
 * POST /agent/knowledge/ask       提问（检索 + LLM 生成）
 * GET  /agent/knowledge/documents 列出已入库文档
 * </pre>
 */
@RestController
@RequestMapping("/agent/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /** 文档入库 */
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@Valid @RequestBody KnowledgeUploadRequest request) {
        KnowledgeService.Document doc = knowledgeService.addDocument(request.name(), request.content());
        return ApiResponse.ok(Map.of(
                "id", doc.id(),
                "name", doc.name(),
                "chunkCount", doc.chunkCount()
        ));
    }

    /** 提问：返回回答 + 来源片段 */
    @PostMapping("/ask")
    public ApiResponse<Map<String, Object>> ask(@Valid @RequestBody KnowledgeAskRequest request) {
        KnowledgeService.Answer result = knowledgeService.ask(request.question());
        List<Map<String, Object>> sources = result.hits().stream()
                .map(h -> {
                    Map<String,Object> map = new HashMap<>();

                    map.put("document", h.documentName());
                    map.put("text", h.text());
                    map.put("score", Math.round(h.score() * 1000) / 1000.0);

                    return map;
                })
                .collect(Collectors.toList());
        return ApiResponse.ok(Map.of(
                "answer", result.answer(),
                "sources", sources
        ));
    }

    /** 列出已入库文档 */
    @GetMapping("/documents")
    public ApiResponse<List<Map<String, Object>>> documents() {
        List<Map<String, Object>> docs = knowledgeService.listDocuments().stream()
                .map(d -> {

                    Map<String,Object> map=new HashMap<>();

                    map.put("id", d.id());
                    map.put("name", d.name());
                    map.put("chunkCount", d.chunkCount());

                    return map;

                })
                .collect(Collectors.toList());
        return ApiResponse.ok(docs);
    }
}

package com.example.aiinterview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评分标准知识库检索服务：从向量库中召回与用户提问/简历内容最相关的 Top-K 片段，
 * 并拼接为可供 System Prompt 直接使用的背景知识（Context）。
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    private final VectorStore vectorStore;

    public KnowledgeBaseService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 语义检索最相关的 topK 条评分标准片段。
     */
    public List<Document> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(topK).build());
        } catch (Exception e) {
            log.warn("向量库检索失败：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将命中的片段拼接为带编号、带模块名的纯文本上下文。
     */
    public String buildContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            Object module = doc.getMetadata().get("module");
            sb.append("【片段 ").append(i + 1).append("】");
            if (module != null) {
                sb.append("（模块：").append(module).append("）");
            }
            sb.append("\n").append(doc.getText()).append("\n\n");
        }
        return sb.toString().trim();
    }
}

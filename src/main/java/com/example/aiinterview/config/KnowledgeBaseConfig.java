package com.example.aiinterview.config;

import com.example.aiinterview.embedding.LocalHashEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 知识库（RAG）配置：
 * <ol>
 *     <li>注册一个本地轻量 EmbeddingModel，避免消耗外部 Embedding API 额度；</li>
 *     <li>在应用启动时读取 {@code knowledge/java_interview_rubric.md}，</li>
 *     <li>按标题（##）与段落切片后写入 SimpleVectorStore 内存向量库。</li>
 * </ol>
 */
@Slf4j
@Configuration
public class KnowledgeBaseConfig {

    private static final int EMBEDDING_DIMENSIONS = 384;
    private static final String SOURCE = "java_interview_rubric.md";

    @Bean
    public EmbeddingModel embeddingModel() {
        return new LocalHashEmbeddingModel(EMBEDDING_DIMENSIONS);
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel,
                                   @Value("classpath:knowledge/java_interview_rubric.md") Resource rubricResource) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        String content;
        try {
            content = rubricResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("读取评分标准知识库失败：{}", e.getMessage(), e);
            throw new IllegalStateException("读取评分标准知识库失败", e);
        }

        List<Document> documents = chunkMarkdown(content);
        vectorStore.add(documents);
        log.info("RAG 知识库初始化完成，共写入 {} 条评分标准片段", documents.size());
        return vectorStore;
    }

    /**
     * 以 “## ” 二级标题为模块边界切分，模块内部再按空行拆分为段落，
     * 使每个评分点都能作为独立片段被精确召回。
     */
    private List<Document> chunkMarkdown(String content) {
        List<Document> documents = new ArrayList<>();
        String[] sections = content.split("(?m)(?=^## )");
        for (String section : sections) {
            String trimmed = section.trim();
            // 跳过文档标题、前言等非评分标准内容
            if (trimmed.isEmpty() || !trimmed.startsWith("## ")) {
                continue;
            }
            int newline = trimmed.indexOf('\n');
            String module = newline < 0 ? trimmed.substring(3).trim() : trimmed.substring(3, newline).trim();
            String body = newline < 0 ? "" : trimmed.substring(newline + 1).trim();

            List<String> paragraphs = splitParagraphs(body);
            if (paragraphs.isEmpty()) {
                documents.add(new Document(trimmed, Map.of("module", module, "source", SOURCE)));
            } else {
                for (String paragraph : paragraphs) {
                    documents.add(new Document("## " + module + "\n" + paragraph,
                            Map.of("module", module, "source", SOURCE)));
                }
            }
        }
        return documents;
    }

    private List<String> splitParagraphs(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return Arrays.stream(body.split("\\R\\s*\\R"))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toList();
    }
}

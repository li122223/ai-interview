package com.example.aiinterview;

import com.example.aiinterview.service.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证：Spring 上下文可正常启动、知识库切片已入库、向量检索能召回相关片段，
 * 以及 Prompt 模板能正确替换 {currentDate} 与 {ragContext} 占位符。
 */
@SpringBootTest
class KnowledgeBaseIntegrationTest {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private PromptTemplateService promptTemplateService;

    @Test
    void knowledgeBaseIsPopulatedAndSearchable() {
        // 用带明显模块关键词的查询验证召回
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query("Redis 缓存雪崩 击穿 一致性 双写").topK(3).build());
        assertThat(hits).as("知识库检索结果不应为空").isNotEmpty();
        System.out.println("=== RAG 检索命中 ===");
        for (Document hit : hits) {
            String module = String.valueOf(hit.getMetadata().get("module"));
            String preview = hit.getText().replaceAll("\\s+", " ").substring(0, Math.min(50, hit.getText().length()));
            System.out.println("[" + module + "] " + preview);
        }
        assertThat(hits.stream().anyMatch(d -> String.valueOf(d.getMetadata().get("module")).contains("Redis")))
                .as("应召回到 Redis 模块片段").isTrue();
    }

    @Test
    void promptTemplateIsAssembledWithDateAndContext() {
        String prompt = promptTemplateService.buildSystemPrompt("【测试片段】Redis 缓存一致性");

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        assertThat(prompt).contains(today);
        assertThat(prompt).contains("【测试片段】Redis 缓存一致性");
        assertThat(prompt).doesNotContain("{currentDate}");
        assertThat(prompt).doesNotContain("{ragContext}");
        assertThat(prompt).contains("### 1. 基础环境与时间线校准");
        assertThat(prompt).contains("### 3. 输出格式与隐身规则");
    }
}


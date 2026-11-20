package com.example.aiinterview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Prompt 模板组装服务：
 * <ol>
 *     <li>启动时通过 {@code classpath:/prompts/resume_diagnosis.st} 加载简历诊断提示词模板；</li>
 *     <li>每次请求时动态替换 {@code {currentDate}}（服务器当前真实日期）与 {@code {ragContext}}（RAG 检索片段）。</li>
 * </ol>
 */
@Slf4j
@Service
public class PromptTemplateService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private final String template;

    public PromptTemplateService(@Value("classpath:/prompts/resume_diagnosis.st") Resource templateResource) {
        this.template = loadTemplate(templateResource);
    }

    private String loadTemplate(Resource resource) {
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            if (!content.contains("{currentDate}") || !content.contains("{ragContext}")) {
                throw new IllegalStateException("Prompt 模板缺少 {currentDate} 或 {ragContext} 占位符");
            }
            return content;
        } catch (Exception e) {
            log.error("加载 Prompt 模板失败：{}", e.getMessage(), e);
            throw new IllegalStateException("加载 Prompt 模板失败", e);
        }
    }

    /**
     * 组装系统提示词：以服务器当前真实日期替换 {currentDate}，以 RAG 检索文本替换 {ragContext}。
     */
    public String buildSystemPrompt(String ragContext) {
        String currentDate = LocalDate.now().format(DATE_FORMATTER);
        String context = (ragContext == null || ragContext.isBlank()) ? "（未检索到相关评分标准片段）" : ragContext;
        return template
                .replace("{currentDate}", currentDate)
                .replace("{ragContext}", context);
    }
}

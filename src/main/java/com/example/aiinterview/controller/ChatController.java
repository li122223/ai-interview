package com.example.aiinterview.controller;

import com.example.aiinterview.dto.ChatRequest;
import com.example.aiinterview.service.KnowledgeBaseService;
import com.example.aiinterview.service.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@RestController
public class ChatController {

    /** 简历诊断使用的模型与采样温度（低温度保证诊断报告严谨稳定）。 */
    private static final String MODEL = "deepseek-chat";
    private static final double TEMPERATURE = 0.2;

    private final ChatClient chatClient;
    private final KnowledgeBaseService knowledgeBaseService;
    private final PromptTemplateService promptTemplateService;

    public ChatController(ChatClient.Builder builder,
                          KnowledgeBaseService knowledgeBaseService,
                          PromptTemplateService promptTemplateService) {
        this.chatClient = builder.build();
        this.knowledgeBaseService = knowledgeBaseService;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 带 RAG 增强的流式聊天接口，使用 SSE（Server-Sent Events）实现打字机输出。
     * 先从向量库检索 Top-3 评分标准片段拼接到 System Prompt，再交由模型回答。
     * 请求体（JSON）：{ "message": "请诊断我的简历" }
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody(required = false) ChatRequest request) {
        String message = request == null || request.message() == null || request.message().isBlank()
                ? "你好, 请用一句话介绍你自己" : request.message();
        log.info("收到流式聊天请求，长度: {}", message.length());

        List<Document> hits = knowledgeBaseService.search(message, 3);
        String ragContext = knowledgeBaseService.buildContext(hits);
        log.info("RAG 检索命中 {} 条评分标准片段", hits.size());

        String systemPrompt = promptTemplateService.buildSystemPrompt(ragContext);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(MODEL)
                .temperature(TEMPERATURE)
                .build();
        return chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .options(options)
                .stream()
                .content()
                .concatWithValues("[DONE]")
                .onErrorResume(e -> Flux.just("[调用失败] " + e.getMessage(), "[DONE]"));
    }
}

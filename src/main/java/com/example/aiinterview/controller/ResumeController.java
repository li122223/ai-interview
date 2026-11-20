package com.example.aiinterview.controller;

import com.example.aiinterview.service.ResumeParseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PDF 简历上传与解析接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeParseService resumeParseService;

    public ResumeController(ResumeParseService resumeParseService) {
        this.resumeParseService = resumeParseService;
    }

    /**
     * 接收前端上传的 PDF 简历，返回解析出的纯文本。
     * 返回 JSON：{ "success": true, "filename": "...", "text": "..." }
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String text = resumeParseService.extractText(file);
            result.put("success", true);
            result.put("filename", file.getOriginalFilename() == null ? "resume.pdf" : file.getOriginalFilename());
            result.put("text", text);
        } catch (Exception e) {
            log.warn("简历上传解析失败：{}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}

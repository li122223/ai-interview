package com.example.aiinterview.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * PDF 简历解析服务：使用 PDFBox 提取 PDF 中的纯文本，并做基础清洗。
 */
@Slf4j
@Service
public class ResumeParseService {

    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("仅支持 PDF 格式的简历文件");
        }

        try (InputStream in = file.getInputStream();
             PDDocument document = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String raw = stripper.getText(document);
            return clean(raw);
        } catch (Exception e) {
            log.error("PDF 简历解析失败", e);
            throw new RuntimeException("PDF 解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 清洗：去除 NUL 字符、统一换行、压缩多余空格与空行。
     */
    private String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" ?\n ?", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}

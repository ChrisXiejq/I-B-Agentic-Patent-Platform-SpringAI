package com.inovationbehavior.backend.controller;

import com.inovationbehavior.backend.model.Result;
import com.inovationbehavior.backend.rag.RagIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * RAG 向量索引构建：从 PDF 目录批量建索引到 PgVector。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/rag")
public class RagIndexController {

    private final RagIndexService ragIndexService;

    /** 默认 PDF 目录（可配置），例如与 LLM base 的 patent_pdfs 对齐 */
    @Value("${app.rag.pdf-dir:}")
    private String defaultPdfDir;

    /**
     * 从指定目录加载 PDF 并写入向量库。
     * POST /agent/rag/build?pdfDir=/path/to/pdfs
     * 若不传 pdfDir 则使用配置 app.rag.pdf-dir 或返回提示。
     */
    @PostMapping("/build")
    public Result buildIndex(@RequestParam(value = "pdfDir", required = false) String pdfDir) {
        String dir = pdfDir != null && !pdfDir.isBlank() ? pdfDir : defaultPdfDir;
        if (dir == null || dir.isBlank()) {
            return Result.error("请提供 pdfDir 参数或配置 app.rag.pdf-dir");
        }
        try {
            int count = ragIndexService.buildIndexFromPdfDirectory(dir);
            return Result.success("已写入 " + count + " 个文档块");
        } catch (Exception e) {
            log.warn("RAG 建索引失败 pdfDir={}", dir, e);
            return Result.error("建索引失败: " + e.getMessage());
        }
    }
}

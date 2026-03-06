package com.inovationbehavior.backend.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 从 PDF 目录构建 RAG 向量索引 + BM25 索引，支持多路召回。
 * 对齐原 LLM base build_vector_db：按页加载 PDF，按块切分后写入向量库与 BM25 存储。
 */
@Service
public class RagIndexService {

    public static final int CHUNK_SIZE = 500;
    public static final int CHUNK_OVERLAP = 50;

    private final VectorStore vectorStore;
    private final BM25DocumentStore bm25DocumentStore;

    public RagIndexService(VectorStore vectorStore, BM25DocumentStore bm25DocumentStore) {
        this.vectorStore = vectorStore;
        this.bm25DocumentStore = bm25DocumentStore;
    }

    /**
     * 从指定目录加载所有 PDF，切分后写入向量库。
     *
     * @param pdfDirPath PDF 目录绝对路径或相对路径
     * @return 写入的文档块数量
     */
    public int buildIndexFromPdfDirectory(String pdfDirPath) {
        Path dir = Path.of(pdfDirPath).toAbsolutePath();
        Assert.isTrue(Files.isDirectory(dir), "PDF 目录不存在或不是目录: " + pdfDirPath);

        List<Document> allDocs = new ArrayList<>();
        try (Stream<Path> list = Files.list(dir)) {
            List<Path> pdfs = list
                    .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .toList();
            for (Path pdfPath : pdfs) {
                Resource resource = new FileSystemResource(pdfPath.toFile());
                PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
                List<Document> docs = reader.get();
                allDocs.addAll(docs);
            }
        } catch (Exception e) {
            throw new RuntimeException("读取 PDF 目录失败: " + pdfDirPath, e);
        }

        if (allDocs.isEmpty()) {
            return 0;
        }

        List<Document> chunks = splitDocuments(allDocs, CHUNK_SIZE, CHUNK_OVERLAP);
        vectorStore.add(chunks);
        // 使用 Weaviate 时其 hybrid 已含 BM25，无需再写内存 BM25 索引
        if (!(vectorStore instanceof WeaviateVectorStore)) {
            try {
                bm25DocumentStore.clear();
                bm25DocumentStore.addDocuments(chunks);
            } catch (Exception e) {
                throw new RuntimeException("写入 BM25 索引失败", e);
            }
        }
        return chunks.size();
    }

    /**
     * 简单按字符数切分，带重叠。与 Python 端 RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=50) 行为接近。
     */
    public static List<Document> splitDocuments(List<Document> documents, int chunkSize, int chunkOverlap) {
        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            String content = doc.getText();
            if (content == null || content.isBlank()) {
                continue;
            }
            List<String> segments = splitText(content, chunkSize, chunkOverlap);
            for (int i = 0; i < segments.size(); i++) {
                java.util.Map<String, Object> meta = new java.util.HashMap<>(doc.getMetadata());
                meta.put("chunk_index", i);
                meta.put("total_chunks", segments.size());
                result.add(new Document(segments.get(i), meta));
            }
        }
        return result;
    }

    private static List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = end - overlap;
            if (start <= 0) {
                start = end;
            }
        }
        return chunks;
    }
}

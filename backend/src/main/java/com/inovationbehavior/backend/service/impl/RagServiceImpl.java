package com.inovationbehavior.backend.service.impl;

import com.inovationbehavior.backend.rag.BM25DocumentStore;
import com.inovationbehavior.backend.rag.RRFusion;
import com.inovationbehavior.backend.rag.RagReranker;
import com.inovationbehavior.backend.rag.WeaviateHybridSearch;
import com.inovationbehavior.backend.service.intf.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RAG：优先使用 Weaviate hybrid（向量+BM25 一体）；否则向量 + 内存 BM25 多路召回 + RRF 融合，可选 Rerank。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final Optional<WeaviateHybridSearch> weaviateHybridSearch;
    private final BM25DocumentStore bm25DocumentStore;
    private final RagReranker ragReranker;

    @Value("${app.rag.top-k:5}")
    private int topK;
    @Value("${app.rag.recall-per-path:10}")
    private int recallPerPath;
    @Value("${app.rag.rrf-k:60}")
    private int rrfK;
    @Value("${app.rag.rerank-enabled:false}")
    private boolean rerankEnabled;
    @Value("${app.rag.hybrid-alpha:0.5}")
    private float hybridAlpha;

    @Override
    public String getRagAnswer(String userQuery, String context) {
        String fullQuery = (userQuery != null ? userQuery : "").trim();
        if (context != null && !context.isEmpty()) {
            fullQuery = fullQuery + " 专利号或上下文: " + context;
        }
        if (fullQuery.isEmpty()) {
            fullQuery = "专利相关信息、技术要点、应用场景";
        }

        List<Document> merged;
        if (weaviateHybridSearch.isPresent()) {
            merged = weaviateHybridSearch.get().hybridSearch(fullQuery, topK, hybridAlpha);
        } else {
            List<Document> vectorDocs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(fullQuery).topK(recallPerPath).build()
            );
            List<BM25DocumentStore.ScoredDocument> bm25Docs = bm25DocumentStore.search(fullQuery, recallPerPath);
            merged = RRFusion.merge(vectorDocs, bm25Docs, topK, rrfK);
        }
        if (rerankEnabled && !merged.isEmpty()) {
            merged = ragReranker.rerank(fullQuery, merged, topK);
        }

        String contextBlock;
        if (merged.isEmpty()) {
            contextBlock = "（暂无检索到相关参考内容，请基于常识简要回答。）";
            log.debug("RAG 向量库与 BM25 均未命中，仅用 LLM 回答");
        } else {
            contextBlock = merged.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));
        }

        String promptText = """
                基于以下参考内容回答问题。如果参考内容中没有相关信息，请基于常识回答。

                参考内容：
                %s

                问题：%s

                请给出简洁准确的回答："""
                .formatted(contextBlock, fullQuery);

        try {
            return chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("RAG 调用 LLM 失败", e);
            return "RAG 回答生成失败: " + e.getMessage();
        }
    }
}

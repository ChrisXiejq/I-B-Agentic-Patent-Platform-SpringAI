package com.inovationbehavior.backend.evaluation;

import com.inovationbehavior.backend.rag.BM25DocumentStore;
import com.inovationbehavior.backend.rag.RRFusion;
import com.inovationbehavior.backend.service.intf.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 能力评估：Precision@K、Recall@K、MRR，支撑召回与排序策略迭代。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvaluationService {

    private final VectorStore vectorStore;
    private final BM25DocumentStore bm25DocumentStore;
    private final RagService ragService;

    /**
     * 单条样本：查询 + 相关文档内容集合（用于匹配召回结果）
     */
    public record RagSample(String query, Set<String> relevantContents) {}

    /**
     * 评估结果
     */
    public record RagMetrics(double precisionAtK, double recallAtK, double mrr, int k) {}

    /**
     * 在给定样本集上评估向量+RRF 召回的 P@K、R@K、MRR（K 取 5）。
     */
    public RagMetrics evaluate(List<RagSample> samples, int k) {
        if (samples == null || samples.isEmpty()) {
            return new RagMetrics(0, 0, 0, k);
        }
        double sumP = 0, sumR = 0, sumMRR = 0;
        for (RagSample sample : samples) {
            List<Document> vectorDocs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(sample.query()).topK(k * 2).build()
            );
            List<BM25DocumentStore.ScoredDocument> bm25Docs = bm25DocumentStore.search(sample.query(), k * 2);
            List<Document> merged = RRFusion.merge(vectorDocs, bm25Docs, k);
            Set<String> relevant = sample.relevantContents();
            if (relevant.isEmpty()) continue;
            Set<String> retrievedContents = merged.stream()
                    .map(Document::getText)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            long hits = retrievedContents.stream().filter(relevant::contains).count();
            sumP += (double) hits / Math.min(k, merged.size());
            sumR += relevant.isEmpty() ? 0 : (double) hits / relevant.size();
            int rank = 1;
            for (Document d : merged) {
                if (d.getText() != null && relevant.contains(d.getText())) {
                    sumMRR += 1.0 / rank;
                    break;
                }
                rank++;
            }
        }
        int n = samples.size();
        return new RagMetrics(sumP / n, sumR / n, sumMRR / n, k);
    }

    public RagMetrics evaluate(List<RagSample> samples) {
        return evaluate(samples, 5);
    }
}

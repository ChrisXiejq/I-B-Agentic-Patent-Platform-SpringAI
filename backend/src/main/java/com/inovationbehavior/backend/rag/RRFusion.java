package com.inovationbehavior.backend.rag;

import org.springframework.ai.document.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reciprocal Rank Fusion (RRF) 融合多路召回结果。
 * RRF(d) = sum_i 1/(k + rank_i(d))，k 默认 60。
 */
public final class RRFusion {

    public static final int DEFAULT_K = 60;

    /**
     * 将向量检索结果与 BM25 结果按 RRF 融合，去重后按 RRF 分排序，返回前 topK 个 Document。
     *
     * @param vectorDocs 向量检索结果（按相关度从高到低）
     * @param bm25Scored  BM25 检索结果（已按分数从高到低）
     * @param topK       最终返回数量
     * @param k          RRF 常数
     */
    public static List<Document> merge(List<Document> vectorDocs, List<BM25DocumentStore.ScoredDocument> bm25Scored, int topK, int k) {
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, Document> byContent = new HashMap<>();

        for (int i = 0; i < vectorDocs.size(); i++) {
            Document d = vectorDocs.get(i);
            String content = d.getText();
            if (content == null) continue;
            String key = content;
            rrfScores.merge(key, 1.0 / (k + i + 1), Double::sum);
            byContent.putIfAbsent(key, d);
        }
        for (int i = 0; i < bm25Scored.size(); i++) {
            Document d = bm25Scored.get(i).document();
            String content = d.getText();
            if (content == null) continue;
            String key = content;
            rrfScores.merge(key, 1.0 / (k + i + 1), Double::sum);
            byContent.putIfAbsent(key, d);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> byContent.get(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<Document> merge(List<Document> vectorDocs, List<BM25DocumentStore.ScoredDocument> bm25Scored, int topK) {
        return merge(vectorDocs, bm25Scored, topK, DEFAULT_K);
    }

    private RRFusion() {}
}

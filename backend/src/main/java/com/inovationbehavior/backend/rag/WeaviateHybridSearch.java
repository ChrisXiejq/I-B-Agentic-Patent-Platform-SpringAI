package com.inovationbehavior.backend.rag;

import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.argument.HybridArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 使用 Weaviate 原生 hybrid 检索（向量 + BM25 融合），一次请求完成多路召回，无需内存 BM25 索引。
 * 仅当 VectorStore 为 WeaviateVectorStore 时注册。
 */
@Slf4j
@Service
@ConditionalOnBean(WeaviateVectorStore.class)
public class WeaviateHybridSearch {

    /** Spring AI 默认写入的 Weaviate 类名 */
    public static final String DEFAULT_OBJECT_CLASS = "SpringAiWeaviate";
    /** 文档正文属性名（与 Spring AI Weaviate 写入一致） */
    public static final String CONTENT_PROPERTY = "content";

    private final WeaviateVectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final String objectClass;

    public WeaviateHybridSearch(WeaviateVectorStore vectorStore, EmbeddingModel embeddingModel) {
        this(vectorStore, embeddingModel, DEFAULT_OBJECT_CLASS);
    }

    public WeaviateHybridSearch(WeaviateVectorStore vectorStore, EmbeddingModel embeddingModel, String objectClass) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.objectClass = objectClass != null ? objectClass : DEFAULT_OBJECT_CLASS;
    }

    /**
     * Hybrid 检索：同一 query 同时做向量检索与 BM25 关键词检索，Weaviate 内融合后返回。
     *
     * @param query 查询文本
     * @param topK  返回条数
     * @param alpha 向量与关键词权重，0.5 为均等，>0.5 偏向量，<0.5 偏 BM25
     * @return 融合后的文档列表
     */
    public List<Document> hybridSearch(String query, int topK, float alpha) {
        Optional<WeaviateClient> clientOpt = vectorStore.getNativeClient();
        if (clientOpt.isEmpty()) {
            log.warn("WeaviateVectorStore 未暴露 native client，回退为纯向量检索");
            return vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder().query(query).topK(topK).build()
            );
        }

        WeaviateClient client = clientOpt.get();
        float[] queryVector;
        try {
            List<float[]> embedded = embeddingModel.embed(List.of(query));
            queryVector = (embedded != null && !embedded.isEmpty()) ? embedded.get(0) : new float[0];
        } catch (Exception e) {
            log.warn("Hybrid 检索嵌入 query 失败，仅用 BM25 侧", e);
            queryVector = new float[0];
        }

        HybridArgument hybrid = HybridArgument.builder()
                .query(query)
                .alpha(alpha)
                .vector(queryVector)
                .build();

        Result<GraphQLResponse> result = client.graphQL().get()
                .withClassName(objectClass)
                .withHybrid(hybrid)
                .withLimit(topK)
                .withFields(Field.builder().name(CONTENT_PROPERTY).build())
                .run();
        if (result.hasErrors()) {
            log.warn("Weaviate hybrid 查询失败: {}", result.getError());
            return vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder().query(query).topK(topK).build()
            );
        }

        return parseGetResponse(result.getResult(), objectClass, CONTENT_PROPERTY);
    }

    @SuppressWarnings("unchecked")
    private List<Document> parseGetResponse(GraphQLResponse response, String className, String contentProp) {
        List<Document> out = new ArrayList<>();
        if (response == null || response.getData() == null) return out;
        Object data = response.getData();
        if (!(data instanceof Map)) return out;
        Object getData = ((Map<?, ?>) data).get("Get");
        if (!(getData instanceof Map)) return out;
        Object list = ((Map<?, ?>) getData).get(className);
        if (!(list instanceof List)) return out;
        for (Object item : (List<?>) list) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> map = (Map<String, Object>) item;
            Object content = map.get(contentProp);
            String text = content != null ? content.toString() : "";
            out.add(new Document(text, Map.of("source", "weaviate_hybrid")));
        }
        return out;
    }
}

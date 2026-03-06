package com.inovationbehavior.backend.memory;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 长期记忆：将用户对话摘要向量化存入 VectorStore，按 userId 检索，供分层记忆 API 使用。
 */
@Component
public class LongTermMemoryStore {

    public static final String METADATA_USER_ID = "user_id";
    public static final String METADATA_SOURCE = "memory";
    public static final int LONG_TERM_TOP_K = 5;

    private final VectorStore vectorStore;

    public LongTermMemoryStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 存入一条长期记忆（如多轮对话摘要或业务关键信息）
     */
    public void add(String userId, String content) {
        if (userId == null || content == null || content.isBlank()) return;
        Document doc = new Document(content, Map.of(
                METADATA_USER_ID, userId,
                METADATA_SOURCE, "long_term"
        ));
        vectorStore.add(List.of(doc));
    }

    /**
     * 按用户与查询检索长期记忆，用于拼入 Agent 上下文
     */
    public List<String> searchByUser(String userId, String query, int topK) {
        if (userId == null || userId.isBlank()) return List.of();
        String fullQuery = (query != null ? query : "") + " " + userId;
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(fullQuery).topK(topK).build()
        );
        return docs.stream()
                .filter(d -> userId.equals(d.getMetadata().get(METADATA_USER_ID)))
                .map(Document::getText)
                .collect(Collectors.toList());
    }
}

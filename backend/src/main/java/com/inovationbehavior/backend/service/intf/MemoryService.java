package com.inovationbehavior.backend.service.intf;

import java.util.Optional;

/**
 * 分层记忆对外 API：短期缓存 + 长期向量化。
 * Agent 在多轮对话中通过本接口获取历史上下文，提升响应一致性。
 */
public interface MemoryService {

    /**
     * 获取用户近期对话上下文（短期缓存 + 可选长期向量检索），用于拼入 prompt。
     */
    Optional<String> getRecentContext(String userId);

    /**
     * 可选：传入当前查询时，长期记忆按相关性检索；否则按用户最近记忆检索。
     */
    default Optional<String> getRecentContext(String userId, String currentQuery) {
        return getRecentContext(userId);
    }

    /**
     * 追加一轮对话（user 或 assistant），用于更新短期缓存与长期归档。
     */
    void appendTurn(String userId, String role, String content);

    /**
     * 将摘要或关键信息归档到长期向量记忆（供定时或 N 轮后调用）。
     */
    void archiveToLongTerm(String userId, String summaryContent);
}

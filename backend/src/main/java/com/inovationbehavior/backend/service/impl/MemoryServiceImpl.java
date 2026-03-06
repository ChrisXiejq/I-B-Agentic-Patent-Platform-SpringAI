package com.inovationbehavior.backend.service.impl;

import com.inovationbehavior.backend.memory.LongTermMemoryStore;
import com.inovationbehavior.backend.service.intf.MemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分层记忆实现：短期缓存（近期 N 轮）+ 长期向量化（VectorStore）。
 * 对外提供标准化 API，Agent 多轮对话可利用历史上下文。
 */
@Slf4j
@Service
public class MemoryServiceImpl implements MemoryService {

    @Value("${app.agent.memory.short-term-turns:10}")
    private int shortTermTurns;

    @Value("${app.agent.memory.long-term-top-k:5}")
    private int longTermTopK;

    private final Map<String, List<Turn>> shortTerm = new ConcurrentHashMap<>();
    private final LongTermMemoryStore longTermMemoryStore;

    public MemoryServiceImpl(LongTermMemoryStore longTermMemoryStore) {
        this.longTermMemoryStore = longTermMemoryStore;
    }

    @Override
    public Optional<String> getRecentContext(String userId) {
        return getRecentContext(userId, null);
    }

    @Override
    public Optional<String> getRecentContext(String userId, String currentQuery) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        StringBuilder sb = new StringBuilder();
        List<Turn> turns = shortTerm.get(userId);
        if (turns != null && !turns.isEmpty()) {
            int from = Math.max(0, turns.size() - shortTermTurns);
            for (int i = from; i < turns.size(); i++) {
                Turn t = turns.get(i);
                sb.append(t.role).append(": ").append(truncate(t.content, 500)).append("\n");
            }
        }
        List<String> longTerm = longTermMemoryStore.searchByUser(userId, currentQuery != null ? currentQuery : "", longTermTopK);
        if (!longTerm.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("[长期记忆]\n");
            longTerm.forEach(s -> sb.append(truncate(s, 300)).append("\n"));
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? Optional.empty() : Optional.of(out);
    }

    @Override
    public void appendTurn(String userId, String role, String content) {
        if (userId == null || userId.isBlank()) return;
        shortTerm.computeIfAbsent(userId, k -> new ArrayList<>()).add(new Turn(role, content));
        List<Turn> list = shortTerm.get(userId);
        while (list.size() > shortTermTurns * 2) {
            list.remove(0);
        }
    }

    @Override
    public void archiveToLongTerm(String userId, String summaryContent) {
        if (userId == null || summaryContent == null || summaryContent.isBlank()) return;
        longTermMemoryStore.add(userId, summaryContent);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private record Turn(String role, String content) {}
}

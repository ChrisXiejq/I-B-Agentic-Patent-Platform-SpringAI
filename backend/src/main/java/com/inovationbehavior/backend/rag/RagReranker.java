package com.inovationbehavior.backend.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG 召回结果 Rerank：使用 LLM 对片段与查询的相关性排序，取 topK。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagReranker {

    private static final Pattern INDEX_PATTERN = Pattern.compile("(?:^|[,，\\s]+)(\\d+)");

    private final ChatModel chatModel;

    /**
     * 使用 LLM 对 docs 按与 query 的相关性重排，返回前 topK 个。
     */
    public List<Document> rerank(String query, List<Document> docs, int topK) {
        if (docs == null || docs.isEmpty()) return List.of();
        if (docs.size() <= topK) return docs;

        StringBuilder passages = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            String content = docs.get(i).getText();
            if (content != null) {
                passages.append("[").append(i).append("] ").append(truncate(content, 300)).append("\n");
            }
        }
        String prompt = """
                问题：%s

                以下为编号后的参考片段，请仅输出与问题最相关的 %d 个片段的编号（从0开始），按相关度从高到低排列，编号之间用逗号分隔，不要其他文字。
                片段：
                %s
                请只输出编号序列，例如：2,0,5
                """.formatted(query, Math.min(topK, docs.size()), passages);

        try {
            String out = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
            List<Integer> indices = parseIndices(out, docs.size());
            List<Document> result = new ArrayList<>();
            for (int idx : indices) {
                if (result.size() >= topK) break;
                if (idx >= 0 && idx < docs.size()) result.add(docs.get(idx));
            }
            return result.isEmpty() ? docs.subList(0, Math.min(topK, docs.size())) : result;
        } catch (Exception e) {
            log.warn("Rerank 失败，返回原顺序", e);
            return docs.subList(0, Math.min(topK, docs.size()));
        }
    }

    private static List<Integer> parseIndices(String out, int maxIndex) {
        List<Integer> list = new ArrayList<>();
        Matcher m = INDEX_PATTERN.matcher(out != null ? out : "");
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 0 && n < maxIndex && !list.contains(n)) list.add(n);
        }
        return list;
    }

    private static String truncate(String s, int len) {
        if (s == null || s.length() <= len) return s != null ? s : "";
        return s.substring(0, len) + "...";
    }
}

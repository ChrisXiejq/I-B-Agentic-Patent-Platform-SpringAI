package com.inovationbehavior.backend.ai.graph.nodes;

import com.inovationbehavior.backend.ai.app.IBApp;
import com.inovationbehavior.backend.ai.graph.PatentGraphState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 检索专家节点：查专利详情、热度、知识库，并追加结果到 stepResults，步数+1。
 */
@Component
@Slf4j
public class RetrievalExpertNode {

    private final IBApp ibApp;

    public RetrievalExpertNode(@Lazy IBApp ibApp) {
        this.ibApp = ibApp;
    }

    private static final String LOG_PREFIX = "[AgentGraph.RetrievalExpert] ";

    public Map<String, Object> apply(PatentGraphState state) {
        String message = state.userMessage().orElse("");
        String chatId = state.chatId().orElse("default");
        log.info("{}>>> 进入节点 | chatId={} 用户问题={} (将调用 RAG + 专利/热度/记忆 等工具)",
                LOG_PREFIX, chatId, abbreviate(message, 80));
        long start = System.currentTimeMillis();
        String out = ibApp.doExpertChat(message, chatId, ibApp.getRetrievalExpertPrompt());
        long elapsed = System.currentTimeMillis() - start;
        int nextCount = state.stepCount() + 1;
        log.info("{}<<< 离开节点 | 输出长度={} 输出预览={} stepCount->{} elapsedMs={}",
                LOG_PREFIX, out != null ? out.length() : 0, abbreviate(out, 200), nextCount, elapsed);
        return Map.of(
                PatentGraphState.STEP_RESULTS, "[Retrieval]\n" + out,
                PatentGraphState.STEP_COUNT, nextCount
        );
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "null";
        s = s.trim();
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}

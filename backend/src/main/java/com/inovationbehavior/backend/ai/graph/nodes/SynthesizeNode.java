package com.inovationbehavior.backend.ai.graph.nodes;

import com.inovationbehavior.backend.ai.app.IBApp;
import com.inovationbehavior.backend.ai.graph.PatentGraphState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 综合节点：根据 stepResults 与用户问题生成最终回复，写入 finalAnswer。
 */
@Component
@Slf4j
public class SynthesizeNode {

    private final IBApp ibApp;

    public SynthesizeNode(@Lazy IBApp ibApp) {
        this.ibApp = ibApp;
    }

    private static final String LOG_PREFIX = "[AgentGraph.Synthesize] ";

    public Map<String, Object> apply(PatentGraphState state) {
        String userMessage = state.userMessage().orElse("");
        String chatId = state.chatId().orElse("default");
        var stepResults = state.stepResults();
        log.info("{}>>> 进入节点 | chatId={} stepResultsCount={} 用户问题={} (将汇总专家输出生成最终回复)",
                LOG_PREFIX, chatId, stepResults.size(), abbreviate(userMessage, 80));
        long start = System.currentTimeMillis();
        String answer = ibApp.synthesizeAnswer(userMessage, stepResults, chatId);
        long elapsed = System.currentTimeMillis() - start;
        log.info("{}<<< 离开节点 | finalAnswer长度={} 预览={} elapsedMs={}",
                LOG_PREFIX, answer != null ? answer.length() : 0, abbreviate(answer, 200), elapsed);
        return Map.of(PatentGraphState.FINAL_ANSWER, answer != null ? answer : "");
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "null";
        s = s.trim();
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}

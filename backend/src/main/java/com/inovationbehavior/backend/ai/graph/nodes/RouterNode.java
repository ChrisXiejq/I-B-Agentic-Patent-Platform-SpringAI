package com.inovationbehavior.backend.ai.graph.nodes;

import com.inovationbehavior.backend.ai.app.IBApp;
import com.inovationbehavior.backend.ai.graph.PatentGraphState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 路由节点：根据用户消息与当前步数决定下一节点（retrieval | analysis | advice | synthesize | end）。
 */
@Component
@Slf4j
public class RouterNode {

    private final IBApp ibApp;

    public RouterNode(@Lazy IBApp ibApp) {
        this.ibApp = ibApp;
    }

    private static final String LOG_PREFIX = "[AgentGraph.Router] ";

    public Map<String, Object> apply(PatentGraphState state) {
        String userMessage = state.userMessage().orElse("");
        String chatId = state.chatId().orElse("default");
        int stepCount = state.stepCount();
        int maxSteps = state.maxSteps();
        var stepResults = state.stepResults();

        log.info("{}>>> 进入节点 | chatId={} stepCount={}/{} stepResultsSize={} userMessage={}",
                LOG_PREFIX, chatId, stepCount, maxSteps, stepResults.size(), abbreviate(userMessage, 80));
        String next = ibApp.classifyIntentForGraph(userMessage, stepCount, maxSteps, stepResults);
        log.info("{}<<< 离开节点 | 意图分类结果 nextNode={} (将跳转到: {})",
                LOG_PREFIX, next, describeNext(next));
        return Map.of(PatentGraphState.NEXT_NODE, next);
    }

    private static String describeNext(String next) {
        return switch (next) {
            case "retrieval" -> "检索专家";
            case "analysis" -> "分析专家";
            case "advice" -> "建议专家";
            case "synthesize" -> "综合节点";
            case "end" -> "综合节点(告别)";
            default -> next;
        };
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "null";
        s = s.trim();
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}

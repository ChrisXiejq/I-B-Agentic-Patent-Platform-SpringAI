package com.inovationbehavior.backend.ai.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

import static java.util.Map.entry;

/**
 * 执行专利多 Agent 图：注入初始状态，运行图，返回最终回复。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PatentGraphRunner {

    private static final String LOG_PREFIX = "[AgentGraph] ";

    private final CompiledGraph<PatentGraphState> patentAgentGraph;

    @Value("${app.agent.graph.max-steps:5}")
    private int maxSteps;

    /**
     * 同步执行图，返回最终回复文本。
     */
    public String run(String userMessage, String chatId) {
        String cid = chatId != null ? chatId : "default";
        log.info("{}======== 图执行开始 ======== chatId={} userMessage(length={}) preview={}",
                LOG_PREFIX, cid, userMessage != null ? userMessage.length() : 0, abbreviate(userMessage, 120));
        // 必须提供 schema 中所有 key 的非 null 初始值，否则 LangGraph4j 合并状态时会 NPE（含 P&E 字段）
        Map<String, Object> initialState = Map.ofEntries(
                entry(PatentGraphState.USER_MESSAGE, userMessage != null ? userMessage : ""),
                entry(PatentGraphState.CHAT_ID, cid),
                entry(PatentGraphState.STEP_RESULTS, new ArrayList<String>()),
                entry(PatentGraphState.NEXT_NODE, ""),
                entry(PatentGraphState.FINAL_ANSWER, ""),
                entry(PatentGraphState.STEP_COUNT, 0),
                entry(PatentGraphState.MAX_STEPS, maxSteps),
                entry(PatentGraphState.PLAN, new ArrayList<String>()),
                entry(PatentGraphState.CURRENT_STEP_INDEX, 0),
                entry(PatentGraphState.NEED_REPLAN, 0),
                entry(PatentGraphState.ENVIRONMENT_CHANGED, 0)
        );
        RunnableConfig config = RunnableConfig.builder().threadId(cid).build();
        try {
            var finalState = patentAgentGraph.invoke(initialState, config);
            String answer = finalState
                    .map(s -> s.finalAnswer().orElse(""))
                    .orElse("Sorry, the request could not be completed. Please try again.");
            int stepCount = finalState.map(PatentGraphState::stepCount).orElse(0);
            int planSize = finalState.map(s -> s.plan().size()).orElse(0);
            int stepResultsSize = finalState.map(s -> s.stepResults().size()).orElse(0);
            log.info("{}======== 图执行结束 ======== chatId={} stepCount={} planSize={} stepResultsSize={} finalAnswer(length={}) preview={}",
                    LOG_PREFIX, cid, stepCount, planSize, stepResultsSize, answer != null ? answer.length() : 0, abbreviate(answer, 150));
            return answer;
        } catch (Exception e) {
            log.error("{}图执行异常 chatId={} error={}", LOG_PREFIX, cid, e.getMessage(), e);
            // 外部 API 瞬时错误（503/429 等）包装后抛出，便于测试/调用方识别并重试
            if (isTransientApiFailure(e)) {
                throw new RuntimeException("Transient API failure (e.g. 503), retry later: " + e.getMessage(), e);
            }
            return "Sorry, the request could not be completed. Please try again.";
        }
    }

    private static boolean isTransientApiFailure(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause()) {
            String msg = x.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("503") || m.contains("429") || m.contains("high demand")
                        || m.contains("rate limit") || m.contains("try again later")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "null";
        s = s.trim();
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}

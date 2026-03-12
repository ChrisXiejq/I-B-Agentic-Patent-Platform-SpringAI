package com.inovationbehavior.backend.ai.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

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
        // 必须提供 schema 中所有 key 的非 null 初始值，否则 LangGraph4j 合并状态时会 NPE
        Map<String, Object> initialState = Map.of(
                PatentGraphState.USER_MESSAGE, userMessage != null ? userMessage : "",
                PatentGraphState.CHAT_ID, cid,
                PatentGraphState.STEP_RESULTS, new ArrayList<String>(),
                PatentGraphState.NEXT_NODE, "",
                PatentGraphState.FINAL_ANSWER, "",
                PatentGraphState.STEP_COUNT, 0,
                PatentGraphState.MAX_STEPS, maxSteps
        );
        RunnableConfig config = RunnableConfig.builder().threadId(cid).build();
        try {
            var finalState = patentAgentGraph.invoke(initialState, config);
            String answer = finalState
                    .map(s -> s.finalAnswer().orElse(""))
                    .orElse("Sorry, the request could not be completed. Please try again.");
            int stepCount = finalState.map(PatentGraphState::stepCount).orElse(0);
            log.info("{}======== 图执行结束 ======== chatId={} stepCount={} finalAnswer(length={}) preview={}",
                    LOG_PREFIX, cid, stepCount, answer != null ? answer.length() : 0, abbreviate(answer, 150));
            return answer;
        } catch (Exception e) {
            log.error("{}图执行异常 chatId={}", LOG_PREFIX, cid, e);
            return "Sorry, the request could not be completed. Please try again.";
        }
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "null";
        s = s.trim();
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}

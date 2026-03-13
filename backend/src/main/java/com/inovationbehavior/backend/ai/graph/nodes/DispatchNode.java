package com.inovationbehavior.backend.ai.graph.nodes;

import com.inovationbehavior.backend.ai.graph.PatentGraphState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * P&E 派发节点：根据 plan 与 currentStepIndex 决定下一节点。
 * 子任务为 retrieval/analysis/advice 时派发到统一 executor（ReAct 循环）；synthesize 时到 synthesize。
 */
@Component
@Slf4j
public class DispatchNode {

    private static final String LOG_PREFIX = "[AgentGraph.Dispatch] ";

    public Map<String, Object> apply(PatentGraphState state) {
        List<String> plan = state.plan();
        int idx = state.currentStepIndex();
        String currentTask = (plan != null && idx >= 0 && idx < plan.size()) ? plan.get(idx) : "";
        String nextNode = "synthesize";
        if (plan != null && idx >= 0 && idx < plan.size()) {
            String step = plan.get(idx).trim().toLowerCase();
            if (step.contains("retrieval") || step.contains("analysis") || step.contains("advice")) {
                nextNode = "executor";
            } else {
                nextNode = "synthesize";
            }
        }
        log.info("{} plan.size()={} currentStepIndex={} currentTask=[{}] -> nextNode={}", LOG_PREFIX, plan != null ? plan.size() : 0, idx, currentTask, nextNode);
        return Map.of(PatentGraphState.NEXT_NODE, nextNode);
    }
}

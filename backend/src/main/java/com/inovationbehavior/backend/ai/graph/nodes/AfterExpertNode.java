package com.inovationbehavior.backend.ai.graph.nodes;

import com.inovationbehavior.backend.ai.graph.PatentGraphState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * P&E 专家后置节点：根据 needReplan、currentStepIndex 与 plan 决定下一跳（replan | synthesize | dispatch）。
 */
@Component
@Slf4j
public class AfterExpertNode {

    private static final String LOG_PREFIX = "[AgentGraph.AfterExpert] ";

    public Map<String, Object> apply(PatentGraphState state) {
        boolean environmentChanged = state.environmentChanged();
        boolean needReplan = state.needReplan();
        int currentStepIndex = state.currentStepIndex();
        int stepCount = state.stepCount();
        int maxSteps = state.maxSteps();
        List<String> plan = state.plan();
        int planSize = plan != null ? plan.size() : 0;

        String nextNode;
        if (stepCount >= maxSteps) {
            nextNode = "synthesize";
            log.info("{} stepCount={} >= maxSteps={} -> synthesize", LOG_PREFIX, stepCount, maxSteps);
        } else if (environmentChanged || needReplan) {
            nextNode = "replan";
            log.info("{} environmentChanged={} needReplan={} -> replan", LOG_PREFIX, environmentChanged, needReplan);
        } else if (currentStepIndex >= planSize) {
            nextNode = "synthesize";
            log.info("{} currentStepIndex={} >= planSize={} -> synthesize", LOG_PREFIX, currentStepIndex, planSize);
        } else {
            nextNode = "dispatch";
            log.info("{} currentStepIndex={} planSize={} -> dispatch (next step)", LOG_PREFIX, currentStepIndex, planSize);
        }
        return Map.of(PatentGraphState.NEXT_NODE, nextNode);
    }
}

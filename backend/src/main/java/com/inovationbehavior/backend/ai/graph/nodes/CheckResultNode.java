package com.inovationbehavior.backend.ai.graph.nodes;

import com.inovationbehavior.backend.ai.app.IBApp;
import com.inovationbehavior.backend.ai.graph.PatentGraphState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * P&E 结果检查节点：每执行完一个子任务后，检查是否发生环境变化（如专利已失效），
 * 若需更新剩余任务则写 environmentChanged=1。
 */
@Component
@Slf4j
public class CheckResultNode {

    private final IBApp ibApp;

    public CheckResultNode(@Lazy IBApp ibApp) {
        this.ibApp = ibApp;
    }

    private static final String LOG_PREFIX = "[AgentGraph.CheckResult] ";

    public Map<String, Object> apply(PatentGraphState state) {
        List<String> stepResults = state.stepResults();
        List<String> plan = state.plan();
        int currentStepIndex = state.currentStepIndex();

        String lastResult = (stepResults != null && !stepResults.isEmpty())
                ? stepResults.get(stepResults.size() - 1) : "";
        List<String> remaining = (plan != null && currentStepIndex < plan.size())
                ? plan.subList(currentStepIndex, plan.size()) : List.of();
        String userMessage = state.userMessage().orElse("");

        log.info("{}>>> 进入节点 | stepResultsSize={} currentStepIndex={} remainingSize={} lastResultPreview={}",
                LOG_PREFIX, stepResults != null ? stepResults.size() : 0, currentStepIndex, remaining.size(),
                abbreviate(lastResult, 100));

        boolean envChanged = ibApp.checkEnvironmentChange(lastResult, remaining, userMessage);
        int envChangedInt = envChanged ? 1 : 0;

        log.info("{}<<< 离开节点 | environmentChanged={}", LOG_PREFIX, envChanged);
        return Map.of(PatentGraphState.ENVIRONMENT_CHANGED, envChangedInt);
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "null";
        s = s.trim();
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}

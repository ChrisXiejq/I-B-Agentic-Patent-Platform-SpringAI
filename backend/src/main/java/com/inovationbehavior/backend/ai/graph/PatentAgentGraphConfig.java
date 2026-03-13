package com.inovationbehavior.backend.ai.graph;

import com.inovationbehavior.backend.ai.graph.nodes.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 专利平台多 Agent 图编排配置（P&E + Replan）。
 * 1. Planner：生成 Task List。
 * 2. Executor：针对每个子任务启动 ReAct 循环（RAG + 工具调用）。
 * 3. CheckResult：每执行完一个子任务检查环境变化；Re-planner 动态更新剩余任务列表。
 * 流程：START → planner → dispatch → (executor|synthesize)；executor → checkResult → afterExpert；
 * afterExpert → (replan|synthesize|dispatch)；replan → dispatch；synthesize → END。
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class PatentAgentGraphConfig {

    private final PlannerNode plannerNode;
    private final DispatchNode dispatchNode;
    private final ExecutorNode executorNode;
    private final CheckResultNode checkResultNode;
    private final AfterExpertNode afterExpertNode;
    private final ReplanNode replanNode;
    private final SynthesizeNode synthesizeNode;

    @Value("${app.agent.graph.max-steps:5}")
    private int defaultMaxSteps;

    @Bean
    public CompiledGraph<PatentGraphState> patentAgentGraph() throws GraphStateException {
        StateGraph<PatentGraphState> graph = new StateGraph<>(PatentGraphState.SCHEMA, PatentGraphState::new)
                .addNode("planner", node(state -> plannerNode.apply(state)))
                .addNode("dispatch", node(state -> dispatchNode.apply(state)))
                .addNode("executor", node(state -> executorNode.apply(state)))
                .addNode("checkResult", node(state -> checkResultNode.apply(state)))
                .addNode("afterExpert", node(state -> afterExpertNode.apply(state)))
                .addNode("replan", node(state -> replanNode.apply(state)))
                .addNode("synthesize", node(state -> synthesizeNode.apply(state)))
                .addEdge(START, "planner")
                .addEdge("planner", "dispatch")
                .addConditionalEdges("dispatch", (PatentGraphState state) -> CompletableFuture.completedFuture(state.nextNode().orElse("synthesize")),
                        Map.of(
                                "executor", "executor",
                                "synthesize", "synthesize"))
                .addEdge("executor", "checkResult")
                .addEdge("checkResult", "afterExpert")
                .addConditionalEdges("afterExpert", (PatentGraphState state) -> CompletableFuture.completedFuture(state.nextNode().orElse("synthesize")),
                        Map.of(
                                "replan", "replan",
                                "synthesize", "synthesize",
                                "dispatch", "dispatch"))
                .addEdge("replan", "dispatch")
                .addEdge("synthesize", END);

        return graph.compile(CompileConfig.builder().recursionLimit(30).build());
    }

    private AsyncNodeAction<PatentGraphState> node(java.util.function.Function<PatentGraphState, Map<String, Object>> action) {
        return (state) -> CompletableFuture.completedFuture(action.apply(state));
    }
}

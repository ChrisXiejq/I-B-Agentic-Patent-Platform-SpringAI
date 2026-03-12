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
 * 专利平台多 Agent 图编排配置：Router → 检索/分析/建议专家 → 综合 → END。
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class PatentAgentGraphConfig {

    private final RouterNode routerNode;
    private final RetrievalExpertNode retrievalExpertNode;
    private final AnalysisExpertNode analysisExpertNode;
    private final AdviceExpertNode adviceExpertNode;
    private final SynthesizeNode synthesizeNode;

    @Value("${app.agent.graph.max-steps:5}")
    private int defaultMaxSteps;

    @Bean
    public CompiledGraph<PatentGraphState> patentAgentGraph() throws GraphStateException {
        StateGraph<PatentGraphState> graph = new StateGraph<>(PatentGraphState.SCHEMA, PatentGraphState::new)
                .addNode("router", node(state -> routerNode.apply(state)))
                .addNode("retrievalExpert", node(state -> retrievalExpertNode.apply(state)))
                .addNode("analysisExpert", node(state -> analysisExpertNode.apply(state)))
                .addNode("adviceExpert", node(state -> adviceExpertNode.apply(state)))
                .addNode("synthesize", node(state -> synthesizeNode.apply(state)))
                .addEdge(START, "router")
                .addConditionalEdges("router", (PatentGraphState state) -> CompletableFuture.completedFuture(state.nextNode().orElse("synthesize")),
                        Map.of(
                                "retrieval", "retrievalExpert",
                                "analysis", "analysisExpert",
                                "advice", "adviceExpert",
                                "synthesize", "synthesize",
                                "end", "synthesize"))  // end 也走综合节点，生成告别语
                .addEdge("retrievalExpert", "router")
                .addEdge("analysisExpert", "router")
                .addEdge("adviceExpert", "router")
                .addEdge("synthesize", END);

        return graph.compile(CompileConfig.builder().recursionLimit(30).build());
    }

    private AsyncNodeAction<PatentGraphState> node(java.util.function.Function<PatentGraphState, Map<String, Object>> action) {
        return (state) -> CompletableFuture.completedFuture(action.apply(state));
    }
}

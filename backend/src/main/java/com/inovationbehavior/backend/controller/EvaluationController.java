package com.inovationbehavior.backend.controller;

import com.inovationbehavior.backend.evaluation.AgentEvaluationService;
import com.inovationbehavior.backend.evaluation.RagEvaluationService;
import com.inovationbehavior.backend.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * RAG 与 Agent 能力评估接口，支撑模型与策略迭代。
 */
@RestController
@RequestMapping("/agent/eval")
@RequiredArgsConstructor
public class EvaluationController {

    private final RagEvaluationService ragEvaluationService;
    private final AgentEvaluationService agentEvaluationService;

    /**
     * RAG 评估：请求体为 { "samples": [ { "query": "...", "relevantContents": ["...", "..."] } ], "k": 5 }
     */
    @PostMapping("/rag")
    public Result ragEval(@RequestBody RagEvalRequest request) {
        List<RagEvaluationService.RagSample> samples = request.getSamples().stream()
                .map(s -> new RagEvaluationService.RagSample(s.query(), s.relevantContents() != null ? s.relevantContents() : Set.of()))
                .toList();
        RagEvaluationService.RagMetrics metrics = ragEvaluationService.evaluate(samples, request.getK() > 0 ? request.getK() : 5);
        return Result.success(metrics);
    }

    /**
     * Agent 评估：请求体为 { "samples": [ { "query": "...", "userId": "...", "referenceAnswerOrCriteria": "..." } ], "useLlmJudge": false }
     */
    @PostMapping("/agent")
    public Result agentEval(@RequestBody AgentEvalRequest request) {
        List<AgentEvaluationService.AgentSample> samples = request.getSamples().stream()
                .map(s -> new AgentEvaluationService.AgentSample(s.query(), s.userId(), s.referenceAnswerOrCriteria()))
                .toList();
        AgentEvaluationService.AgentEvalResult result;
        if (Boolean.TRUE.equals(request.getUseLlmJudge()) && !samples.isEmpty()) {
            List<AgentEvaluationService.AgentEvalResult> singleResults = samples.stream()
                    .map(agentEvaluationService::evaluateWithLlmJudge).toList();
            int n = singleResults.size();
            double f = singleResults.stream().mapToDouble(AgentEvaluationService.AgentEvalResult::faithfulness).sum() / n;
            double c = singleResults.stream().mapToDouble(AgentEvaluationService.AgentEvalResult::factualConsistency).sum() / n;
            double r = singleResults.stream().mapToDouble(AgentEvaluationService.AgentEvalResult::reasoningQuality).sum() / n;
            result = new AgentEvaluationService.AgentEvalResult(f, c, r, "");
        } else {
            result = agentEvaluationService.evaluate(samples);
        }
        return Result.success(result);
    }

    public record RagEvalRequest(List<RagSampleDto> samples, int k) {
        public int getK() { return k; }
        public List<RagSampleDto> getSamples() { return samples != null ? samples : List.of(); }
    }
    public record RagSampleDto(String query, Set<String> relevantContents) {}
    public record AgentEvalRequest(List<AgentSampleDto> samples, Boolean useLlmJudge) {
        public List<AgentSampleDto> getSamples() { return samples != null ? samples : List.of(); }
        public Boolean getUseLlmJudge() { return useLlmJudge != null && useLlmJudge; }
    }
    public record AgentSampleDto(String query, String userId, String referenceAnswerOrCriteria) {}
}

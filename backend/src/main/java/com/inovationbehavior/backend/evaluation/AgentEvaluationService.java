package com.inovationbehavior.backend.evaluation;

import com.inovationbehavior.backend.service.intf.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Agent 生成效果评估：Faithfulness、Factual Consistency、Reasoning Quality，
 * 支撑模型与策略迭代。当前为规则/启发式实现，可扩展为 LLM-as-judge。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEvaluationService {

    private final AgentService agentService;
    private final ChatModel chatModel;

    /** 单条评估样本 */
    public record AgentSample(String query, String userId, String referenceAnswerOrCriteria) {}

    /** 评估输出 */
    public record AgentEvalResult(double faithfulness, double factualConsistency, double reasoningQuality, String modelOutput) {}

    private static final Pattern REASONING_PATTERN = Pattern.compile("(THOUGHT:|推理|因为|所以|首先|然后|因此)");

    /**
     * 对单条样本调用 Agent 并计算三项指标（0~1）。
     */
    public AgentEvalResult evaluateOne(AgentSample sample) {
        String output = agentService.chat(sample.query(), sample.userId());
        double faithfulness = scoreFaithfulness(output, sample.referenceAnswerOrCriteria());
        double factual = scoreFactualConsistency(output, sample.referenceAnswerOrCriteria());
        double reasoning = scoreReasoningQuality(output);
        return new AgentEvalResult(faithfulness, factual, reasoning, output);
    }

    /**
     * 批量评估，返回平均分
     */
    public AgentEvalResult evaluate(List<AgentSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return new AgentEvalResult(0, 0, 0, "");
        }
        double f = 0, fact = 0, r = 0;
        for (AgentSample s : samples) {
            AgentEvalResult one = evaluateOne(s);
            f += one.faithfulness();
            fact += one.factualConsistency();
            r += one.reasoningQuality();
        }
        int n = samples.size();
        return new AgentEvalResult(f / n, fact / n, r / n, "");
    }

    /** 忠实度：输出是否紧扣参考/依据（简单用关键词重叠率近似） */
    private double scoreFaithfulness(String output, String reference) {
        if (output == null || output.isBlank()) return 0;
        if (reference == null || reference.isBlank()) return 0.5;
        String[] refWords = reference.split("\\s+");
        int match = 0;
        for (String w : refWords) {
            if (w.length() > 1 && output.contains(w)) match++;
        }
        return refWords.length == 0 ? 0.5 : Math.min(1.0, (double) match / refWords.length * 2);
    }

    /** 事实一致性：与参考事实是否冲突（简单启发：无明确否定参考则给较高分） */
    private double scoreFactualConsistency(String output, String reference) {
        if (output == null || output.isBlank()) return 0;
        if (reference == null || reference.isBlank()) return 0.5;
        if (output.contains("与上述不符") || output.contains("与参考矛盾")) return 0.2;
        return 0.8;
    }

    /** 推理质量：是否包含推理结构（THOUGHT、因为、所以等） */
    private double scoreReasoningQuality(String output) {
        if (output == null || output.isBlank()) return 0;
        return REASONING_PATTERN.matcher(output).find() ? 0.9 : 0.5;
    }

    /**
     * 使用 LLM 对单条进行三项打分（可选扩展）
     */
    public AgentEvalResult evaluateWithLlmJudge(AgentSample sample) {
        String output = agentService.chat(sample.query(), sample.userId());
        String prompt = """
                请对以下 Agent 回复从 0~1 打分（仅输出三个数字，用逗号分隔）：
                1. Faithfulness（忠实于依据） 2. Factual Consistency（事实一致） 3. Reasoning Quality（推理质量）
                问题：%s
                参考/依据：%s
                Agent 回复：%s
                输出格式：0.8,0.9,0.7
                """.formatted(sample.query(), sample.referenceAnswerOrCriteria(), output);
        try {
            String resp = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
            String[] parts = resp.trim().split("[,，\\s]+");
            double f = parts.length > 0 ? parseScore(parts[0]) : 0.5;
            double fact = parts.length > 1 ? parseScore(parts[1]) : 0.5;
            double r = parts.length > 2 ? parseScore(parts[2]) : 0.5;
            return new AgentEvalResult(f, fact, r, output);
        } catch (Exception e) {
            log.warn("LLM Judge 失败，退回启发式", e);
            return evaluateOne(sample);
        }
    }

    private static double parseScore(String s) {
        try {
            double v = Double.parseDouble(s.replaceAll("[^0-9.]", ""));
            return Math.max(0, Math.min(1, v));
        } catch (Exception e) {
            return 0.5;
        }
    }
}

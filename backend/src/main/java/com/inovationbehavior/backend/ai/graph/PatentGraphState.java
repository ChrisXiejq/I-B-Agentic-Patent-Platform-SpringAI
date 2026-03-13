package com.inovationbehavior.backend.ai.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

/**
 * 多 Agent 图编排的共享状态（专利平台）。
 * 支持 P&E：Plan（多步计划）、Execute（按步执行）、Replan（结果不足时重新规划）。
 */
public class PatentGraphState extends AgentState {

    public static final String USER_MESSAGE = "userMessage";
    public static final String CHAT_ID = "chatId";
    /** 各专家节点的输出累积列表 */
    public static final String STEP_RESULTS = "stepResults";
    /** 路由/派发决策：retrieval | analysis | advice | synthesize | end */
    public static final String NEXT_NODE = "nextNode";
    /** 最终回复（综合节点写入） */
    public static final String FINAL_ANSWER = "finalAnswer";
    /** 当前已执行专家步数 */
    public static final String STEP_COUNT = "stepCount";
    /** 最大专家步数，防止死循环 */
    public static final String MAX_STEPS = "maxSteps";

    /** P&E：当前执行计划，如 ["retrieval","analysis","advice"] 或 ["synthesize"] */
    public static final String PLAN = "plan";
    /** P&E：当前执行到计划的第几步（0-based） */
    public static final String CURRENT_STEP_INDEX = "currentStepIndex";
    /** P&E：上一步执行结果是否不足，需要 Replan（1=是，0=否） */
    public static final String NEED_REPLAN = "needReplan";
    /** P&E：检测到环境变化（如专利已失效），需更新剩余任务列表（1=是，0=否） */
    public static final String ENVIRONMENT_CHANGED = "environmentChanged";

    /** 简单覆盖型 channel 用 base；列表累积用 appender。默认值必须非 null，否则 LangGraph4j initialDataFromSchema 会 NPE。 */
    @SuppressWarnings("unchecked")
    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            entry(USER_MESSAGE, Channels.base(() -> "")),
            entry(CHAT_ID, Channels.base(() -> "")),
            entry(STEP_RESULTS, Channels.appender(ArrayList::new)),
            entry(NEXT_NODE, Channels.base(() -> "")),
            entry(FINAL_ANSWER, Channels.base(() -> "")),
            entry(STEP_COUNT, Channels.base(() -> 0)),
            entry(MAX_STEPS, Channels.base(() -> 5)),
            entry(PLAN, Channels.base(ArrayList::new)),
            entry(CURRENT_STEP_INDEX, Channels.base(() -> 0)),
            entry(NEED_REPLAN, Channels.base(() -> 0)),
            entry(ENVIRONMENT_CHANGED, Channels.base(() -> 0))
    );

    public PatentGraphState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> userMessage() {
        return value(USER_MESSAGE);
    }

    public Optional<String> chatId() {
        return value(CHAT_ID);
    }

    @SuppressWarnings("unchecked")
    public List<String> stepResults() {
        return value(STEP_RESULTS).map(v -> (List<String>) v).orElse(List.of());
    }

    public Optional<String> nextNode() {
        return value(NEXT_NODE);
    }

    public Optional<String> finalAnswer() {
        return value(FINAL_ANSWER);
    }

    public int stepCount() {
        return value(STEP_COUNT).map(v -> (Number) v).map(Number::intValue).orElse(0);
    }

    public int maxSteps() {
        return value(MAX_STEPS).map(v -> (Number) v).map(Number::intValue).orElse(5);
    }

    @SuppressWarnings("unchecked")
    public List<String> plan() {
        return value(PLAN).map(v -> (List<String>) v).orElse(List.of());
    }

    public int currentStepIndex() {
        return value(CURRENT_STEP_INDEX).map(v -> (Number) v).map(Number::intValue).orElse(0);
    }

    /** true 表示上一步结果不足，应进入 Replan 节点 */
    public boolean needReplan() {
        return value(NEED_REPLAN).map(v -> (Number) v).map(Number::intValue).orElse(0) != 0;
    }

    /** true 表示检测到环境变化（如专利已失效），应更新剩余任务列表 */
    public boolean environmentChanged() {
        return value(ENVIRONMENT_CHANGED).map(v -> (Number) v).map(Number::intValue).orElse(0) != 0;
    }
}

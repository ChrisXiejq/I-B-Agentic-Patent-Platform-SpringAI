package com.inovationbehavior.backend.ai.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 多 Agent 图编排的共享状态（专利平台）。
 * 用于 Router、检索专家、分析专家、建议专家、综合节点之间传递数据。
 */
public class PatentGraphState extends AgentState {

    public static final String USER_MESSAGE = "userMessage";
    public static final String CHAT_ID = "chatId";
    /** 各专家节点的输出累积列表 */
    public static final String STEP_RESULTS = "stepResults";
    /** 路由决策：retrieval | analysis | advice | synthesize | end */
    public static final String NEXT_NODE = "nextNode";
    /** 最终回复（综合节点写入） */
    public static final String FINAL_ANSWER = "finalAnswer";
    /** 当前已执行专家步数 */
    public static final String STEP_COUNT = "stepCount";
    /** 最大专家步数，防止死循环 */
    public static final String MAX_STEPS = "maxSteps";

    /** 简单覆盖型 channel 用 base；列表累积用 appender。默认值必须非 null，否则 LangGraph4j initialDataFromSchema 会 NPE。 */
    @SuppressWarnings("unchecked")
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            USER_MESSAGE, Channels.base(() -> ""),
            CHAT_ID, Channels.base(() -> ""),
            STEP_RESULTS, Channels.appender(ArrayList::new),
            NEXT_NODE, Channels.base(() -> ""),
            FINAL_ANSWER, Channels.base(() -> ""),
            STEP_COUNT, Channels.base(() -> 0),
            MAX_STEPS, Channels.base(() -> 5)
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
}

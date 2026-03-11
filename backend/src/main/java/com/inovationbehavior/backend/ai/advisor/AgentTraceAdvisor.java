package com.inovationbehavior.backend.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Agent 可观测：记录每一步的请求、响应、以及模型发起的工具调用，便于理解 Agent 的思考与执行过程。
 * 建议与 {@link com.inovationbehavior.backend.ai.config.TracingToolCallbackProvider} 配合使用，可同时看到每次工具的真实执行与返回。
 */
@Slf4j
public class AgentTraceAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String TRACE_PREFIX = "[AgentTrace] ";

    @Override
    public String getName() {
        return "AgentTraceAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 200; // 尽量早执行，便于看到完整链路
    }

    private ChatClientRequest before(ChatClientRequest request) {
        String userText = request.prompt() != null && request.prompt().getUserMessage() != null
                ? request.prompt().getUserMessage().getText() : "";
        Object cid = request.context().get(ChatMemory.CONVERSATION_ID);
        String conversationId = cid != null ? cid.toString() : null;
        log.info("{} Request | conversationId={} | userMessage(length={}) | preview={}",
                TRACE_PREFIX, conversationId, userText != null ? userText.length() : 0, abbreviate(userText, 120));
        return request;
    }

    private void after(ChatClientResponse clientResponse) {
        try {
            var chatResponse = clientResponse.chatResponse();
            if (chatResponse == null || chatResponse.getResult() == null) return;

            Message output = chatResponse.getResult().getOutput();
            if (output == null) return;

            String text = output.getText();
            log.info("{} Response | text(length={}) | preview={}",
                    TRACE_PREFIX, text != null ? text.length() : 0, abbreviate(text, 200));

            if (output instanceof AssistantMessage assistantMessage) {
                List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    for (AssistantMessage.ToolCall tc : toolCalls) {
                        log.info("{} ToolCall | name={} | arguments={}", TRACE_PREFIX, tc.name(), abbreviate(tc.arguments(), 300));
                    }
                }
            }

            if (chatResponse.getMetadata() != null) {
                var meta = chatResponse.getMetadata();
                if (meta.getUsage() != null) {
                    log.info("{} Metadata | usage={}", TRACE_PREFIX, meta.getUsage());
                }
                if (meta.getModel() != null && !meta.getModel().isBlank()) {
                    log.info("{} Metadata | model={}", TRACE_PREFIX, meta.getModel());
                }
            }
        } catch (Exception e) {
            log.warn("{} Failed to log response: {}", TRACE_PREFIX, e.getMessage());
        }
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "null";
        s = s.trim();
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        request = before(request);
        ChatClientResponse response = chain.nextCall(request);
        after(response);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        request = before(request);
        Flux<ChatClientResponse> flux = chain.nextStream(request);
        return new ChatClientMessageAggregator().aggregateChatClientResponse(flux, this::after);
    }
}

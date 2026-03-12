package com.inovationbehavior.backend.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * 自定义日志 Advisor
 * 打印 info 级别日志、只输出单次用户提示词和 AI 回复的文本
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return 0;
	}

	private static final String LOG_PREFIX = "[AgentGraph.LLM] ";

	private ChatClientRequest before(ChatClientRequest request) {
		Prompt prompt = request.prompt();
		String promptText = prompt != null ? prompt.getContents() : null;
		log.info("{}Request prompt(length={}) preview={}", LOG_PREFIX,
				promptText != null ? promptText.length() : 0, abbreviate(promptText, 200));
		return request;
	}

	private void observeAfter(ChatClientResponse chatClientResponse) {
		String text = chatClientResponse.chatResponse().getResult().getOutput().getText();
		log.info("{}Response length={} preview={}", LOG_PREFIX, text != null ? text.length() : 0, abbreviate(text, 300));
	}

	private static String abbreviate(String s, int maxLen) {
		if (s == null) return "null";
		s = s.trim();
		if (s.length() <= maxLen) return s;
		return s.substring(0, maxLen) + "...";
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
		chatClientRequest = before(chatClientRequest);
		ChatClientResponse chatClientResponse = chain.nextCall(chatClientRequest);
		observeAfter(chatClientResponse);
		return chatClientResponse;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
		chatClientRequest = before(chatClientRequest);
		Flux<ChatClientResponse> chatClientResponseFlux = chain.nextStream(chatClientRequest);
		return (new ChatClientMessageAggregator()).aggregateChatClientResponse(chatClientResponseFlux, this::observeAfter);
	}
}

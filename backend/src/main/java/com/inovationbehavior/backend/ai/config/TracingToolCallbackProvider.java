package com.inovationbehavior.backend.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Arrays;

/**
 * 对工具执行做打点：每次模型发起工具调用并执行时，打印工具名、入参和返回结果摘要。
 * 由 {@link AiToolConfig} 在 app.agent.trace-tools=true 时包装默认 Provider 使用。
 */
@Slf4j
public class TracingToolCallbackProvider implements ToolCallbackProvider {

    private static final String TRACE_PREFIX = "[AgentTrace.Tool] ";

    private final ToolCallbackProvider delegate;

    public TracingToolCallbackProvider(ToolCallbackProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        ToolCallback[] raw = delegate.getToolCallbacks();
        if (raw == null || raw.length == 0) return raw;
        return Arrays.stream(raw)
                .map(TracingToolCallback::new)
                .toArray(ToolCallback[]::new);
    }

    private static class TracingToolCallback implements ToolCallback {
        private final ToolCallback delegate;

        TracingToolCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public String call(String toolInput) {
            String name = toolName();
            log.info("{} name={} | input(length={}) | preview={}", TRACE_PREFIX, name,
                    toolInput != null ? toolInput.length() : 0, abbreviate(toolInput, 200));
            long start = System.currentTimeMillis();
            try {
                String result = delegate.call(toolInput);
                long elapsed = System.currentTimeMillis() - start;
                log.info("{} name={} | result(length={}) | preview={} | elapsedMs={}", TRACE_PREFIX, name,
                        result != null ? result.length() : 0, abbreviate(result, 300), elapsed);
                return result;
            } catch (Exception e) {
                log.warn("{} name={} | error={} | elapsedMs={}", TRACE_PREFIX, name, e.getMessage(), System.currentTimeMillis() - start);
                throw e;
            }
        }

        private String toolName() {
            try {
                if (delegate.getToolDefinition() != null) {
                    return delegate.getToolDefinition().name();
                }
            } catch (Exception ignored) {
            }
            return delegate.getClass().getSimpleName();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        private static String abbreviate(String s, int maxLen) {
            if (s == null) return "null";
            s = s.trim();
            if (s.length() <= maxLen) return s;
            return s.substring(0, maxLen) + "...";
        }
    }
}

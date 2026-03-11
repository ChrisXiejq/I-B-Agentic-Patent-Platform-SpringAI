package com.inovationbehavior.backend.config;

import com.inovationbehavior.backend.ai.config.TracingToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 提供默认的 ToolCallback / ToolCallbackProvider Bean，在未启用 MCP 或自定义工具时保证正常启动。
 * 当 app.agent.trace-tools=true 时，使用 {@link TracingToolCallbackProvider} 包装，可打印每次工具调用的名称、入参和返回。
 */
@Configuration
public class AiToolConfig {

    @Bean(name = "allTools")
    @ConditionalOnMissingBean(name = "allTools")
    public ToolCallback[] allTools() {
        return new ToolCallback[0];
    }

    @Bean
    @ConditionalOnMissingBean(ToolCallbackProvider.class)
    public ToolCallbackProvider toolCallbackProvider(ToolCallback[] allTools,
                                                     @Value("${app.agent.trace-tools:false}") boolean traceTools) {
        ToolCallbackProvider raw = () -> allTools;
        return traceTools ? new TracingToolCallbackProvider(raw) : raw;
    }
}

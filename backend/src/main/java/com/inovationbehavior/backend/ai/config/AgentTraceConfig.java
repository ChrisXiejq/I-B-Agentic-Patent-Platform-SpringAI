package com.inovationbehavior.backend.ai.config;

import com.inovationbehavior.backend.ai.advisor.AgentTraceAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 可观测配置：注册 {@link AgentTraceAdvisor}，用于在日志中查看每一步请求/响应及模型发起的工具调用。
 */
@Configuration
public class AgentTraceConfig {

    @Bean
    public Advisor agentTraceAdvisor() {
        return new AgentTraceAdvisor();
    }
}

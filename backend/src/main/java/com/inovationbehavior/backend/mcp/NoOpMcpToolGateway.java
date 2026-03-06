package com.inovationbehavior.backend.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP 未配置时的占位实现，不提供任何远程工具。
 * app.agent.mcp.enabled=true 时仅注册 McpClientToolGateway，本 Bean 不注册。
 */
@Component
@ConditionalOnMissingBean(name = "mcpClientToolGateway")
public class NoOpMcpToolGateway implements McpToolGateway {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public List<String> getToolNames() {
        return List.of();
    }

    @Override
    public String callTool(String toolName, Map<String, Object> args) {
        return "MCP 未配置或不可用，请使用本地工具。";
    }
}

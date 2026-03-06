package com.inovationbehavior.backend.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 远程工具网关：当 app.agent.mcp.enabled=true 且 server-url 配置时，
 * 可在此处接入 io.modelcontextprotocol.sdk 的 McpClient，实现与独立 MCP Server（RAG/业务 API）的对接。
 * 当前为占位实现，返回配置的 tool-names 供 Agent 提示使用，执行时提示“请部署 MCP Server”。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.agent.mcp", name = "enabled", havingValue = "true")
public class McpClientToolGateway implements McpToolGateway {

    @Value("${app.agent.mcp.server-url:}")
    private String serverUrl;

    @Value("${app.agent.mcp.tool-names:}")
    private String toolNamesConfig = "";

    @Override
    public boolean isAvailable() {
        return serverUrl != null && !serverUrl.isBlank();
    }

    @Override
    public List<String> getToolNames() {
        if (toolNamesConfig == null || toolNamesConfig.isBlank()) return List.of();
        return Arrays.stream(toolNamesConfig.split("[,，\\s]+")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    @Override
    public String callTool(String toolName, Map<String, Object> args) {
        if (!isAvailable()) return "MCP 未配置或不可用。";
        return "MCP 工具 \"" + toolName + "\" 需由独立 MCP Server 提供。请部署 mcp-server-rag / mcp-server-business 并配置 " +
                "io.modelcontextprotocol.sdk 客户端连接 " + serverUrl + " 后重试。";
    }
}

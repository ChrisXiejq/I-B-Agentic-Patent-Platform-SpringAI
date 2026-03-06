package com.inovationbehavior.backend.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具网关：连接独立 MCP Server，发现并执行远程工具，供 Agent 自动路由调用。
 * 支持配置化与插拔（未配置时 Agent 仅使用本地 PatentAgentTools）。
 */
public interface McpToolGateway {

    /**
     * 是否可用（已连接并初始化）
     */
    boolean isAvailable();

    /**
     * 远程 MCP 工具名称列表，用于拼入 Agent 系统提示与路由判断。
     */
    List<String> getToolNames();

    /**
     * 执行远程工具
     *
     * @param toolName 工具名
     * @param args     参数（通常来自 LLM 输出的 ARGS JSON）
     * @return 执行结果文本
     */
    String callTool(String toolName, Map<String, Object> args);
}

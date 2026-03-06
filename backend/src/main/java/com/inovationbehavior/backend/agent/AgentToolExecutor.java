package com.inovationbehavior.backend.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovationbehavior.backend.mcp.McpToolGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 LLM 输出中的工具调用（ReAct 风格），并委托 PatentAgentTools 执行。
 * 支持格式：ACTION: tool_name ARGS: {"key":"value"} 或 ACTION: tool_name ARGS: patent_no=xxx
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolExecutor {

    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "ACTION:\\s*(\\w+)\\s*(?:ARGS?:\\s*([\\s\\S]*?))?(?=ACTION:|OBSERVATION:|THOUGHT:|$)",
            Pattern.CASE_INSENSITIVE
    );

    private final PatentAgentTools patentAgentTools;
    private final McpToolGateway mcpToolGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> LOCAL_TOOLS = Set.of(
            "get_identification", "getIdentification",
            "get_enterprise_interest", "getEnterpriseInterest",
            "get_patent_analysis", "getPatentAnalysis",
            "get_rag_patent_info", "getRagPatentInfo"
    );

    /**
     * 若 content 包含 ACTION: xxx，则执行对应工具并返回执行结果；否则返回 empty。
     */
    public Optional<String> tryExecuteTool(String content) {
        Matcher m = ACTION_PATTERN.matcher(content);
        if (!m.find()) {
            return Optional.empty();
        }
        String toolName = m.group(1).trim();
        String argsStr = m.group(2) != null ? m.group(2).trim() : "";
        try {
            String result = dispatch(toolName, argsStr);
            return Optional.of("OBSERVATION: " + result);
        } catch (Exception e) {
            log.warn("Tool execution failed: {} with args {}", toolName, argsStr, e);
            return Optional.of("OBSERVATION: 工具执行失败: " + e.getMessage());
        }
    }

    private String dispatch(String toolName, String argsStr) throws JsonProcessingException {
        if (mcpToolGateway != null && mcpToolGateway.isAvailable() && !LOCAL_TOOLS.contains(toolName)) {
            Map<String, Object> argsMap = parseArgsToMap(argsStr);
            return mcpToolGateway.callTool(toolName, argsMap);
        }
        String userId = getArg(argsStr, "userId", "user_id");
        String patentNo = getArg(argsStr, "patentNo", "patent_no", "patentNo");
        String query = getArg(argsStr, "query", "q");

        return switch (toolName) {
            case "get_identification", "getIdentification" -> patentAgentTools.getIdentification(userId != null ? userId : "");
            case "get_enterprise_interest", "getEnterpriseInterest" -> {
                if (patentNo == null || patentNo.isBlank()) throw new IllegalArgumentException("patent_no 必填");
                yield patentAgentTools.getEnterpriseInterest(patentNo);
            }
            case "get_patent_analysis", "getPatentAnalysis" -> {
                if (patentNo == null || patentNo.isBlank()) throw new IllegalArgumentException("patent_no 必填");
                yield patentAgentTools.getPatentAnalysis(patentNo);
            }
            case "get_rag_patent_info", "getRagPatentInfo" -> {
                if (patentNo == null || patentNo.isBlank()) throw new IllegalArgumentException("patent_no 必填");
                yield patentAgentTools.getRagPatentInfo(patentNo, query);
            }
            default -> "未知工具: " + toolName + "。可用: get_identification, get_enterprise_interest, get_patent_analysis, get_rag_patent_info";
        };
    }

    private String getArg(String argsStr, String... keys) throws JsonProcessingException {
        if (argsStr == null || argsStr.isBlank()) return null;
        String trimmed = argsStr.trim();
        if (trimmed.startsWith("{")) {
            JsonNode node = objectMapper.readTree(trimmed);
            for (String key : keys) {
                if (node.has(key)) return node.get(key).asText(null);
            }
            return null;
        }
        for (String pair : trimmed.split("[,;]")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim().replace("\"", "");
            for (String key : keys) {
                if (key.equals(k)) return kv[1].trim().replace("\"", "");
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgsToMap(String argsStr) {
        if (argsStr == null || argsStr.isBlank()) return Map.of();
        String trimmed = argsStr.trim();
        if (trimmed.startsWith("{")) {
            try {
                return objectMapper.readValue(trimmed, Map.class);
            } catch (JsonProcessingException e) {
                return Map.of();
            }
        }
        Map<String, Object> out = new HashMap<>();
        for (String pair : trimmed.split("[,;]")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) out.put(kv[0].trim().replace("\"", ""), kv[1].trim().replace("\"", ""));
        }
        return out;
    }
}

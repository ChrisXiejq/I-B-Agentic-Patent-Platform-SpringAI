package com.inovationbehavior.backend.service.impl;

import com.inovationbehavior.backend.agent.AgentToolExecutor;
import com.inovationbehavior.backend.agent.ReActPromptConstants;
import com.inovationbehavior.backend.mcp.McpToolGateway;
import com.inovationbehavior.backend.service.intf.AgentService;
import com.inovationbehavior.backend.service.intf.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Agent 核心任务编排：ReAct + CoT 多轮推理与工具调用，结合分层记忆。
 * 通过 Tool 调用接入专利检索、转化热度、RAG 等能力；意图识别与规划由 GPT-4o/通义 在系统提示下完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final ChatModel chatModel;
    private final AgentToolExecutor agentToolExecutor;
    private final MemoryService memoryService;
    private final McpToolGateway mcpToolGateway;

    @Override
    public String chat(String userQuery, String userId) {
        if (userQuery == null || userQuery.isBlank()) {
            return "请输入您的问题。";
        }
        try {
            String systemPrompt = ReActPromptConstants.SYSTEM_PROMPT;
            if (mcpToolGateway != null && mcpToolGateway.isAvailable() && !mcpToolGateway.getToolNames().isEmpty()) {
                systemPrompt += "\n\n## 远程 MCP 工具（可选）\n" + String.join(", ", mcpToolGateway.getToolNames()) + "。需要时可用 ACTION: <工具名> ARGS: {...} 调用。";
            }
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            memoryService.getRecentContext(userId, userQuery).ifPresent(history -> {
                if (!history.isBlank()) {
                    messages.add(new UserMessage("[历史对话摘要]\n" + history));
                }
            });
            messages.add(new UserMessage(userQuery));

            String lastContent;
            int rounds = 0;
            do {
                Prompt prompt = new Prompt(messages);
                lastContent = chatModel.call(prompt).getResult().getOutput().getText();
                if (lastContent == null) lastContent = "";
                Optional<String> observation = agentToolExecutor.tryExecuteTool(lastContent);
                if (observation.isEmpty()) {
                    break;
                }
                messages.add(new AssistantMessage(lastContent));
                messages.add(new UserMessage(observation.get()));
                rounds++;
            } while (rounds < ReActPromptConstants.MAX_TOOL_CALL_ROUNDS);

            memoryService.appendTurn(userId, "user", userQuery);
            memoryService.appendTurn(userId, "assistant", lastContent);
            return lastContent;
        } catch (Exception e) {
            log.warn("Agent 对话失败", e);
            return "Agent 回复失败: " + e.getMessage();
        }
    }
}

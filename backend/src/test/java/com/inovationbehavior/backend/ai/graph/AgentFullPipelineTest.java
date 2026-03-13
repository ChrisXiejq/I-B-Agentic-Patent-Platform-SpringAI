package com.inovationbehavior.backend.ai.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Agent 全链路集成测试：覆盖 P&E 图所有节点与尽可能多的工具。
 *
 * <p>覆盖的节点：Planner → Dispatch → Executor（按任务 retrieval/analysis/advice）→ CheckResult → AfterExpert →（Replan 或）Synthesize。
 * 工具覆盖（依赖模型在 ReAct 中的调用）：PatentDetail、PatentHeat、RAG、WebSearch、MemoryRetrieval(retrieve_history)、UserIdentity 等，
 * 由多步查询在一次运行中尽可能触发。
 *
 * <p>依赖真实 Gemini API 与 Redis/网络。若 Gemini 返回 503（high demand）等瞬时错误，测试会直接抛出该异常而非兜底文案，可稍后重试。
 *
 * <p>Mockito 警告：用 {@code mvn test} 运行时会自动加 -javaagent；在 IDE 中运行若仍见动态 agent 警告，可在测试的 VM 选项中加入
 * {@code -javaagent:${user.home}/.m2/repository/org/mockito/mockito-core/.../mockito-core-...jar}（路径以本地 .m2 为准）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentFullPipelineTest {

    private static final String ERROR_FALLBACK = "Sorry, the request could not be completed";

    @Autowired
    private PatentGraphRunner patentGraphRunner;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("全链路：多步查询（检索+分析+建议）覆盖 Planner/Executor/CheckResult/AfterExpert/Synthesize，返回非空回复")
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void fullPipeline_multiStepQuery_exercisesAllNodesAndReturnsAnswer() {
        String chatId = "test-full-pipeline-" + System.currentTimeMillis();
        String userMessage = "请先查一下专利相关的信息或热度，再简单分析技术价值，最后给我一点转化建议。";
        String answer = patentGraphRunner.run(userMessage, chatId);

        assertNotNull(answer, "图执行应返回非 null");
        assertFalse(answer.isBlank(), "图执行应返回非空字符串");
        assertFalse(answer.contains(ERROR_FALLBACK),
                "不应返回错误兜底文案，表示全链路与 LLM/工具调用正常");

        // 全链路会经过：Planner(生成 plan) -> Dispatch -> Executor(retrieval) -> CheckResult -> AfterExpert -> Dispatch -> Executor(analysis) -> ... -> Synthesize
        // 日志中应能看到 [AgentGraph.Planner]、[AgentGraph.Dispatch]、[AgentGraph.Executor]、[AgentGraph.CheckResult]、[AgentGraph.AfterExpert]、[AgentGraph.Synthesize]
    }

    @Test
    @DisplayName("全链路：明确请求专利详情与热度，鼓励触发 PatentDetail/PatentHeat 等工具")
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void fullPipeline_patentQuery_mayInvokePatentAndRagTools() {
        String chatId = "test-patent-tools-" + System.currentTimeMillis();
        String userMessage = "帮我查专利CN10000001-1的详情和热度，并简要分析价值。";
        String answer = patentGraphRunner.run(userMessage, chatId);

        assertNotNull(answer);
        assertFalse(answer.isBlank());
        assertFalse(answer.contains(ERROR_FALLBACK));
        // 工具是否被调用由模型决定，此处仅断言链路成功完成；若开启 app.agent.trace-tools=true 可在日志中查看 ToolCall
    }

    @Test
    @DisplayName("全链路：简单问候仅经 Planner -> Dispatch -> Synthesize，快速返回")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void fullPipeline_simpleGreeting_onlyPlannerAndSynthesize() {
        String chatId = "test-greeting-" + System.currentTimeMillis();
        String userMessage = "你好，介绍一下你自己。";
        String answer = patentGraphRunner.run(userMessage, chatId);

        assertNotNull(answer);
        assertFalse(answer.isBlank());
        assertFalse(answer.contains(ERROR_FALLBACK));
    }

    @Test
    @DisplayName("全链路：请求涉及检索与网页搜索，可能触发 RAG 与 WebSearch 工具")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void fullPipeline_retrievalAndWebSearch_mayInvokeRagAndSearchWeb() {
        String chatId = "test-rag-web-" + System.currentTimeMillis();
        String userMessage = "搜索一下专利商业化是什么意思，并简要说明。";
        String answer = patentGraphRunner.run(userMessage, chatId);
        assertNotNull(answer);
        assertFalse(answer.isBlank());
        assertFalse(answer.contains(ERROR_FALLBACK));
    }

    @Test
    @DisplayName("全链路：经 HTTP POST /ai/agent 同步调用，覆盖 Controller -> IBApp -> 图")
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void fullPipeline_viaHttpPost_returnsJsonWithContent() throws Exception {
        String chatId = "test-http-full-" + System.currentTimeMillis();
        String body = """
                {"message": "专利转化平台能做什么？请简单说明并给一条建议。", "chatId": "%s", "stream": false}
                """.formatted(chatId);

        MvcResult result = mockMvc.perform(post("/ai/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertNotNull(json);
        assertTrue(json.contains("content"), "响应应包含 content");
        assertTrue(json.contains("success"), "响应应包含 success");
        assertTrue(json.contains(chatId), "响应应包含 chatId");
    }
}


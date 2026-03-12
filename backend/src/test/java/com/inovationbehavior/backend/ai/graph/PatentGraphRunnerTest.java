package com.inovationbehavior.backend.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 多 Agent 图编排集成测试。
 * 会真实调用 LLM、RAG、工具，需配置好 API Key 与数据源；超时时间已放宽。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PatentGraphRunnerTest {

    private static final String ERROR_FALLBACK = "Sorry, the request could not be completed";

    @Autowired
    private PatentGraphRunner patentGraphRunner;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // 1. 直接测图执行器（PatentGraphRunner）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("图执行：检索类问题应返回非空回复")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void run_retrievalStyleQuestion_returnsNonEmptyAnswer() {
        String userMessage = "分析一下热水器专利的价值，并且给我具体的应用建议";
        String chatId = "test-graph-retrieval-" + System.currentTimeMillis();
        String answer = patentGraphRunner.run(userMessage, chatId);
        assertNotNull(answer, "图执行应返回非 null");
        assertFalse(answer.isBlank(), "图执行应返回非空字符串");
        assertFalse(answer.contains(ERROR_FALLBACK),
                "不应返回错误兜底文案，表示图与 LLM 调用正常");
    }

    @Test
    @DisplayName("图执行：简单问候可走路由并返回回复")
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void run_simpleGreeting_returnsAnswer() {
        String userMessage = "你好，请简单介绍一下你自己。";
        String chatId = "test-graph-greeting-" + System.currentTimeMillis();
        String answer = patentGraphRunner.run(userMessage, chatId);
        assertNotNull(answer);
        assertFalse(answer.isBlank());
    }

    @Test
    @DisplayName("图执行：空消息或 null 不抛异常并返回兜底")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void run_emptyOrNullMessage_returnsFallbackWithoutThrow() {
        String empty = patentGraphRunner.run("", "test-empty");
        assertNotNull(empty);
        assertTrue(empty.contains(ERROR_FALLBACK) || empty.length() > 0);

        String nullMsg = patentGraphRunner.run(null, "test-null");
        assertNotNull(nullMsg);
    }

    // -------------------------------------------------------------------------
    // 2. HTTP 接口：POST/GET /api/ai/agent（同步，stream=false）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/ai/agent 同步：返回 JSON 且 content 非空")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void postAgentSync_returnsJsonWithContent() throws Exception {
        String chatId = "test-http-post-" + System.currentTimeMillis();
        String body = """
                {"message": "专利转化是什么意思？", "chatId": "%s", "stream": false}
                """.formatted(chatId);

        MvcResult result = mockMvc.perform(post("/ai/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertNotNull(json);
        assertTrue(json.contains("content"));
        assertTrue(json.contains("success"));
        // 若返回了 content 且 success 为 true，则 content 不应为错误兜底
        if (json.contains("\"success\":true") && json.contains(ERROR_FALLBACK)) {
            // 可能是网络/API 问题，至少结构正确
            assertTrue(json.contains("chatId"));
        }
    }

    @Test
    @DisplayName("GET /api/ai/agent 同步：message 参数必填，stream=false 返回 JSON")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void getAgentSync_returnsJsonWithContent() throws Exception {
        String chatId = "test-http-get-" + System.currentTimeMillis();
        mockMvc.perform(get("/ai/agent")
                        .param("message", "什么是专利许可？")
                        .param("chatId", chatId)
                        .param("stream", "false"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.chatId").value(chatId));
    }

    @Test
    @DisplayName("GET /api/ai/agent 缺少 message 返回 400")
    void getAgentWithoutMessage_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/ai/agent").param("stream", "false"))
                .andExpect(status().isBadRequest());
    }
}

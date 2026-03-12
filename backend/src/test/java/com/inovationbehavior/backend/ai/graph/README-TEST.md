# 多 Agent 图编排测试说明

## 测试类

- **PatentGraphRunnerTest**：多 Agent 图 + HTTP 接口的集成测试。

## 运行方式

在项目根目录（backend）下：

```bash
# 运行本包下所有测试
mvn test -Dtest=com.inovationbehavior.backend.ai.graph.* -q

# 只运行 PatentGraphRunnerTest
mvn test -Dtest=PatentGraphRunnerTest -q
```

或在 IDE 中右键 `PatentGraphRunnerTest` → Run。

## 前置条件

- **API Key**：需配置 `GOOGLE_AI_API_KEY`（或对应 LLM）和 `OPENAI_API_KEY`（Embedding），否则 LLM/向量调用会失败。
- **数据源**：若启用 RAG/向量库，需可用的 PostgreSQL（PgVector）等配置；测试里若连接失败可能报错或超时。
- **超时**：部分用例设置了 90–120 秒超时，因会真实调用 LLM。

## 用例说明

| 用例 | 说明 |
|------|------|
| run_retrievalStyleQuestion_returnsNonEmptyAnswer | 提检索/概念类问题，断言图执行返回非空且非错误兜底 |
| run_simpleGreeting_returnsAnswer | 简单问候，断言有回复 |
| run_emptyOrNullMessage_returnsFallbackWithoutThrow | 空/null 消息不抛异常，可返回兜底文案 |
| postAgentSync_returnsJsonWithContent | POST /ai/agent 同步，断言 JSON 结构及 content 存在 |
| getAgentSync_returnsJsonWithContent | GET /ai/agent?message=...&stream=false，断言 success/content/chatId |
| getAgentWithoutMessage_returnsBadRequest | 缺少 message 时返回 400 |

## 若 HTTP 测试 404

若应用配置了 `server.servlet.context-path: /api`，而 MockMvc 请求未带该前缀导致 404，可在测试里对 MockMvc 设置 context path，或把请求路径改为 `/api/ai/agent`（视你当前 Spring Boot 版本对 MockMvc 的默认行为而定）。

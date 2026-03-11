# 记忆架构：按需检索（MCP Tool）

## 设计原则

- **不自动注入记忆**：每轮请求的 Prompt 中不再携带历史记忆，节省 Token 并减少干扰。
- **持久化仍自动**：每轮对话结束后，由 `MemoryPersistenceAdvisor` 自动写入三层（Working → Experiential → Long-Term）。
- **检索按需**：Agent 在需要回忆时，**显式调用** `retrieve_history` 工具，再根据返回内容作答。

## retrieve_history 工具

- **名称**：`retrieve_history`
- **参数**：
  - `conversation_id`：当前会话 ID（与当前对话使用的 conversation_id 一致）
  - `query`：检索意图或关键词（如「用户问过的专利号」「许可费用」），用于语义召回
- **返回**：短期上下文 + 中期事件摘要 + 长期事实（按 relevance 与 recency 重排后的若干条）

当用户问“刚才我说过哪个专利号？”或“根据我们之前的讨论给个建议”时，Agent 应主动调用 `retrieve_history(conversation_id, "用户提到的专利号"或"之前的讨论")`，再把工具返回的内容用于生成回复。

## 配置与 Bean

- 持久化：`memoryPersistenceAdvisor`（仅写不读）
- 工具：`MemoryRetrievalTool` 注册到 `allTools`，工具名 `retrieve_history`
- 配置项：`app.memory.working` / `experiential` / `long-term`（见 `application.yaml`）

## 与 MCP 的兼容

`retrieve_history` 以 Spring AI `@Tool` 形式实现，与现有工具一起注册到 Agent。若后续接入独立 MCP 服务，可将同一检索逻辑封装为 MCP Tool，供其他客户端复用。

# Agent 可观测：如何看清 Agent 的思考与执行

## 1. 日志里会看到什么

全能力 Agent（`doChatWithFullAgent` / `doChatWithFullAgentStream`）已接入 **AgentTraceAdvisor**，日志中会按请求/响应打印：

- **请求**：`[AgentTrace] Request | conversationId=... | userMessage(length=...) | preview=...`
- **响应**：`[AgentTrace] Response | text(length=...) | preview=...`
- **模型发起的工具调用**（若有）：`[AgentTrace] ToolCall | name=... | arguments=...`
- **元数据**（若有）：`[AgentTrace] Metadata | usage=...`、`model=...`

以上均为 **INFO** 级别，直接看控制台或日志文件即可。

## 2. 工具的真实执行（入参/返回/耗时）

若还要看到**每次工具被调用时的入参、返回内容摘要和耗时**，请开启工具追踪：

```yaml
# application.yaml 或 application-xxx.yaml
app:
  agent:
    trace-tools: true
```

开启后会出现：

- `[AgentTrace.Tool] name=... | input(length=...) | preview=...`
- `[AgentTrace.Tool] name=... | result(length=...) | preview=... | elapsedMs=...`

这样就能区分「模型决定调什么」（ToolCall）和「实际执行与返回」（Tool 日志）。

## 3. 如何快速定位一次对话的完整链路

1. 用 `conversationId` 或请求时间在日志里搜一次对话。
2. 按时间顺序看：
   - 先出现 `[AgentTrace] Request` → 用户输入；
   - 若有 `[AgentTrace] ToolCall` → 模型在本轮决定调用的工具及参数；
   - 若有 `[AgentTrace.Tool]` → 对应工具的真实执行与结果；
   - 最后出现 `[AgentTrace] Response` → 本轮最终回复。

多轮工具调用时，会多次出现 ToolCall / Tool 执行，再跟一条 Response。

## 4. 关闭或降低日志量

- 关闭 AgentTrace 日志：在 logback/log4j 中把 `com.inovationbehavior.backend.ai.advisor.AgentTraceAdvisor` 设为 `WARN`。
- 关闭工具执行日志：设 `app.agent.trace-tools: false`，并把 `com.inovationbehavior.backend.ai.config.TracingToolCallbackProvider` 设为 `WARN`（若仍想保留 Bean 但不打日志）。

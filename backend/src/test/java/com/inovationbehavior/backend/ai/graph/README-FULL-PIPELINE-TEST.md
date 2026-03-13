# Agent 全链路测试说明

## 测试类

- **AgentFullPipelineTest**：覆盖 P&E 图全链路与多种查询形态，用于验证 Planner、Executor、CheckResult、AfterExpert、Replan、Synthesize 及工具调用路径。

## 覆盖的节点（Agent）

| 测试方法 | Planner | Dispatch | Executor | CheckResult | AfterExpert | Replan | Synthesize |
|----------|---------|----------|----------|-------------|-------------|--------|------------|
| fullPipeline_multiStepQuery_... | ✓ | ✓ | ✓(多步) | ✓ | ✓ | 可能 | ✓ |
| fullPipeline_patentQuery_... | ✓ | ✓ | ✓ | ✓ | ✓ | 可能 | ✓ |
| fullPipeline_simpleGreeting_... | ✓ | ✓ | - | - | - | - | ✓ |
| fullPipeline_retrievalAndWebSearch_... | ✓ | ✓ | ✓ | ✓ | ✓ | 可能 | ✓ |
| fullPipeline_viaHttpPost_... | ✓ | ✓ | ✓ | ✓ | ✓ | 可能 | ✓ |

## 覆盖的工具（由模型在 ReAct 中按需调用）

- **PatentDetailTool** / **PatentHeatTool**：多步或专利类查询时可能被调用。
- **RAG**（检索增强）：Executor 执行 retrieval 任务时使用。
- **WebSearchTool**：检索类或“搜索/查一下”类问题时可能被调用。
- **MemoryRetrievalTool**（retrieve_history）：多轮对话或“回忆”类问题时可能被调用。
- **UserIdentityTool**：建议类任务时可能被调用。

工具是否被调用取决于模型决策，测试仅保证全链路执行成功；若需在日志中查看工具调用，请设置 `app.agent.trace-tools: true`（如 application-local.yaml）。

## 运行方式

```bash
# 运行全链路测试（需配置 API Key、数据源等）
./mvnw test -Dtest=AgentFullPipelineTest

# 运行单个方法
./mvnw test -Dtest=AgentFullPipelineTest#fullPipeline_multiStepQuery_exercisesAllNodesAndReturnsAnswer
```

## 日志查看

运行测试时，控制台会输出图执行日志，包括：

- `[AgentGraph] ======== 图执行开始/结束 ========`：含 chatId、stepCount、planSize、stepResultsSize、finalAnswer 预览。
- `[AgentGraph.Planner]`：规划节点进入/离开、plan 内容。
- `[AgentGraph.Dispatch]`：当前 currentTask、nextNode。
- `[AgentGraph.Executor]`：当前 task、输出长度、stepCount、needReplan、elapsedMs。
- `[AgentGraph.CheckResult]`：stepResultsSize、remainingSize、lastResultPreview、environmentChanged。
- `[AgentGraph.AfterExpert]`：下一跳（replan / synthesize / dispatch）。
- `[AgentGraph.Replan]`：环境变化或结果不足时的重规划。
- `[AgentGraph.Synthesize]`：综合节点进入/离开、finalAnswer 预览。

若开启 `app.agent.trace-tools: true`，还可看到 `[AgentTrace.Tool]` 的 name、input、result 预览。

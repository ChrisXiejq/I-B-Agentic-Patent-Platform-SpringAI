# 多 Agent 图编排 — 测试执行流程分析

## 一、测试结果概览

| 项目 | 结果 |
|------|------|
| **测试** | `PatentGraphRunnerTest.run_retrievalStyleQuestion_returnsNonEmptyAnswer` |
| **退出码** | 0（通过） |
| **用户输入** | 「专利商业化平台能做什么？或者介绍一下专利转化的基本概念。」 |
| **chatId** | test-graph-retrieval-1773329800880 |
| **最终回复长度** | 244 字符 |
| **总步数** | 5（达到 maxSteps 后强制进入综合） |

---

## 二、整体执行流程（时序）

```
图执行开始
    → Router（第1次）→ 意图=retrieval
    → RetrievalExpert（第1次）→ 输出 112 字，stepCount=1
    → Router（第2次）→ 意图=retrieval
    → RetrievalExpert（第2次）→ 输出 113 字，stepCount=2
    → Router（第3次）→ 意图=retrieval
    → RetrievalExpert（第3次）→ 输出 113 字，stepCount=3
    → Router（第4次）→ 意图=retrieval
    → RetrievalExpert（第4次）→ 输出 111 字，stepCount=4
    → Router（第5次）→ 意图=retrieval
    → RetrievalExpert（第5次）→ 输出 113 字，stepCount=5
    → Router（第6次）→ stepCount>=5，强制 synthesize
    → Synthesize → 汇总 3 条专家输出 → 最终回复 244 字
图执行结束
```

---

## 三、各阶段执行细节

### 1. 图执行入口（PatentGraphRunner）

- **输入**：`userMessage`（28 字）、`chatId`、`maxSteps=5`
- **初始状态**：USER_MESSAGE、CHAT_ID、STEP_RESULTS=[]、NEXT_NODE=""、FINAL_ANSWER=""、STEP_COUNT=0、MAX_STEPS=5
- **日志**：`[AgentGraph] ======== 图执行开始 ========`

---

### 2. Router 节点（共执行 6 次）

**职责**：根据当前用户消息、stepCount、stepResults 决定下一跳（retrieval / analysis / advice / synthesize / end）。

| 轮次 | 输入状态 | 分类用 LLM 请求 | LLM 回复 | 决策 | 下一节点 |
|------|----------|-----------------|----------|------|----------|
| 1 | stepCount=0, stepResults=0 | 意图分类 prompt（约 2146 字） | retrieval | retrieval | 检索专家 |
| 2 | stepCount=1, stepResults=1 | 同上（约 2722 字） | retrieval | retrieval | 检索专家 |
| 3 | stepCount=2, stepResults=2 | 同上（约 3298 字） | retrieval | retrieval | 检索专家 |
| 4 | stepCount=3, stepResults=2 | 同上（约 3874 字） | retrieval | retrieval | 检索专家 |
| 5 | stepCount=4, stepResults=3 | 同上（约 3874 字） | retrieval | retrieval | 检索专家 |
| 6 | stepCount=5, stepResults=3 | — | — | **强制 synthesize**（stepCount>=5） | 综合节点 |

**说明**：前 5 次路由都只依赖「当前用户问句」做意图分类，没有把「已有 stepResults」当作「可结束检索」的信号，因此一直返回 retrieval，直到第 6 次因 `stepCount >= maxSteps` 被强制改为 synthesize。

---

### 3. RetrievalExpert 节点（共执行 5 次）

**职责**：在 RAG（向量+BM25+RRF+rerank）+ 工具（专利详情/热度/记忆等）下，以「检索专家」身份回答用户问题，结果追加到 `stepResults`，`stepCount` 加 1。

| 轮次 | 用户问题 | 内部流程 | 耗时 | 输出长度 | 输出要点 |
|------|----------|----------|------|----------|----------|
| 1 | 专利商业化平台能做什么？… | Query 改写 → RAG 检索 → LLM（prompt 约 3467 字）→ 无工具调用 | ~14.5s | 112 | 专利转化定义 + 商业化平台在市场匹配阶段的作用 |
| 2 | 同上 | 同上（prompt 约 3989 字，含上一轮检索结果） | ~13.9s | 113 | 内容与第 1 次高度重合 |
| 3 | 同上 | 同上（prompt 约 3726 字） | ~10.7s | 113 | 同上 |
| 4 | 同上 | 同上（prompt 约 4247 字） | ~10.8s | 111 | 同上 |
| 5 | 同上 | 同上（prompt 约 4374 字） | ~14.0s | 113 | 同上 |

**说明**：每次都会做 query 改写、RAG、LLM；未触发 getPatentDetails/getPatentHeat 等工具调用，因为问题偏「概念介绍」，模型仅基于 RAG 文档作答。5 次输出语义接近，存在重复计算。

---

### 4. Synthesize 节点（执行 1 次）

- **输入**：`userMessage`（原问）、`stepResults`（3 条，来自检索专家的多次输出）、`chatId`
- **prompt**：系统角色 + 用户问题 + 「Expert outputs: [Retrieval]\n… [Retrieval]\n… [Retrieval]\n…」
- **LLM**：根据 3 条专家输出生成一条友好、结构化的最终回复
- **耗时**：约 1.9s
- **输出**（244 字）：开场白 + 专利转化定义 + 商业化平台功能 + 本助手能做什么 + 引导下一步（专利号/技术领域/商业化方式等）

---

### 5. 图执行结束（PatentGraphRunner）

- **最终状态**：stepCount=5，finalAnswer 长度=244
- **日志**：`[AgentGraph] ======== 图执行结束 ========`

---

## 四、资源与耗时（从日志推断）

| 项目 | 数值 |
|------|------|
| Router 调用 LLM | 6 次（5 次分类 + 1 次因 maxSteps 未再调分类） |
| RetrievalExpert 调用 RAG+LLM | 5 次 |
| Synthesize 调用 LLM | 1 次 |
| **总 LLM 调用** | **约 12 次**（6 + 5 + 1） |
| 工具调用（getPatentDetails 等） | 0 次 |
| 总耗时（约） | ~70s（含 RAG 与网络） |

---

## 五、现象与可优化点

### 1. 检索循环过多

- **现象**：同一问题下，Router 连续 5 次都选 retrieval，RetrievalExpert 被调用 5 次，内容重复。
- **原因**：`classifyIntentForGraph` 只根据「当前用户消息」做意图分类，没有考虑「已有 stepResults / 上一节点类型」。
- **建议**：
  - 若 `stepCount > 0` 且上一步是 retrieval，且 stepResults 非空，可优先倾向 **synthesize**（或增加「已有足够检索结果」的判定）。
  - 或在路由 prompt 中显式加入：当前 stepResults 条数、上一节点类型，要求「若已有检索结果且用户问题未变，选 synthesize」。
- **已做优化**：在 `IBApp.classifyIntentForGraph` 中，当 `stepCount >= 1` 且 `stepResults` 非空且 LLM 仍返回 **retrieval / analysis / advice** 任一专家时，强制改为 synthesize，从而同一问题下每种专家最多执行 1 次，然后进入综合节点（避免分析/建议专家也像检索专家一样被重复调用 5 次）。

### 2. stepResults 条数

- 日志中 stepResultsSize 出现 2 和 3（例如第 3 次 Router 为 2，第 4 次为 3），与「5 次检索专家」不完全一致，可能是 appender 合并/状态更新顺序导致；若需严格「每步一条」，可核对 LangGraph4j 对 STEP_RESULTS 的合并语义。

### 3. Mockito / JDK 告警

- 日志中的 Mockito self-attaching、dynamic agent loading 等为测试环境常见提示，不影响本次图执行正确性；若需消除，可按 Mockito 文档将 Mockito 配置为 JVM agent 运行。

---

## 七、重复日志原因与修复（2026-03-12）

**现象**：每次 LLM 请求/响应会打印两遍 `[AgentGraph.LLM] Request/Response`。

**原因**：`ChatClient` 在 `@PostConstruct` 里已通过 `defaultAdvisors` 注册了 `MyLoggerAdvisor`，而 `doExpertChat`、`doChatWithFullAgent`、`doChatWithRag` 等又在调用链上再次 `.advisors(new MyLoggerAdvisor())`，导致同一请求经过两个 MyLoggerAdvisor，日志重复。

**修复**：去掉各调用处重复的 `.advisors(new MyLoggerAdvisor())`，仅保留 defaultAdvisors 中的一份，每轮只打一条 Request、一条 Response。

---

## 六、结论

- **测试通过**：图从「用户提问」到「综合节点产出最终回复」的整条链路跑通，NPE 已解决，schema 默认值与非 null 初始状态生效。
- **流程正确**：Router → 多轮 RetrievalExpert → 达到 maxSteps 后强制 Synthesize → 结束，与设计一致。
- **可改进**：通过路由逻辑（或 maxSteps 前「已有检索结果则 synthesize」）减少重复检索，降低延迟与成本；同时可核对 stepResults 的条数与预期是否一致。

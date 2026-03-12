# 专利商业化平台 Backend — 技术文档（面试向超详版）

本文档覆盖项目技术选型、架构、数据流转、模块设计理由、边界情况与面试中可能被问到的细节。阅读顺序可按章节线性阅读，或按「面试问答」索引跳转。

---

## 目录

1. [项目概述与业务定位](#1-项目概述与业务定位)
2. [技术选型与选型理由](#2-技术选型与选型理由)
3. [整体架构与模块划分](#3-整体架构与模块划分)
4. [请求全链路与数据流转](#4-请求全链路与数据流转)
5. [多 Agent 图编排（LangGraph4j）](#5-多-agent-图编排langgraph4j)
6. [RAG 检索增强](#6-rag-检索增强)
7. [工具体系](#7-工具体系)
8. [记忆系统（三层）](#8-记忆系统三层)
9. [配置体系](#9-配置体系)
10. [API 设计](#10-api-设计)
11. [边界情况与设计决策](#11-边界情况与设计决策)
12. [测试与评测](#12-测试与评测)
13. [面试向问答摘要](#13-面试向问答摘要)

---

## 1. 项目概述与业务定位

- **项目名称**：Innovation Behavior — 专利商业化平台 Backend（Spring AI 技术栈）。
- **业务目标**：为专利成果转化场景提供智能对话能力，包括：专利检索与详情/热度查询、专利价值与技术分析、商业化建议（许可/转让/合作）、知识库问答（RAG）、以及多轮对话与记忆。
- **技术定位**：基于 Spring Boot 3 + Spring AI 的 Java 后端，采用多 Agent 图编排（LangGraph4j）、混合 RAG、三层记忆与丰富工具调用，兼顾同步/流式、可观测与可评测。

---

## 2. 技术选型与选型理由

### 2.1 为什么用 Spring Boot 3 而不是其他 Java 框架？

- **统一生态**：与 Spring AI、Spring Data、Spring Web 同源，依赖管理（BOM）、自动配置、条件 Bean 一致，减少版本冲突与集成成本。
- **可运维性**：actuator、profile（如 `local`）、外部化配置（yaml + 环境变量）便于本地与部署环境切换；与现有 MyBatis、Redis、腾讯云 COS 等集成成熟。
- **版本选择**：Java 21+、Jakarta 命名空间与 Spring Boot 3.4 保持一致，便于长期维护。

### 2.2 为什么选 Spring AI 而不是直接用 OpenAI/Google SDK？

- **抽象统一**：`ChatModel`、`EmbeddingModel`、`VectorStore`、`DocumentRetriever` 等抽象可切换实现（如 chat 从 Gemini 换 OpenAI 只需改配置与 starter），避免业务代码写死某家 SDK。
- **Advisor 链**：请求/响应可插拔（RAG、记忆持久化、日志、违禁词、可观测），与 ChatClient 的 prompt().advisors().toolCallbacks() 链式调用契合，便于扩展。
- **RAG 模块化**：Spring AI 提供 QueryTransformer、DocumentRetriever、QueryAugmenter、RetrievalAugmentationAdvisor 等模块，便于实现多路召回、RRF、Rerank 与空上下文策略，而不从零造轮子。
- **版本说明**：当前使用 `spring-ai-bom` 1.1.0-M2（里程碑版），生产可考虑稳定版；选型理由不变。

### 2.3 为什么用 LangGraph4j 做多 Agent 编排？

- **图语义清晰**：专利场景需要「路由 → 检索/分析/建议专家 → 综合」的多步、有条件分支，用有向图 + 条件边表达比手写状态机更直观、可维护。
- **状态与节点解耦**：共享状态（PatentGraphState）由框架在节点间传递，节点只读/写自己关心的字段（如 Router 写 nextNode，专家写 stepResults/stepCount），避免全局可变状态散落。
- **与 Java 生态一致**：LangGraph4j 是 JVM 上的实现，与 Spring 注入、CompletableFuture 兼容；条件边返回 `CompletableFuture<String>` 映射到下一节点名，编译期即可检查图结构。
- **递归与上限**：`CompileConfig.recursionLimit(30)` 防止异常情况下无限循环；业务上再通过 `maxSteps`（如 5）在路由层强制进入综合节点。

### 2.4 为什么 Chat 用 Google Gemini、Embedding 用 OpenAI？

- **Chat（Gemini 2.5 Flash）**：在成本与延迟之间折中，支持较长上下文与工具调用；与 Spring AI 的 `spring-ai-starter-model-google-genai` 集成简单，配置 `api-key` 与 `model` 即可。
- **Embedding（OpenAI text-embedding-3-small）**：维度 1536，与 PgVector 的 HNSW、COSINE_DISTANCE 搭配常见；语义检索质量稳定，且与 RAG 文档量级（数十到数百 chunk）匹配。若全栈希望统一一家厂商，可改为 Gemini Embedding，需同步改 dimensions 与索引。

### 2.5 为什么向量库用 PgVector（PostgreSQL）而不是独立向量库？

- **运维简化**：与业务共用数据库（如 Supabase PostgreSQL），无需单独部署 Milvus/Weaviate 等；Supabase 提供 PgVector 扩展与连接池，适合中小规模文档与记忆向量。
- **事务与一致性**：向量与业务表可在同一库中，若未来需要“写入业务数据同时写向量”可放在同一事务；当前 RAG 与记忆表（vector_store、agent_memory、agent_experiential_memory）均使用同一 `pgvectorDataSource`。
- **Spring AI 支持**：`spring-ai-pgvector-store` 提供 `PgVectorStore`，与 `VectorStoreDocumentRetriever`、记忆的 `SearchRequest` 兼容；索引类型 HNSW、距离 COSINE_DISTANCE、dimensions 1536 与 OpenAI 嵌入一致。

### 2.6 为什么 RAG 用「向量 + BM25」混合而不是纯向量？

- **关键词与语义互补**：用户常带专利号、专业术语（如「许可」「转让」）；BM25 对精确词匹配敏感，向量对同义与语义相似更敏感，双路召回再融合可提高 Recall。
- **RRF（Reciprocal Rank Fusion）**：多路排序列表通过 RRF 融合为一个排序，避免单一排序的偏差；再经 Embedding Rerank 取 finalTopK，在可控 token 内保留最相关文档。
- **配置可调**：`vector-top-k`、`bm25-top-k`、`final-top-k` 可按文档规模与延迟要求调整；当前 8+8→6 适合文档量级与上下文长度。

### 2.7 为什么业务库用 MyBatis 而不是 JPA？

- **历史与团队习惯**：专利、问卷、情报、新闻等已有 MyBatis Mapper 与 SQL 设计；保持一致性，且复杂查询（如动态条件）用 XML/SqlProvider 更直观。
- **与 AI 模块解耦**：AI 相关数据（向量、记忆）走 Spring Data / PgVector / JDBC；业务 CRUD 走 MyBatis，职责清晰。

### 2.8 为什么需要单独 PgVector 数据源（与主库 MySQL 分离）？

- **数据库类型不同**：主业务为 MySQL；向量与记忆使用 PostgreSQL + PgVector 扩展，必须独立连接。
- **连接池隔离**：向量与 RAG 的请求可能较重（检索、嵌入），独立 Hikari 池（如 `pgvector-pool`）避免拖垮主库连接池。
- **条件装配**：`@ConditionalOnProperty(name = "spring.ai.vectorstore.pgvector.url")` 保证未配置 PgVector 时不影响主应用启动（如纯业务环境）。

---

## 3. 整体架构与模块划分

### 3.1 顶层目录

| 目录 | 用途 |
|------|------|
| `src/main/java` | 业务与 AI 源码，根包 `com.inovationbehavior.backend` |
| `src/main/resources` | `application.yaml`、`application-local*`、RAG 文档 `documents/**/*.md`、评测用例 `eval/*.json` |
| `scripts` | RAGAS 等评测脚本（Python），与 Java 导出结果配合 |
| `docs` | 记忆/可观测/Web Search 说明、图执行流程、本技术文档 |

### 3.2 包与职责（一句话）

- **backend**：`BackendApplication` 启动类。
- **config**：PgVector 数据源、RestTemplate、违禁词/AppAi 配置等全局 Bean。
- **controller**：REST 入口（AI、健康、专利、问卷、情报、新闻、经济、课程、文章等）；全局异常 `GlobalExceptionHandler`。
- **ai.app**：`IBApp` — 统一 AI 门面（对话、RAG、工具、全能力 Agent、图编排入口、路由/专家/综合逻辑）。
- **ai.graph**：图状态 `PatentGraphState`、运行器 `PatentGraphRunner`、图配置 `PatentAgentGraphConfig`。
- **ai.graph.nodes**：`RouterNode`、`RetrievalExpertNode`、`AnalysisExpertNode`、`AdviceExpertNode`、`SynthesizeNode`。
- **ai.advisor**：`BannedWordsAdvisor`、`MyLoggerAdvisor`、`AgentTraceAdvisor` 等对话链顾问。
- **ai.rag.***：文档加载、分块、混合检索（向量+BM25+RRF+Rerank）、检索前后增强（QueryAugmenter、空上下文策略）。
- **ai.tools**：各类工具实现与 `ToolRegistration` 统一注册。
- **ai.memory**：三层记忆（Working / Experiential / Long-Term）、持久化 Advisor、`MemoryRetrievalTool`、向量库配置。
- **ai.eval**：RAG 评测（检索指标、Ragas、忠实度等）。
- **mapper / service / model**：MyBatis 与业务服务层，与 AI 解耦。

### 3.3 依赖关系（简化）

- **Controller** → **IBApp** →（图）**PatentGraphRunner** → **CompiledGraph** → 各 **Node**；Node 依赖 **IBApp**（@Lazy 打破循环）。
- **IBApp** 依赖 **ChatClient**、**hybridRagAdvisor** / **retrievalExpertRagAdvisor**、**allTools**、**agentTraceAdvisor**、**memoryPersistenceAdvisor** 等。
- **RAG**：`HybridRagConfig` 组装 `DocumentRetriever`、`ContextualQueryAugmenter`，产出 `hybridRagAdvisor` 与 `retrievalExpertRagAdvisor`；文档来源 `DocumentLoader` + `RagDocumentCorpus`，向量侧 `PgVectorVectorStoreConfig` 增量同步 chunk。

---

## 4. 请求全链路与数据流转

### 4.1 主入口：POST/GET /api/ai/agent（stream=false）

1. **AiController** 接收 `message`、`chatId`（可选）、`stream=false`。
2. **ibApp.doChatWithMultiAgentOrFull(message, chatId)**：若存在 **PatentGraphRunner** Bean，则走图；否则走单 Agent 全能力 **doChatWithFullAgent**。
3. **图路径**：**PatentGraphRunner.run(message, chatId)** 构造 **initialState**（见下节），调用 **patentAgentGraph.invoke(initialState, config)**，从最终状态取 **finalAnswer** 返回。
4. **单 Agent 路径**：**doChatWithFullAgent** 使用 queryRewriter（可选）、ChatClient + defaultAdvisors、**hybridRagAdvisor**、**allTools**、**memoryPersistenceAdvisor**、**agentTraceAdvisor**，一次 call 得到回复。

### 4.2 图内数据流转（状态驱动）

- **初始状态**：userMessage、chatId、stepResults=[]、nextNode=""、finalAnswer=""、stepCount=0、maxSteps=5（或配置值）。
- **START → router**：RouterNode 读 state，调 **classifyIntentForGraph**，写 **nextNode**（retrieval|analysis|advice|synthesize|end）。
- **条件边**：根据 nextNode 跳转对应专家或 synthesize；**end** 也映射到 **synthesize**（生成告别语）。
- **专家节点**：读 userMessage、chatId，调 **doExpertChat(..., expertPrompt)**（内部：queryRewriter + RAG Advisor + allTools + 记忆等），得到文本；写 **STEP_RESULTS**（appender 追加一条）、**STEP_COUNT**+1。
- **专家 → router**：再次进入 Router，此时 stepCount≥1、stepResults 非空；路由逻辑可强制 **synthesize**（防同专家重复），或继续分类直到 **stepCount≥maxSteps** 强制 synthesize。
- **synthesize → END**：SynthesizeNode 读 stepResults、userMessage，调 **synthesizeAnswer**，写 **finalAnswer**；图结束。
- **Runner** 从 finalState 取 finalAnswer，若为空则返回兜底文案；异常时 catch 并返回友好提示。

---

## 5. 多 Agent 图编排（LangGraph4j）

### 5.1 为什么状态要定义 SCHEMA，且默认值不能为 null？

- LangGraph4j 在 **invoke** 时会用 schema 的 **Channel** 定义与传入的 **initialState** 做合并；若 schema 里某 channel 的默认值为 **null**，框架内部 **initialDataFromSchema** 可能触发 NPE。因此所有 Channel 的默认值均为非 null：字符串用 `Channels.base(() -> "")`，数字用 `Channels.base(() -> 0)` 或 `() -> 5`，列表用 `Channels.appender(ArrayList::new)`。

### 5.2 为什么 STEP_RESULTS 用 appender 而不是 base？

- **appender** 表示「追加」语义：每次专家节点返回一个 Map 包含 `STEP_RESULTS` 与一条新字符串时，框架会把该条**追加**到当前列表，而不是覆盖。这样多步专家（若未来允许多步）或单步专家的输出会累积，供 Synthesize 一次性汇总。若用 base，每次会覆盖整份列表，无法实现多专家输出合并。

### 5.3 为什么 Runner 的 initialState 必须包含 schema 中所有 key？

- 与上类似：框架合并状态时若缺少某个 key，可能按 schema 默认值补全；若默认值仍为 null 或合并逻辑未覆盖，会 NPE。因此 **PatentGraphRunner** 显式构造包含 **USER_MESSAGE、CHAT_ID、STEP_RESULTS、NEXT_NODE、FINAL_ANSWER、STEP_COUNT、MAX_STEPS** 的 Map，且 **STEP_RESULTS** 为 `new ArrayList<>()`，**NEXT_NODE/FINAL_ANSWER** 为 `""`，保证与 schema 一致且无 null。

### 5.4 条件边为什么返回节点名而不是节点对象？

- LangGraph4j 的 **addConditionalEdges** 接受一个返回 **CompletableFuture&lt;String&gt;** 的 lambda，String 即**下一节点名**（与 addNode 时注册的名字一致）。这样图结构是「router → retrievalExpert | analysisExpert | adviceExpert | synthesize」，与 **Map.of("retrieval","retrievalExpert", ...)** 的映射一致；**end** 映射到 **synthesize** 以便统一生成结束语。

### 5.5 为什么专家节点后都回到 router 而不是直接到 synthesize？

- 设计上允许「多步专家」：例如先 retrieval 再 analysis 再 advice，由 router 根据当前 stepCount 与 stepResults 决定下一步。同时通过 **stepCount≥maxSteps** 与「已有 stepResults 时强制 synthesize」两条规则防止死循环；实际效果多为「单次专家 → 强制 synthesize」。

### 5.6 为什么节点里 IBApp 用 @Lazy 注入？

- **循环依赖**：IBApp 依赖 PatentGraphRunner，Runner 依赖 CompiledGraph，Graph 依赖各 Node，Node 又依赖 IBApp。Spring 若在创建 Bean 时全部立即解析会报循环依赖。对 Node 的 IBApp 参数加 **@Lazy** 后，注入的是代理，首次调用时才初始化 IBApp，从而打破环。

### 5.7 图编译的 recursionLimit 与业务的 maxSteps 区别？

- **recursionLimit(30)**：图框架层最大递归/迭代次数，防止图结构异常导致无限循环。
- **maxSteps**：业务语义「最多执行多少步专家」；在 **classifyIntentForGraph** 里当 stepCount≥maxSteps 时强制返回 **synthesize**，从而结束专家循环。两者配合：先由业务 maxSteps 收口，再由 recursionLimit 兜底。

### 5.8 关键代码引用（便于面试时对照）

| 设计点 | 文件与位置 | 说明 |
|--------|------------|------|
| 状态 SCHEMA 与默认值 | `PatentGraphState.java` 第 31–40 行 | `Channels.base(() -> "")` / `Channels.appender(ArrayList::new)`，无 null |
| 初始状态构造 | `PatentGraphRunner.java` 第 34–43 行 | 显式包含全部 key，STEP_RESULTS 为 `new ArrayList<>()` |
| 条件边与 end→synthesize | `PatentAgentGraphConfig.java` 第 47–53 行 | `state.nextNode().orElse("synthesize")`，Map 中 `"end","synthesize"` |
| 专家回 router | `PatentAgentGraphConfig.java` 第 54–56 行 | retrievalExpert/analysisExpert/adviceExpert 均 addEdge 回 "router" |
| 问候直达 synthesize | `IBApp.java` 第 321–323 行 | `stepCount==0 && isSimpleGreetingOrIntro(userMessage)` 直接 return "synthesize" |
| 已有 stepResults 防环 | `IBApp.java` 第 351–356 行 | stepCount≥1 且 decision 为 retrieval/analysis/advice 时改为 "synthesize" |
| 检索专家用专用 RAG | `IBApp.java` 第 296–298 行 | `systemPrompt.contains("retrieval")` 时用 retrievalExpertRagAdvisor |
| 综合节点空 stepResults | `IBApp.java` 第 378–380 行 | context = "No prior expert outputs."，用于自我介绍等 |

### 5.9 图执行流程（文字 + 可转 Mermaid）

```
START
  → router（读 userMessage/chatId/stepCount/maxSteps/stepResults）
  → classifyIntentForGraph（可能短路：问候→synthesize；stepCount≥maxSteps→synthesize；已有结果且为专家意图→synthesize）
  → 条件边：retrieval→retrievalExpert | analysis→analysisExpert | advice→adviceExpert | synthesize→synthesize | end→synthesize
  → 若进入专家：doExpertChat（RAG + 工具 + 记忆）→ 写 STEP_RESULTS（追加一条）、STEP_COUNT+1 → 回到 router
  → 若进入 synthesize：synthesizeAnswer（stepResults + userMessage）→ 写 FINAL_ANSWER → END
Runner 从 finalState 取 finalAnswer 返回；异常则返回兜底文案。
```

---

## 6. RAG 检索增强

### 6.1 文档从哪里来、如何分块？

- **DocumentLoader** 从 **app.rag.documents.path**（默认 `classpath*:documents/**/*.md`）加载 Markdown；经 **ChunkSplitter** 分块。分块策略由 **RagSplitterConfig** 决定：**use-semantic=true** 时用语义分块 + Token 回退（max-chunk-tokens、overlap-tokens），否则用递归字符分块（chunk-size、chunk-overlap）。每个 chunk 生成 **chunk_key**（如 sha256）用于增量同步与去重。

### 6.2 向量与 BM25 的语料是否一致？

- 是。**RagDocumentCorpus** 持有 **DocumentLoader.loadDocumentsForRag()** 的结果；**PgVectorVectorStoreConfig** 启动时用该 corpus 向 **vector_store** 表做增量同步（存在则跳过）；**BM25DocumentRetriever** 直接使用同一 corpus 在内存中建 BM25 索引。这样两路检索的文档源一致，仅检索算法不同。

### 6.3 RRF 融合与 Rerank 的顺序？

- **HybridDocumentRetriever.retrieve**：先 **vectorRetriever.retrieve** 与 **bm25Retriever.retrieve** 得到两个 List；再用 **RrfFusion.fuse(rankedLists)** 得到融合排序；最后 **reranker.rerank(query, fused)** 用 query-doc 向量相似度重排并取 **finalTopK**。这样既利用双路召回，又用向量相似度做最终精排，控制送入 LLM 的 token 量。

### 6.4 空上下文时为什么有两种策略（默认 vs 检索专家）？

- **默认 ContextualQueryAugmenter**：**allowEmptyContext=false**，空上下文时用 **emptyContextPromptTemplate** 替换用户消息为固定话术「知识库无相关内容，请提供专利号或描述需求」，模型直接回复该话术，**不会触发工具调用**。
- **检索专家专用**（**retrievalExpertRagAdvisor**）：使用 **createInstanceForRetrievalExpertWithWebFallback()**，**allowEmptyContext=true**，空上下文时模板为「知识库无相关文档，用户问题：{query}，你必须先调用 searchWeb 再回答」。这样在用户问「字节跳动介绍」等知识库外问题时，模型会先调用 **searchWeb** 再作答，而不是直接说「不知道」。

### 6.5 检索专家为什么单独一个 RAG Advisor？

- 检索专家需要「无结果时引导上网搜索」；其他对话（如分析专家、全能力单 Agent）仍希望无结果时统一回复「知识库无相关内容」。因此 **HybridRagConfig** 提供两个 Bean：**hybridRagAdvisor**（默认）、**retrievalExpertRagAdvisor**（空上下文→searchWeb）；**doExpertChat** 在 systemPrompt 包含 "retrieval" 时选用 **retrievalExpertRagAdvisor**，否则用 **hybridRagAdvisor**。

### 6.6 QueryRewriter 在链路的哪里生效？

- 在 **doExpertChat**、**doChatWithFullAgent** 等入口处，对**用户原始 message** 先做 **queryRewriter.doQueryRewrite(message)**（若 Bean 存在），再传入 ChatClient。改写目的是让查询更利于向量/关键词检索（如扩展同义词、规范化表述），与 RAG 的 **RewriteQueryTransformer** 可配合或复用。

### 6.7 RAG 相关代码位置

- **混合检索**：`HybridDocumentRetriever`（向量 + BM25 → RrfFusion → EmbeddingReranker，finalTopK）。
- **检索专家空上下文策略**：`ContextualQueryAugmenterFactory.createInstanceForRetrievalExpertWithWebFallback()`，allowEmptyContext=true，模板含「必须调用 searchWeb」。
- **两套 Advisor**：`HybridRagConfig` 中 `hybridRagAdvisor`（默认）、`retrievalExpertRagAdvisor`（检索专家专用）。

---

## 7. 工具体系

### 7.1 工具如何注册、如何被模型调用？

- **ToolRegistration** 中 **allTools** Bean 将 **PatentDetailTool、PatentHeatTool、UserIdentityTool、WebSearchTool、MemoryRetrievalTool**（可选）以及 **FileOperationTool、WebScrapingTool、ResourceDownloadTool、TerminalOperationTool、PDFGenerationTool、TerminateTool** 等通过 **ToolCallbacks.from(...)** 转为 **ToolCallback[]**。ChatClient 在 **.toolCallbacks(allTools)** 后，每次 LLM 请求会把工具 schema 传给模型，模型可返回 tool_calls；框架执行对应方法并把结果塞回对话，形成 ReAct 式多步调用。

### 7.2 WebSearchTool 的 API Key 从哪里来、未配置会怎样？

- **@Value("${search-api.api-key:}")** 注入，支持环境变量 **SEARCH_API_KEY**（在 application.yaml 中 `search-api.api-key: "${SEARCH_API_KEY:}"`）。未配置时 apiKey 为空，请求 SearchAPI.io 会失败，工具返回 "Error searching Baidu: ..."；业务上可通过配置或条件注册避免在无 Key 时注册该工具（当前实现为始终注册，依赖文档说明配置）。

### 7.3 为什么需要 TerminateTool？

- 在 ReAct/多步工具调用场景中，模型在「任务已满足或无法继续」时应主动结束，而不是一直尝试调用其他工具。**TerminateTool** 提供给模型一个显式「结束对话」的入口，便于流式或长会话控制。

### 7.4 TracingToolCallbackProvider 的作用？

- 当 **app.agent.trace-tools=true** 时，通过 **TracingToolCallbackProvider** 包装工具调用，在每次工具执行前后打印 **name、input、result** 等，便于调试与面试时说明「某次请求调用了哪些工具、参数与返回是什么」。日志前缀如 **[AgentTrace.Tool]**。

### 7.5 各工具职责一览（面试可逐一说）

- **PatentDetailTool / PatentHeatTool**：按专利号查详情与热度，对接平台数据或 mock。
- **UserIdentityTool**：按邀请码/问卷获取用户身份，供建议专家个性化建议。
- **WebSearchTool**：调用 SearchAPI.io（Baidu 等），需 `search-api.api-key`；知识库外或检索专家空上下文时由模型调用。
- **MemoryRetrievalTool**：按 conversation_id + query 检索三层记忆，实现「按需回忆」。
- **FileOperationTool / WebScrapingTool / ResourceDownloadTool**：文件、网页抓取、资源下载，扩展信息获取。
- **TerminalOperationTool**：受限命令行（慎用，权限与安全需控制）。
- **PDFGenerationTool**：生成 PDF 报告。
- **TerminateTool**：显式结束对话，便于流式或长会话控制。

---

## 8. 记忆系统（三层）

### 8.1 三层分别是什么、存在哪里？

- **Working（短期）**：内存、滑动窗口（如 window-size=10）、按重要度剪枝（prune-threshold）；超出 max-total-chars 或 turns-to-compress 时生成摘要写入 Experiential。不落库。
- **Experiential（中期）**：事件级摘要，存 **agent_experiential_memory**（PgVector）；带 decay 权重、top-k、similarity-threshold。
- **Long-Term（长期）**：语义事实，存 **agent_memory**（PgVector）；支持 NLI 冲突检测、原子更新（Recall → Conflict Check → Atomic Update）、可选原子事实；inject-top-k 等控制 retrieve 时注入条数。

### 8.2 记忆是否自动注入到 prompt？

- **不自动注入**。每轮结束后 **MemoryPersistenceAdvisor** 负责**写入**三层（Working → 溢出写 Experiential/Long-Term）；**读取**由 Agent 在需要时显式调用 **MemoryRetrievalTool**（retrieve_history(conversation_id, query)）完成，这样控制 token 与延迟，且模型可「按需回忆」而不是每轮都带全量历史。

### 8.3 重要性评分与剪枝？

- Working 层对每条消息做**重要性评分**（专利号权重、关键词权重、长度因子等，见 app.memory.importance）；低于 **prune-threshold** 的轮次（如「好的」「谢谢」）可被剔除，保留高价值轮次，延缓窗口溢出。

### 8.4 记忆相关配置与代码

- **MultiLevelMemoryConfig**：装配 Working / Experiential / Long-Term 的 window、top-k、threshold。
- **MemoryPersistenceAdvisor**：请求后写入三层（Working 溢出→Experiential/Long-Term）；不自动把历史注入 prompt。
- **MemoryRetrievalTool**：可选注册到 allTools，模型在需要时调用 retrieve_history(conversation_id, query)。

---

## 9. 配置体系

### 9.1 主配置与 Profile

- **application.yaml**：通用配置（数据源占位、Spring AI、PgVector、app.\* 业务配置、logging）。**spring.profiles.active** 默认 **local**，加载 **application-local.yaml**（不提交，.gitignore）；**application-local.yaml.example** 为模板，供复制后填入密钥。
- 敏感信息通过 **环境变量** 注入（如 GOOGLE_AI_API_KEY、OPENAI_API_KEY、SEARCH_API_KEY、SUPABASE_DB_PASSWORD），避免密钥进库。

### 9.2 与面试相关的关键配置项

- **app.agent.graph.max-steps**：图内专家最大步数，超过后强制 synthesize。
- **app.agent.trace-tools**：是否打印工具调用详情。
- **app.rag.hybrid.\***：vector-top-k、bm25-top-k、final-top-k。
- **app.rag.splitter.use-semantic**：是否语义分块。
- **app.memory.working/experiential/long-term.\***：各层窗口、top-k、阈值等。
- **search-api.api-key**：网页搜索 Key，缺则 searchWeb 报错。
- **spring.ai.vectorstore.pgvector.url**：PgVector 数据源；缺则 PgVector 相关 Bean 不装配，RAG/记忆向量不可用。

### 9.3 application.yaml 结构（节选）

- **spring.datasource**：主库 MySQL（业务）。
- **spring.ai**：chat/embedding 的 provider、model、api-key 占位；vectorstore.pgvector 的 url、dimensions、index-type 等。
- **app.ai**：banned-words 列表。
- **app.memory**：working（window-size、prune-threshold、max-total-chars）、experiential、long-term 的 top-k、threshold、表名等。
- **app.rag**：documents.path、splitter（use-semantic、chunk-size、overlap）、hybrid（vector-top-k、bm25-top-k、final-top-k）。
- **app.agent**：graph.max-steps、trace-tools。
- **app.eval**：rag 用例路径、ragas 样本数、导出路径。
- **logging**：level 按包配置（如 ai 包 DEBUG）。

---

## 10. API 设计

### 10.1 为什么 /ai/agent 同时支持 POST 和 GET？

- **POST**：请求体 `{ message, chatId?, stream? }`，适合前端表单或 JSON 客户端；**GET**：参数 query，便于浏览器或简单调试。两者在 **stream=false** 时都走 **doChatWithMultiAgentOrFull**，返回 JSON **AgentResponse**；stream=true 时返回 SSE。

### 10.2 流式与同步如何分流？

- **stream=false**（或 GET 的 stream 参数为 false）：同步执行图或单 Agent，得到完整 **content** 后一次性返回 **AgentResponse**。
- **stream=true**：不跑图，走 **doChatWithFullAgentStream**，返回 **SseEmitter**，逐 chunk 推送。图编排当前仅支持同步；若未来要流式图，需在图内节点支持流式输出并透传。

### 10.3 其他 AI 端点用途？

- **/ai/chat/sync**、**/ai/chat/sse** 等：仅对话或仅 RAG 等能力拆分，供不同前端或场景按需调用。
- **/ai/chat/rag**：纯 RAG 问答，无工具、无记忆持久化。
- **/ai/manus/chat**：Manus 单 Agent 流式（ReAct 工具调用），无 RAG、无图、无记忆，用于对比或轻量场景。

### 10.4 控制器与请求体（代码级）

- **AiController**：POST `/api/ai/agent` 请求体 `{ "message": string, "chatId?: string", "stream?: boolean" }`；GET 同路径用 query 参数。stream=false 时调用 `ibApp.doChatWithMultiAgentOrFull(message, chatId)`，返回 `AgentResponse`（content、usage 等）。流式时走 `doChatWithFullAgentStream`，返回 SseEmitter。

---

## 11. 边界情况与设计决策

### 11.1 初始状态 NPE（LangGraph4j）

- **现象**：invoke 时若 initialState 缺少 key 或 schema 默认值为 null，会 NPE。
- **处理**：schema 中所有 Channel 使用非 null 默认（如 ""、0、5、ArrayList::new）；Runner 构造的 initialState 显式包含全部 key 且值为非 null。

### 11.2 简单问候/自我介绍不走专家

- **现象**：「你好，介绍自己」被路由判成 retrieval，会进检索专家再综合，浪费一次 RAG+LLM。
- **处理**：在 **classifyIntentForGraph** 开头，若 **stepCount==0** 且 **isSimpleGreetingOrIntro(userMessage)** 为 true（短句且含「你好」「介绍自己」「你是谁」等），直接返回 **synthesize**，不再调分类 LLM；综合节点在 stepResults 为空时用 "No prior expert outputs." 生成自我介绍。

### 11.3 同类型专家重复执行（检索/分析/建议环）

- **现象**：路由一直返回 retrieval（或 analysis/advice），导致同一专家被调用多次直到 maxSteps。
- **处理**：当 **stepCount≥1 且 stepResults 非空** 且本次决策为 **retrieval|analysis|advice** 时，强制改为 **synthesize**，这样每种专家最多执行一次即进入综合。

### 11.4 空上下文时检索专家不调用 searchWeb

- **现象**：用户问「字节跳动介绍」时，RAG 空上下文被默认 augmenter 替换成「输出：知识库无相关内容」，模型直接回复，不触发工具。
- **处理**：检索专家使用 **retrievalExpertRagAdvisor**，空上下文模板改为「必须调用 searchWeb 再回答」，并 **allowEmptyContext=true**，保留用户问题占位符 {query}，使模型有机会发起 searchWeb。

### 11.5 分析专家在无专利号时回复「我不知道」

- **现象**：用户问「分析热水器专利价值」但未给专利号，RAG 只有通用文档，分析专家多次返回「我不知道」。
- **设计**：属于合理行为；通过「已有 stepResults 则强制 synthesize」避免分析专家被重复调用 5 次；综合节点将多条「我不知道」整理成「请提供专利号或细分领域」的友好回复。若需优化，可在分析专家 prompt 中明确「无专利号时先说明需专利号或领域再简要泛化分析」。

### 11.6 违禁词与 BannedWordsAdvisor

- **app.ai.banned-words** 配置英文违禁词（ spam、违法、诈骗、辱骂等）；**BannedWordsAdvisor** 在请求前检查用户消息，命中则抛 **BannedWordException**，由全局异常处理返回 400 或统一错误格式，避免违规内容进入模型。

---

## 12. 测试与评测

### 12.1 图与 Agent 的单元/集成测试

- **PatentGraphRunnerTest**：对 **PatentGraphRunner.run** 直接传 message/chatId，断言返回非空、非错误兜底；可覆盖检索类问题、简单问候、空消息等。依赖 Spring 容器（@SpringBootTest），加载真实 RAG、向量库、LLM，属集成测试。
- MockMvc 可对 **POST/GET /ai/agent** 做接口级测试（如缺 message 时 400）。

### 12.2 RAG 评测（Ragas 等）

- **app.eval.rag**：测试用例路径、RAGAS 样本数、导出路径；**RagEvalRunner** 等从 JSON 读用例，执行检索→生成→算分；Python 脚本 **scripts/ragas_eval.py** 可消费 Java 导出的输入做 Ragas 指标。详见 **scripts/README-ragas.md**。

---

## 13. 面试向问答摘要

- **为什么用 Spring AI？** 统一 Chat/Embedding/VectorStore/Advisor 抽象，RAG 模块化，便于换模型与扩展。
- **为什么用 LangGraph4j？** 图语义清晰，状态与节点解耦，支持条件边与递归上限，适合多步、多专家编排。
- **图状态为什么不能有 null 默认值？** 框架合并状态时会用 schema 默认值，null 会导致 NPE。
- **为什么 STEP_RESULTS 用 appender？** 多步专家输出需累积成列表，供综合节点一次性汇总。
- **为什么检索专家要单独 RAG Advisor？** 空上下文时需引导调用 searchWeb，其他场景仍用「知识库无相关内容」话术。
- **为什么记忆不自动注入？** 按需通过 retrieve_history 调用，控制 token 与延迟，模型可主动「回忆」。
- **为什么「你好介绍自己」直接 synthesize？** 避免无意义的检索专家调用，提升响应与成本。
- **为什么已有 stepResults 时强制 synthesize？** 防止同一专家被重复调用多次（检索/分析/建议环）。
- **maxSteps 与 recursionLimit 区别？** maxSteps 是业务「最多专家步数」；recursionLimit 是图框架的迭代上限，双重保险。
- **为什么 PgVector 单独数据源？** 向量库是 PostgreSQL，业务库是 MySQL；且连接池与负载隔离。
- **混合 RAG 的 RRF 作用？** 多路排序融合为一，再 Rerank 取 topK，兼顾召回与精度。
- **WebSearch 未配置会怎样？** apiKey 为空，调用 SearchAPI 失败，工具返回错误信息；建议用环境变量或条件注册避免误用。
- **为什么 ChatClient 的 defaultAdvisors 里已有 MyLoggerAdvisor，链上不再重复加？** 避免同一次请求的 Request/Response 被打印两遍；Advisor 链只保留一处日志即可。
- **为什么检索专家 prompt 里写「RAG context is missing or insufficient 时用 searchWeb」？** 与 retrievalExpertRagAdvisor 的空上下文模板配合：空上下文时 augmenter 会替换成「必须调用 searchWeb」，prompt 再强调一次，提高模型实际调用 searchWeb 的概率。
- **Okio 3.9.0 覆盖依赖是为什么？** 解决与 Spring AI / 某 transitive 依赖的 Okio 版本冲突，避免运行时 ClassNotFoundException 或方法签名不兼容。

---

## 14. 附录：文档与脚本索引

- **记忆与可观测**：见 `docs/` 下记忆说明、可观测说明。
- **Web Search 配置**：`docs/WEB-SEARCH-SETUP.md`。
- **图执行流程**：`docs/agent-graph-execution-flow.md`。
- **RAG 评测**：`scripts/README-ragas.md`、`scripts/ragas_eval.py`、`scripts/requirements-ragas.txt`。

---

以上内容覆盖技术选型、架构、数据流、图编排、RAG、工具、记忆、配置、API、边界情况与面试要点。若某一块需要展开到代码级（如某类某方法），可按文档中的「关键代码引用」与包路径定位；如需时序图可基于「图执行流程」章节用 Mermaid 绘制。

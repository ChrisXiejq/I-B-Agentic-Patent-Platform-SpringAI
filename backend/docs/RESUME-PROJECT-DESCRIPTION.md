# 简历项目描述（专利成果转化智能 Agent 平台）- 更新版

> 已按当前代码库（含 P&E + ReAct 架构）校对，可直接复制到简历使用。面试时可结合 `docs/TECHNICAL-DOCUMENTATION.md`、`docs/agent-graph-PE-Replan.md` 展开。

---

## 时间与角色

**2025.12 - 2026.03**　专利成果转化智能 Agent 平台　**核心成员**

---

## 项目背景

基于 Spring Boot 3 + Spring AI + RAG + Tool Calling + MCP 的专利成果转化智能平台，面向企业/高校/个人提供专利转化智能查询、专利成果调研等功能，为领域研究提供专利成果转化数据，解决专利技术理解成本高、专利转化决策难等问题。

---

## 1. 技术栈

Spring AI，RAG + Agent，ReAct，LangGraph4j，PgVector，MCP，Spring Boot，MySQL，Redis 等。

---

## 2. 业务中台设计与实现

基于 Spring Boot 构建业务中台，提供专利问卷调研、成果转化查询等基础业务能力；设计 JWT 鉴权、黑名单与 Token 刷新机制，解决用户鉴权与 Token 自动续期问题；使用 Redis 实现热点数据缓存与过期策略，降低数据库压力。

---

## 3. Agent 系统架构

基于 **LangGraph4j** 实现 **Plan-and-Execute（P&E）+ ReAct** 图编排：**Planner** 接收用户请求生成任务列表，**Executor** 对每个子任务运行 ReAct 循环（RAG + 工具调用）完成检索/价值评估/建议等能力，**Re-planner** 在每步执行后检查结果，环境变化（如专利已失效）时动态更新剩余任务列表、结果不足时整计划重规划，综合节点汇总输出；通过 maxSteps 与 recursionLimit 控制步数与递归上限，提升可维护性与可解释性；对简单问候/自我介绍做短路路由，减少无效调用。

---

## 4. 记忆架构设计

实现三层记忆架构：Working Memory 采用滑动窗口 + importance 剪枝进行感官与上下文管理，Experiential Memory 通过递归摘要及 decay weight 元数据存储事件摘要和用户意图，Semantic Memory 通过 NLI 控制写入，存储长期事实与偏好数据。

---

## 5. 提示词工程与集中式工具注册、MCP

利用角色定义、Few-shot 与 Prompt Template 提升提示词灵活性与回答准确性；基于单例模式和 Spring Bean 实现集中式工具注册类，插拔式统一管理热度查询、Web Search、记忆检索等 Tool 能力，提高代码可维护性；部分工具采用单独部署 + MCP 模式集成，通过本地 Stdio 调用，保障主服务稳定性与安全性。

---

## 6. RAG 文档处理与查询增强

设置语义切分 + Token 回退策略提升长文信息保留率；综合运用查询重写等机制优化原始用户 prompt，提高召回率和准确率。

---

## 7. RAG 文档检索增强

检索专家使用 Adaptive-RAG 动态调度检索策略，灵活采用向量检索 + 相似度阈值与 BM25 的混合召回机制，使用 RRF Fusion 与 rerank 处理召回结果，Recall@20 从 62% 提升至 80%，Top@1 准确率提升 10%。

---

## 8. RAGAS 自动化评估

基于 500 条测试集进行自动化评估，Faithfulness 超过 80%，召回率与上下文精确度超过 90%。

---

## 使用说明

- **Agent 架构**：已改为 P&E + ReAct（Planner → Executor → CheckResult → Re-planner），与当前图实现一致；面试可强调「环境变化只更新剩余任务、结果不足整计划重规划」的亮点。
- **JWT/黑名单/Token 刷新**：已按你提供的表述保留；若实际由网关或前端实现，面试时说明即可。
- **数据指标**：Recall@20、Top@1、Faithfulness、500 条等可按你真实评测结果微调。
- **面试准备**：技术细节与设计理由见 `docs/TECHNICAL-DOCUMENTATION.md`、P&E 流程见 `docs/agent-graph-PE-Replan.md`。

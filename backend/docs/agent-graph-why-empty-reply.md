# 全链路测试「回复无实际内容」原因说明

## 测试用例与执行概要

- **用例**：`fullPipeline_multiStepQuery_exercisesAllNodesAndReturnsAnswer`
- **用户消息**：`请先查一下专利相关的信息或热度，再简单分析技术价值，最后给我一点转化建议。`
- **现象**：图执行成功（退出码 0），但最终回复只是礼貌地请用户提供专利号、介绍能力，没有专利数据、技术分析或转化建议等「实际内容」。

---

## 执行流程（按日志顺序）

| 步骤 | 节点 | 发生了什么 |
|------|------|------------|
| 1 | **Planner** | 规划出 `retrieval, analysis, advice, synthesize` |
| 2 | **Dispatch** | 当前任务 = `retrieval` → 进入 Executor |
| 3 | **Executor (retrieval)** | 执行检索专家：RAG 已注入（QueryRewrite、文档拼接、ContextualQueryAugmenter），工具可用（getPatentDetails、getPatentHeat、searchWeb 等）。**模型没有调用任何工具**，直接文本回复：「我能为您查询专利信息和专利热度，但需要您提供专利号。我无法进行技术价值分析或提供商业转化建议。」 |
| 4 | **Executor 出口** | 该回复含「无法」→ `isResultInsufficient(out)==true` → **needReplan=1** |
| 5 | **CheckResult** | 用 LLM 判断「是否环境变化」→ 返回 **environmentChanged=true** |
| 6 | **AfterExpert** | needReplan=true、environmentChanged=true → 走 **Replan** |
| 7 | **Replan** | 剩余任务原为 [analysis, advice, synthesize]；重规划后 **newRemaining=[synthesize]**，即**直接跳过 analysis、advice** |
| 8 | **Dispatch** | 下一任务 = synthesize → 进入 Synthesize |
| 9 | **Synthesize** | 仅有一条 stepResult（检索专家的「需要专利号」那段话），综合成最终回复：自我介绍 + 请提供专利号 + 询问意向。 |

因此：**检索步未产出任何专利/热度/知识库数据，且后续分析与建议步被 Replan 跳过，最终回复自然没有「实际内容」。**

---

## 根本原因归纳

### 1. 用户消息未带专利号

- 测试消息是「查一下专利相关的信息或热度」的**泛化请求**，没有具体专利号。
- `getPatentDetails` / `getPatentHeat` 需要专利号才能查；模型在「没有专利号」时选择不调用这两类工具，而是直接文字回复「需要您提供专利号」，**从工具契约上看是合理的**。

### 2. 检索专家未用 searchWeb 补足

- `RETRIEVAL_EXPERT_PROMPT` 已说明：对「通用/概念类问题（如专利商业化是什么、平台介绍）」或 RAG 不足时，应使用 **searchWeb**。
- 本次运行中模型**没有调用 searchWeb**，没有用网页结果补充「专利相关信息或热度」的通用回答，导致检索步只返回一句「需要专利号」，没有可用的检索内容供后续分析与建议使用。

### 3. 「结果不足」触发 Replan 并收缩计划

- 检索专家回复里包含「**无法**」，命中 `isResultInsufficient()` 规则 → **needReplan=1**。
- CheckResult 再通过 LLM 得到 environmentChanged=true，AfterExpert 进入 Replan。
- Replan 将剩余步骤收缩为仅 **synthesize**，**analysis、advice 两步被跳过**，因此不会再有「技术价值分析」和「转化建议」的专家输出。

综合：**检索步既无工具调用产出，又被判为不足并触发重规划，后续专家步被砍掉，最终只有「请提供专利号」类的综合回复。**

---

## 可选改进方向

1. **测试用例带专利号（最直接）**  
   - 例如：`请查一下专利 CN123456789 的详情和热度，再简单分析技术价值并给一点转化建议。`  
   - 这样检索专家有机会调用 getPatentDetails/getPatentHeat，产出真实数据，analysis/advice 也有输入，不易被 Replan 收束成只剩 synthesize。

2. **强化检索专家 prompt**  
   - 在 `RETRIEVAL_EXPERT_PROMPT` 中明确：**若用户未提供专利号**，应优先用 **searchWeb** 查「专利商业化」「平台介绍」「专利热度/价值一般概念」等，并基于检索结果简要回复，而不是只回复「需要专利号」。
   - 这样即使没有专利号，检索步也能产出一些「实际内容」，减少被判为不足、进而触发 Replan 收缩的可能。

3. **微调 isResultInsufficient / Replan 策略（按产品需求）**  
   - 当前「需要您提供专利号」类回复会因含「无法」被判为不足并触发 Replan。  
   - 若希望「仅提示用户提供专利号」时**不要**收缩后续步骤，可对这类短句做例外（例如：仅当同时满足「请提供」且无任何工具调用记录」时再视为不足），或放宽 Replan 条件，让 analysis/advice 仍有机会基于「当前已有信息」做泛化分析或建议。

以上三点可单独或组合使用，以在保持当前图结构的前提下，让同一条多步查询更容易产出「有实际内容」的回复。

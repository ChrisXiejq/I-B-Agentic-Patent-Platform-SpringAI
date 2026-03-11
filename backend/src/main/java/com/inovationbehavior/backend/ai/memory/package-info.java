/**
 * 三层分层记忆架构（企业级）：Working → Experiential → Long-Term。
 * <p>
 * Layer1 Working：内存、滑动窗口、动态剪枝（importance &lt; 阈值剔除）、溢出写 Experiential。
 * Layer2 Experiential：事件摘要 + pgvector，decay_weight 重排。
 * Layer3 Long-Term：NLI 三段论（Recall → Conflict → UPDATE/MERGE），可选 Zettelkasten 原子事实。
 * <p>
 * 主要组件：
 * <ul>
 *   <li>{@link WorkingMemoryService} Layer1 工作记忆</li>
 *   <li>{@link ExperientialMemoryService} Layer2 中期事件摘要</li>
 *   <li>{@link LongTermMemoryService} Layer3 长期语义记忆</li>
 *   <li>{@link MemoryPersistenceAdvisor} 仅持久化，不注入</li>
 *   <li>{@link MemoryRetrievalTool} MCP 工具 retrieve_history 按需检索</li>
 *   <li>{@link ImportanceScorer} / {@link PatentDomainImportanceScorer} 重要性评分</li>
 *   <li>{@link NliConflictDetector} / {@link LlmNliConflictDetector} NLI 冲突检测</li>
 *   <li>{@link AtomicFactExtractor} / {@link LlmAtomicFactExtractor} 原子事实（Zettelkasten）</li>
 * </ul>
 * 配置见 application.yaml：app.memory.working / experiential / long-term。
 */

package com.inovationbehavior.backend.ai.memory;

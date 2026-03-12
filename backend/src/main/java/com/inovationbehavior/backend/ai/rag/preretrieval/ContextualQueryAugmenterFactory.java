package com.inovationbehavior.backend.ai.rag.preretrieval;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 检索前：创建上下文查询增强器的工厂（空上下文时的回复模板等）
 */
public final class ContextualQueryAugmenterFactory {

    /** 默认：空上下文时要求模型直接输出“知识库无相关内容”的固定话术（不触发工具） */
    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                You should output the following content:
                Sorry, no content related to your question was found in the current knowledge base. I mainly support patent commercialization, patent retrieval, and value assessment related consultations.
                You may directly enter a patent number to query details and heat, or describe your needs for me to invoke tools to assist.
                """);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }

    /**
     * 检索专家专用：空上下文时保留用户问题并明确要求先调用 searchWeb，再根据搜索结果回答。
     * 模板占位符与 Spring AI ContextualQueryAugmenter 一致（如 query）。
     */
    public static ContextualQueryAugmenter createInstanceForRetrievalExpertWithWebFallback() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                The knowledge base has no relevant documents for this query.
                User question: {query}
                You MUST call the searchWeb tool with the user's question or key terms (e.g. in Chinese or English) to get up-to-date information from the web, then answer based on the search results. Do not reply without calling searchWeb first.
                """);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}

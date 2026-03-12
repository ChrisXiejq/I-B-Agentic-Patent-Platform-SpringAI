package com.inovationbehavior.backend.ai.app;

import com.inovationbehavior.backend.ai.advisor.BannedWordsAdvisor;
import com.inovationbehavior.backend.ai.advisor.MyLoggerAdvisor;
import com.inovationbehavior.backend.ai.rag.preretrieval.QueryRewriter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class IBApp {

    private final ChatModel chatModel;

    @Autowired(required = false)
    private com.inovationbehavior.backend.ai.graph.PatentGraphRunner patentGraphRunner;
    private ChatClient chatClient;

    @Resource
    private BannedWordsAdvisor bannedWordsAdvisor;

    private static final String SYSTEM_PROMPT = """
            You are an intelligent assistant for the patent commercialization platform. You focus on helping users with patent retrieval, value assessment, and commercialization.
            At the opening, briefly introduce yourself and explain you can: query patent details and heat by patent number, help analyze patent technical points and applicable scenarios, and provide personalized advice based on user identity (invitation code/survey).
            During the conversation, you may proactively ask: patent numbers or technical fields of interest, commercialization intentions (license/transfer/equity investment, etc.), target industry or partner type.
            Provide concise, professional advice based on patent information from platform tools, and timely suggest users use the "query patent details" and "query patent heat" capabilities.
            When you need to recall what the user said earlier or this conversation's history, call the retrieve_history tool with the current conversation_id and a short query (e.g. patent number or topic).
            """;

    public IBApp(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** 默认注入违禁词等 Advisor 后初始化 ChatClient */
    @PostConstruct
    void initChatClient() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        var advisors = new java.util.ArrayList<Advisor>();
        if (bannedWordsAdvisor != null) {
            advisors.add(bannedWordsAdvisor);
        }
        advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());
        advisors.add(new MyLoggerAdvisor());
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(advisors)
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    /** 专利咨询报告（结构化输出：标题 + 建议列表） */
    record PatentReport(String title, List<String> suggestions) {
    }

    /**
     * 专利咨询报告（实战结构化输出）
     */
    public PatentReport doChatWithReport(String message, String chatId) {
        PatentReport report = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "After each conversation, generate a brief patent advisory report: title as 'Patent Advisory Report', content as a list of 3-5 suggestions addressing the user's question, related to patent commercialization, value assessment, or connection advice.")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(PatentReport.class);
        log.info("patentReport: {}", report);
        return report;
    }

    // RAG 知识库问答（专利/平台文档）

    @Resource
    private Advisor hybridRagAdvisor;

    @Resource
    @Qualifier("retrievalExpertRagAdvisor")
    private Advisor retrievalExpertRagAdvisor;

    @Resource
    @Qualifier("IBVectorStore")
    private VectorStore vectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 和 RAG 知识库进行对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        // 查询重写
//        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(hybridRagAdvisor)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * 专利平台对话（支持调用工具：查专利详情、热度、用户身份等）
     */
    public Flux<String> doChatWithTools(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(allTools)
                .stream()
                .content();
    }

    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * 专利平台对话（调用 MCP 服务）
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // ========== 全能力 Agent：记忆按需检索（retrieve_history 工具）+ RAG + 工具调用 ==========

    @Autowired(required = false)
    @Qualifier("memoryPersistenceAdvisor")
    private Advisor memoryPersistenceAdvisor;

    @Autowired(required = false)
    @Qualifier("agentTraceAdvisor")
    private Advisor agentTraceAdvisor;

    /**
     * 多 Agent 图编排入口（优先）。若未启用图则回退到单 Agent 全能力。
     */
    public String doChatWithMultiAgentOrFull(String message, String chatId) {
        if (patentGraphRunner != null) {
            return patentGraphRunner.run(message, chatId);
        }
        return doChatWithFullAgent(message, chatId);
    }

    /**
     * 全能力 Agent 同步调用。记忆不自动注入；需要历史时 Agent 显式调用 retrieve_history(conversation_id, query)。
     */
    public String doChatWithFullAgent(String message, String chatId) {
        String rewrittenMessage = queryRewriter != null ? queryRewriter.doQueryRewrite(message) : message;
        var adv = chatClient.prompt().user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId));
        if (agentTraceAdvisor != null) {
            adv = adv.advisors(agentTraceAdvisor);
        }
        if (memoryPersistenceAdvisor != null) {
            adv = adv.advisors(memoryPersistenceAdvisor);
        }
        ChatResponse chatResponse = adv
                .advisors(hybridRagAdvisor)
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("full-agent sync content length: {}", content != null ? content.length() : 0);
        return content;
    }

    /**
     * 全能力 Agent 流式调用。记忆按需通过 retrieve_history 工具获取。
     */
    public Flux<String> doChatWithFullAgentStream(String message, String chatId) {
        String rewrittenMessage = queryRewriter != null ? queryRewriter.doQueryRewrite(message) : message;
        var adv = chatClient.prompt().user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId));
        if (agentTraceAdvisor != null) {
            adv = adv.advisors(agentTraceAdvisor);
        }
        if (memoryPersistenceAdvisor != null) {
            adv = adv.advisors(memoryPersistenceAdvisor);
        }
        return adv
                .advisors(hybridRagAdvisor)
                .toolCallbacks(allTools)
                .stream()
                .content();
    }

    // ========== 多 Agent 图编排：专家节点与路由/综合 ==========

    private static final String RETRIEVAL_EXPERT_PROMPT = """
            You are the retrieval expert of the patent platform. Your job is to fetch patent details, patent heat, knowledge base content, and when needed, up-to-date or general knowledge from the web.
            - Use getPatentDetails, getPatentHeat, and retrieve_history for patent-specific or conversation history.
            - Use searchWeb (web search) when: the user asks for general/conceptual info (e.g. what is patent commercialization, platform introduction), or when RAG context is missing or insufficient. Prefer searchWeb for broad or latest-information questions.
            Reply briefly with the retrieved data; do not give commercialization advice.
            """;
    private static final String ANALYSIS_EXPERT_PROMPT = """
            You are the technical/value analysis expert. Based on patent details and heat already available or that you fetch, analyze technical points, applicability, and value.
            Use getPatentDetails, getPatentHeat, retrieve_history, and RAG when needed. Reply with concise analysis; do not give licensing or partnership advice.
            """;
    private static final String ADVICE_EXPERT_PROMPT = """
            You are the commercialization advisor. Give licensing, transfer, or partnership advice based on user identity and patent context.
            Use getUserIdentity, getPatentDetails, getPatentHeat when needed. Reply with actionable advice.
            """;

    /**
     * 专家节点专用：按给定 systemPrompt 执行一次对话（RAG + 工具 + 记忆），用于图内检索/分析/建议节点。
     */
    public String doExpertChat(String message, String chatId, String systemPrompt) {
        String rewritten = queryRewriter != null ? queryRewriter.doQueryRewrite(message) : message;
        var adv = chatClient.prompt()
                .system(systemPrompt)
                .user(rewritten)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId));
        if (memoryPersistenceAdvisor != null) {
            adv = adv.advisors(memoryPersistenceAdvisor);
        }
        if (agentTraceAdvisor != null) {
            adv = adv.advisors(agentTraceAdvisor);
        }
        // 检索专家使用带“空上下文→要求 searchWeb”的 RAG，以便知识库无结果时上网查询
        Advisor ragAdvisor = systemPrompt.contains("retrieval") && retrievalExpertRagAdvisor != null
                ? retrievalExpertRagAdvisor : hybridRagAdvisor;
        ChatResponse chatResponse = adv
                .advisors(ragAdvisor)
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        String expertName = systemPrompt.contains("retrieval") ? "Retrieval" : systemPrompt.contains("analysis") ? "Analysis" : "Advice";
        log.info("[AgentGraph.IBApp] 专家调用 expert={} messageLength={} responseLength={} (本步会触发 RAG + 工具调用，详见 AgentTrace.Tool 日志)",
                expertName, message != null ? message.length() : 0, content != null ? content.length() : 0);
        return content != null ? content : "";
    }

    /**
     * 路由节点：对当前用户消息做意图分类，供图条件边使用。
     * 返回: retrieval | analysis | advice | synthesize | end
     */
    public String classifyIntentForGraph(String userMessage, int stepCount, int maxSteps, List<String> stepResults) {
        if (stepCount >= maxSteps) {
            log.info("[AgentGraph.IBApp] 路由分类 stepCount>={} 强制 synthesize", maxSteps);
            return "synthesize";
        }
        // 简单问候/自我介绍：直接进综合节点，不经过检索专家
        if (stepCount == 0 && isSimpleGreetingOrIntro(userMessage)) {
            log.info("[AgentGraph.IBApp] 路由分类 识别为简单问候/自我介绍，直接 synthesize");
            return "synthesize";
        }
        String prompt = """
                Classify the user intent into exactly one word: retrieval, analysis, advice, synthesize, or end.
                - retrieval: user wants to query patent details, heat, or search knowledge (not simple greeting or self-intro).
                - analysis: user wants technical/value analysis of a patent.
                - advice: user wants commercialization, licensing, or partnership advice.
                - synthesize: user said thanks or asked to summarize; or sent a simple greeting (e.g. 你好, hi) or asked for self-introduction (介绍自己, 你是谁); or we already have enough context.
                - end: user said goodbye or clearly wants to end.
                User message: %s
                Reply with only one word from: retrieval, analysis, advice, synthesize, end.
                """.formatted(userMessage);
        ChatResponse resp = chatClient.prompt()
                .user(prompt)
                .call()
                .chatResponse();
        String raw = resp.getResult().getOutput().getText();
        if (raw == null) {
            log.info("[AgentGraph.IBApp] 路由分类 LLM 返回空，使用 synthesize");
            return "synthesize";
        }
        String lower = raw.trim().toLowerCase();
        String decision = lower.contains("retrieval") ? "retrieval" : lower.contains("analysis") ? "analysis" : lower.contains("advice") ? "advice" : lower.contains("end") ? "end" : "synthesize";
        // 若已有专家输出且意图仍为某类专家，直接 synthesize，避免同一问题重复进入检索/分析/建议专家
        if (stepCount >= 1 && stepResults != null && !stepResults.isEmpty()
                && ("retrieval".equals(decision) || "analysis".equals(decision) || "advice".equals(decision))) {
            log.info("[AgentGraph.IBApp] 路由分类 已有 stepResults 且决策={}，改为 synthesize 避免重复专家调用", decision);
            decision = "synthesize";
        }
        log.info("[AgentGraph.IBApp] 路由分类 userMessage(preview)={} stepCount={} 原始LLM回复={} 决策={}",
                userMessage != null && userMessage.length() > 60 ? userMessage.substring(0, 60) + "..." : userMessage, stepCount, abbreviate(raw, 80), decision);
        return decision;
    }

    /** 简单问候或“介绍自己”类短句，无需走检索专家，直接 synthesize 即可 */
    private static boolean isSimpleGreetingOrIntro(String userMessage) {
        if (userMessage == null) return false;
        String s = userMessage.trim();
        if (s.length() > 50) return false;
        String lower = s.toLowerCase();
        return lower.contains("你好") || lower.contains("嗨") || lower.contains("hi ") || lower.equals("hi")
                || lower.contains("介绍自己") || lower.contains("自我介绍") || lower.contains("你是谁")
                || lower.contains("introduce yourself");
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "null";
        s = s.trim();
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    /**
     * 综合节点：根据已有 stepResults 与用户问题，生成最终回复。
     */
    public String synthesizeAnswer(String userMessage, List<String> stepResults, String chatId) {
        String context = stepResults == null || stepResults.isEmpty()
                ? "No prior expert outputs."
                : String.join("\n---\n", stepResults);
        String prompt = """
                You are the patent platform assistant. Below are expert outputs from retrieval/analysis/advice agents.
                Provide a concise, friendly final answer to the user. Do not repeat long raw data; summarize and give clear next steps if needed.
                User question: %s
                Expert outputs:
                %s
                """.formatted(userMessage, context);
        ChatResponse resp = chatClient.prompt()
                .user(prompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = resp.getResult().getOutput().getText();
        log.info("[AgentGraph.IBApp] 综合节点 stepResultsCount={} finalAnswerLength={}",
                stepResults != null ? stepResults.size() : 0, content != null ? content.length() : 0);
        return content != null ? content : "";
    }

    public String getRetrievalExpertPrompt() { return RETRIEVAL_EXPERT_PROMPT; }
    public String getAnalysisExpertPrompt() { return ANALYSIS_EXPERT_PROMPT; }
    public String getAdviceExpertPrompt() { return ADVICE_EXPERT_PROMPT; }
}

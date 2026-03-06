package com.inovationbehavior.backend.agent;

import com.inovationbehavior.backend.model.Patent;
import com.inovationbehavior.backend.service.intf.PatentService;
import com.inovationbehavior.backend.service.intf.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 工具：对应原 LLM base MCP 的 get_identification、get_enterprise_interest、get_patent_analysis、get_rag_patent_info。
 * 供系统提示中描述能力；升级到 Spring AI 1.0+ 后可加 @Tool 实现 function calling。
 */
@Component
@RequiredArgsConstructor
public class PatentAgentTools {

    private final PatentService patentService;
    private final RagService ragService;

    /** 获取用户身份类型（企业/高校/个人） */
    public String getIdentification(String userId) {
        return "enterprise";
    }

    /** 获取该专利的企业兴趣度（填写问卷的企业数量/热度） */
    public String getEnterpriseInterest(String patentNo) {
        return "HIGH";
    }

    /** 根据专利号查询专利详情（名称、摘要、链接等） */
    public String getPatentAnalysis(String patentNo) {
        Patent p = patentService.getPatentByNo(patentNo);
        if (p == null) {
            return "未找到专利: " + patentNo;
        }
        List<String> parts = new ArrayList<>();
        if (p.getName() != null && !p.getName().isEmpty()) {
            parts.add("名称: " + p.getName());
        }
        if (p.getSummary() != null && !p.getSummary().isEmpty()) {
            parts.add("摘要: " + p.getSummary());
        }
        if (p.getLink() != null && !p.getLink().isEmpty()) {
            parts.add("链接: " + p.getLink());
        }
        return parts.isEmpty() ? p.toString() : String.join("\n", parts);
    }

    /** 基于 RAG 获取专利相关知识增强回答 */
    public String getRagPatentInfo(String patentNo, String query) {
        String q = (query != null && !query.isBlank()) ? query.trim() : ("专利 " + patentNo + " 的相关信息、技术要点、应用场景");
        String insights = ragService.getRagAnswer(q, patentNo);
        return "专利 " + patentNo + " RAG 知识增强回答:\n" + insights;
    }
}

package com.inovationbehavior.backend.ai.tools;

import com.inovationbehavior.backend.model.Patent;
import com.inovationbehavior.backend.service.intf.PatentService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 专利详情查询工具
 */
@Component
public class PatentDetailTool {

    @Autowired
    private PatentService patentService;

    @Tool(description = "根据专利号查询专利详细信息，包括名称、摘要、链接、类型、状态、PDF 列表等")
    public String getPatentDetails(
            @ToolParam(description = "专利号，如 CN 或申请号") String patentNo) {
        if (patentNo == null || patentNo.isBlank()) {
            return "请提供专利号 patentNo";
        }
        try {
            Patent patent = patentService.getPatentByNo(patentNo.trim());
            if (patent == null) {
                return "未找到专利: " + patentNo;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("专利号: ").append(patent.getNo()).append("\n");
            sb.append("名称: ").append(patent.getName() != null ? patent.getName() : "-").append("\n");
            sb.append("摘要: ").append(patent.getSummary() != null ? patent.getSummary() : "-").append("\n");
            sb.append("链接: ").append(patent.getLink() != null ? patent.getLink() : "-").append("\n");
            sb.append("申请人: ").append(patent.getAppln_application() != null ? patent.getAppln_application() : "-").append("\n");
            sb.append("类型: ").append(patent.getType() != null ? patent.getType() : "-").append("\n");
            sb.append("状态: ").append(patent.getStatus() != null ? patent.getStatus() : "-").append("\n");
            sb.append("代理机构: ").append(patent.getAgency() != null ? patent.getAgency() : "-").append("\n");
            if (patent.getUpdate_time() != null) {
                sb.append("更新时间: ").append(patent.getUpdate_time()).append("\n");
            }
            List<String> pdfs = patent.getPdfs();
            if (pdfs != null && !pdfs.isEmpty()) {
                sb.append("PDF 数量: ").append(pdfs.size()).append("\n");
                sb.append("PDF 列表: ").append(pdfs.stream().limit(5).collect(Collectors.joining("; ")));
                if (pdfs.size() > 5) sb.append(" ...等").append(pdfs.size()).append(" 个");
            } else {
                sb.append("PDF: 暂无");
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询专利详情失败: " + e.getMessage();
        }
    }
}

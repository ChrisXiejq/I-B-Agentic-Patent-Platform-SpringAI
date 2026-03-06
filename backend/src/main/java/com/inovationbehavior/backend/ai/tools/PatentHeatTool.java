package com.inovationbehavior.backend.ai.tools;

import com.inovationbehavior.backend.model.Patent;
import com.inovationbehavior.backend.service.intf.PatentService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 专利热度/状态工具
 */
@Component
public class PatentHeatTool {

    @Autowired
    private PatentService patentService;

    @Tool(description = "查询指定专利的热度或受关注程度，基于专利状态与评价情况（status=1 表示已评价/热度较高）")
    public String getPatentHeat(
            @ToolParam(description = "专利号，如 CN 或申请号") String patentNo) {
        if (patentNo == null || patentNo.isBlank()) {
            return "请提供专利号 patentNo";
        }
        try {
            Patent patent = patentService.getPatentByNo(patentNo.trim());
            if (patent == null) {
                return "未找到专利: " + patentNo;
            }
            Integer status = patent.getStatus();
            String heatDesc = (status != null && status == 1)
                    ? "已评价，热度较高"
                    : "未评价或普通状态";
            return String.format("专利 %s 热度：%s（status=%s）。可结合专利详情与问卷数据进一步分析关注度。",
                    patent.getNo(), heatDesc, status);
        } catch (Exception e) {
            return "查询专利热度失败: " + e.getMessage();
        }
    }
}

package com.inovationbehavior.backend.ai.tools;

import com.inovationbehavior.backend.mapper.SurveyMapper;
import com.inovationbehavior.backend.model.survey.Survey;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户身份相关工具（与问卷/邀请码关联）
 */
@Component
public class UserIdentityTool {

    @Autowired(required = false)
    private SurveyMapper surveyMapper;

    @Tool(description = "根据邀请码或专利号查询用户/问卷身份信息，用于判断当前用户与专利问卷的关联")
    public String getUserIdentity(
            @ToolParam(description = "邀请码 invitationCode，可选") String invitationCode,
            @ToolParam(description = "专利号 patentNo，可选，用于查询该专利下的问卷身份") String patentNo) {
        if (surveyMapper == null) {
            return "用户身份服务暂不可用（SurveyMapper 未注入）";
        }
        if (patentNo != null && !patentNo.isBlank()) {
            Survey survey = surveyMapper.getSurvey(patentNo);
            if (survey == null) {
                return "专利号 " + patentNo + " 下暂无问卷/用户身份记录";
            }
            return String.format("专利 %s 的问卷身份：identification=%s, enterprise=%s, value=%s, use=%s, policy=%s, invitationCode=%s",
                    patentNo,
                    survey.getIdentification() != null ? survey.getIdentification() : "-",
                    survey.getEnterprise() != null ? survey.getEnterprise() : "-",
                    survey.getValue() != null ? survey.getValue() : "-",
                    survey.getUsage() != null ? survey.getUsage() : "-",
                    survey.getPolicy() != null ? survey.getPolicy() : "-",
                    survey.getInvitationCode() != null ? survey.getInvitationCode() : "-");
        }
        if (invitationCode != null && !invitationCode.isBlank()) {
            return "当前用户标识为邀请码: " + invitationCode + "。该邀请码用于问卷提交与身份关联，具体问卷内容需结合专利号查询。";
        }
        return "未提供 invitationCode 或 patentNo。可传入 patentNo 查询该专利下的问卷身份，或传入 invitationCode 表示当前用户标识。";
    }
}

package com.inovationbehavior.backend.controller;
import com.inovationbehavior.backend.model.Patent;
import com.inovationbehavior.backend.model.Result;
import com.inovationbehavior.backend.service.intf.AgentService;
import com.inovationbehavior.backend.service.intf.PatentService;
import com.inovationbehavior.backend.service.intf.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
public class AgentController {
    @Autowired
    private RagService ragService;

    @Autowired
    PatentService patentService;

    /**
     * 用户委托agent查询专利相关信息
     * @param userQuery 用户查询内容
     * @param patentNo 专利号
     * @return 分析结果
     */
    @PostMapping("/analyse")
    public Result rag(@RequestParam("userQuery") String userQuery, @RequestParam("patent_no") String patentNo) {
        String answer = ragService.getRagAnswer(userQuery, patentNo);
        return Result.success(answer);
    }

    /**
     * agent tool，查询专利信息
     * @param patentNo 专利号
     * @return 专利信息
     */
    @PostMapping("/tools/patent/search")
    public Result search(@RequestParam("patent_no") String patentNo) {
        Patent tmp = patentService.getPatentByNo(patentNo);
        if (tmp != null) {
            return Result.success(tmp);
        }
        return Result.error("未找到No：" + patentNo);
    }

    /**
     * agent tool，查询这个专利下有多少企业感兴趣（即填过问卷）
     * @param patentNo 专利号
     * @return 感兴趣指数
     */
    @PostMapping("/tools/patent/enterprise")
    public Result interest(@RequestParam("patent_no") String patentNo) {
        return Result.success("HIGH");
    }

    @Autowired
    private AgentService agentService;

    /**
     * Agent 对话接口，支持多用户记忆区分
     * @param query 用户查询
     * @param userId 可选用户ID（用于分层记忆多租户，可从 JWT 解析）
     */
    @PostMapping("/chat")
    public Result chat(
            @RequestParam("query") String query,
            @RequestParam(value = "userId", required = false) String userId
    ) {
        String answer = agentService.chat(query, userId);
        return Result.success(answer);
    }

}

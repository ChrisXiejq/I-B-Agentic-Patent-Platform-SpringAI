package com.inovationbehavior.backend.service.intf;

public interface AgentService {
    /**
     * 与智能体服务交互，获取响应
     *
     * @param userQuery 用户查询
     * @return 智能体服务的响应
     */
    default String chat(String userQuery) {
        return chat(userQuery, null);
    }

    /**
     * 与智能体服务交互，获取响应（支持多用户记忆）
     *
     * @param userQuery 用户查询
     * @param userId 可选用户ID，用于分层记忆多租户区分
     * @return 智能体服务的响应
     */
    String chat(String userQuery, String userId);
}

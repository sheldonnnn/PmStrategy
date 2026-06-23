package com.cmbc.strategy.integration;

/**
 * 员工消息推送
 */
public interface IOCRMMEmlMessageNewTopicService {
    /**
     * 联机调用，消息推送服务
     * @return
     */
    public void sendMessageFromSystem(String userId, String instanceId, String message);
}

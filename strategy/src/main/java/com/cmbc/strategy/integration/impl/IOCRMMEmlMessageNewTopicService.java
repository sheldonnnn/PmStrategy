package com.cmbc.strategy.integration.impl;

import com.alibaba.fastjson.JSON;
import com.cmbc.strategy.config.SM3Utils;
import com.cmbc.strategy.config.SeqUtil;
import com.cmbc.strategy.domain.request.CommonHeader;
import com.cmbc.strategy.domain.request.SendMessageFromSystemBody;
import com.cmbc.strategy.domain.request.SendMessageFromSystemRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: cuijian
 * @Date: 2026/02/28 19:34
 * @Description:
 */
@Service
public class IOCRMMEmlMessageNewTopicService implements IOCRMMEmlMessageNewTopicService {
    private static final Logger log = LoggerFactory.getLogger(IOCRMMEmlMessageNewTopicService.class);

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private SeqUtil seqUtil;

    @Value("${sendMessage.url}")
    String sendMessageUrl;

    @Value("${sendMessage.key}")
    String sendMessageKey;
    
    @Value("${sendMessage.applyId}")
    String applyId; // 秘钥

    // Image truncated body method, transcribing what is visible
    /*
        return;
    }
    String code = (String) returnCode.get("code");
    if (!succrssCode.equals(code)) {
        log.error("爱民生消息通知异常: {}", returnCode.get("message"));
        return;
    }
    }
    // 开始排查returnCode
    Map returnCode = (Map) reply.get("returnCode");
    if (null == returnCode) {
        log.error("爱民生消息通知异常, 返回信息为空!!");
        return;
    }
    String code = (String) returnCode.get("code");
    if (!succrssCode.equals(code)) {
        log.error("爱民生消息通知异常: {}", returnCode.get("message"));
        return;
    }
    } catch (Exception e) {
        log.error("爱民生消息通知异常, {}", e);
        return;
    }
    }
    */

    private SendMessageFromSystemRequest buildSendMessageData(String userId, String instanceId, String message) {
        String seqNum = seqUtil.getSeqNum();
        CommonHeader commonHeader = new CommonHeader();
        commonHeader.setSeqNum(seqNum);
        commonHeader.setOperatorId("system");
        commonHeader.setOperatorName("system");
        commonHeader.setDomainCode("Z13");
        commonHeader.setAuthBody(SM3Utils.encryptString(applyId + seqNum, sendMessageKey));

        SendMessageFromSystemBody body = new SendMessageFromSystemBody();
        body.setApplyId(applyId);
        body.setChannels("2");
        body.setReceivers(userId);
        Map<String, String> info = new HashMap<>();
        info.put("instanceId", instanceId);
        info.put("message", message);
        body.setParams(JSON.toJSONString(info));
        body.setTransmitParams("");
        SendMessageFromSystemRequest request = new SendMessageFromSystemRequest();
        request.setCommonHeader(commonHeader);
        request.setBody(body);
        return request;
    }
}

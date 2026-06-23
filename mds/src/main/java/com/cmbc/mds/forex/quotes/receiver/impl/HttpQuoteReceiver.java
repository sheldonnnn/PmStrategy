package com.cmbc.mds.forex.quotes.receiver.impl;

import com.cmbc.mds.forex.quotes.receiver.IbmMqQuoteMessageProcessor;
import com.cmbc.mds.forex.quotes.receiver.QuoteReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes/receiver")
public class HttpQuoteReceiver extends QuoteReceiver {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IbmMqQuoteMessageProcessor ibmMqQuoteMessageProcessor;

    /**
     * 手工推送 IBM MQ 测试行情。
     *
     * <p>该接口用于测试环境模拟 IBM MQ 文本报文，业务处理必须尽量复用真实 MQ 通路。
     * `provider` 参数仅作为测试覆盖值使用；不传时完全使用报文中的 serviceId。
     */
    @PostMapping("/push")
    public String receiveHttpQuote(@RequestParam(value = "provider", required = false) String paramProvider,
            @RequestBody String body) {
        if (body == null || body.isBlank()) {
            log.debug("收到空 HTTP 测试行情，忽略处理。provider={}", paramProvider);
            return "success";
        }

        // 复用 IBM MQ 消息处理器，标记 expectedProvider 为 HTTP_IBMMQ_REPLAY 供日志区分，
        // 解析报文后交由父类 QuoteReceiver 进行路由和分发
        ibmMqQuoteMessageProcessor.process("HTTP_IBMMQ_REPLAY", body, this, paramProvider);
        return "success";
    }
}

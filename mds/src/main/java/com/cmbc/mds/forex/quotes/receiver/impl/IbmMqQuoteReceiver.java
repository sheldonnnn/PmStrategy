package com.cmbc.mds.forex.quotes.receiver.impl;

import com.cmbc.mds.forex.quotes.receiver.IbmMqQuoteMessageProcessor;
import com.cmbc.mds.forex.quotes.receiver.JmsMessageBodyExtractor;
import com.cmbc.mds.forex.quotes.receiver.QuoteReceiver;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
public class IbmMqQuoteReceiver extends QuoteReceiver {

    @Autowired
    private JmsMessageBodyExtractor messageBodyExtractor;

    @Autowired
    private IbmMqQuoteMessageProcessor ibmMqQuoteMessageProcessor;

    @Value("${app.quote.message-capture.enabled:false}")
    private boolean messageCaptureEnabled;

    @Value("${app.quote.message-capture.path:logs/mq_messages_capture.log}")
    private String messageCapturePath;

    /**
     * 高盛通道接收。
     */
//    @JmsListener(destination = "${ibm.mq.queue.gs}")
    public void onMessageGs(Message message) {
        handleMessage(message);
    }

    /**
     * 汇丰通道接收。
     */
//    @JmsListener(destination = "${ibm.mq.queue.hsbc}")
    public void onMessageHsbc(Message message) {
        handleMessage(message);
    }

    /**
     * 瑞银通道接收。
     */
//    @JmsListener(destination = "${ibm.mq.queue.ubs}")
    public void onMessageUbs(Message message) {
        handleMessage(message);
    }

    /**
     * FXALL 通道接收。
     */
//    @JmsListener(destination = "${ibm.mq.queue.fxall}")
    public void onMessageFxall(Message message) {
        handleMessage(message);
    }

    /**
     * 真实 IBM MQ 公共入口：只负责提取 JMS 文本体和测试回放落盘，业务处理交给公共处理器。
     */
    public void handleMessage(Message message) {
        try {
            // 1. 提取 JMS 文本消息
            String jsonBody = messageBodyExtractor.extract(message);
            if (jsonBody == null || jsonBody.isBlank()) {
                log.debug("收到空内容的有效消息，忽略处理。ID: {}", message.getJMSMessageID());
                return;
            }

            // 2. 仅用于测试环境的回放捕获（包含严重的同步磁盘 I/O 阻塞点）
            if (messageCaptureEnabled) {
                captureMessageForTestReplay(message, jsonBody);
            }
            
            // 3. 将解析后的文本消息交给 IBM MQ 专属的消息处理器，提取基础信息并交由父类进行分发
            ibmMqQuoteMessageProcessor.process("IBMMQ", jsonBody, this);
        } catch (JMSException e) {
            log.error("JMS 消息解析失败", e);
        } catch (Exception e) {
            log.error("IBM MQ 消息业务处理异常", e);
        }
    }

    /**
     * 测试环境回放使用：保存完整 JMS 消息体和属性。
     *
     * 注意：这里包含同步的磁盘 I/O 检查和文件写入，高并发下会造成严重的 MQ 消费堆积。
     * 建议将其废弃或改造成通过无锁内存队列交由后台线程异步落盘。
     */
    private void captureMessageForTestReplay(Message message, String jsonBody) {
        try {
            ObjectNode fullMessageNode = objectMapper.createObjectNode();
            fullMessageNode.put("body", jsonBody);

            ObjectNode propertiesNode = objectMapper.createObjectNode();
            java.util.Enumeration<?> propertyNames = message.getPropertyNames();
            while (propertyNames.hasMoreElements()) {
                String name = propertyNames.nextElement().toString();
                Object value = message.getObjectProperty(name);
                if (value instanceof Integer intValue) {
                    propertiesNode.put(name, intValue);
                } else if (value instanceof Long longValue) {
                    propertiesNode.put(name, longValue);
                } else if (value instanceof Boolean boolValue) {
                    propertiesNode.put(name, boolValue);
                } else if (value instanceof Double doubleValue) {
                    propertiesNode.put(name, doubleValue);
                } else if (value != null) {
                    propertiesNode.put(name, value.toString());
                }
            }
            fullMessageNode.set("properties", propertiesNode);
            fullMessageNode.put("JMSMessageID", message.getJMSMessageID());
            if (message.getJMSType() != null) {
                fullMessageNode.put("JMSType", message.getJMSType());
            }

            Path logPath = Paths.get(messageCapturePath);
            Path parent = logPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(logPath,
                    objectMapper.writeValueAsString(fullMessageNode) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception ex) {
            log.error("保存 MQ 消息到本地文件失败", ex);
        }
    }
}

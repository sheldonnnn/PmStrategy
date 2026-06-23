package com.cmbc.mds.forex.quotes.receiver;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Extracts UTF-8 text bodies from supported JMS message types.
 */
@Component
public class JmsMessageBodyExtractor {

    private static final Logger log = LoggerFactory.getLogger(JmsMessageBodyExtractor.class);
    private static final int READ_BUFFER_SIZE = 8192;

    public String extract(Message message) throws JMSException {
        if (message instanceof TextMessage textMessage) {
            return textMessage.getText();
        }
        if (message instanceof BytesMessage bytesMessage) {
            return readBytesMessage(bytesMessage);
        }

        log.warn("Unsupported JMS message type, skipped. Type={}, ID={}",
                message.getClass().getName(), message.getJMSMessageID());
        return null;
    }

    private String readBytesMessage(BytesMessage bytesMessage) throws JMSException {
        long declaredLength = bytesMessage.getBodyLength();
        if (declaredLength < 0 || declaredLength > Integer.MAX_VALUE) {
            throw new JMSException("JMS message body length is invalid: " + declaredLength);
        }

        int initialCapacity = (int) Math.min(declaredLength, READ_BUFFER_SIZE);
        ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity);
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = bytesMessage.readBytes(buffer)) != -1) {
            if (bytesRead == 0) {
                throw new JMSException("JMS BytesMessage returned zero bytes before end of body");
            }
            output.write(buffer, 0, bytesRead);
        }

        if (output.size() != declaredLength) {
            throw new JMSException("JMS message body length mismatch: declared="
                    + declaredLength + ", actual=" + output.size());
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}

package com.cmbc.strategy.config;

import com.cmbc.oms.infrastructure.util.DateUtil;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Date;

/**
 * @author cuijian
 * @description 系统序列号生成器
 * @since 2024/8/29 11:37
 */
@Component
public class SeqUtil {

    public static final String DATE_YYYYMMDDHHMMSS = "yyyyMMddHHmmssSSS";

    public static final String MODULE_NO = "523"; // 系统编号
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成32位系统内部流水号
     *
     * @return
     */
    public String getSeqNum() {
        String timeStr = DateUtil.getFormatDateString(new Date(), DATE_YYYYMMDDHHMMSS);
        // 15位系统随机串
        StringBuilder randomStr = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            randomStr.append(RANDOM.nextInt(10));
        }
        return timeStr + randomStr;
    }
}

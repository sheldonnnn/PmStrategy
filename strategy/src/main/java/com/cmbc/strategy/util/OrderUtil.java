package com.cmbc.strategy.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OrderUtil {

    public static String generateOrderId(String businessType, String instanceId){

        // 1. 获取当前时间戳 (精确到毫秒)
        // 格式: yyyyMMddHHmmssSSS
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));


        // 2. 提取实例ID的后 4 位作为标识 (可选)
        // 这样在日志中可以通过 OrderId 直接反向定位是哪个实例发的单
        String instanceSuffix = (instanceId != null && instanceId.length() > 4)
                ? instanceId.substring(instanceId.length() - 4)
                : "0000";

        // 3. 组装最终 OrderID
        // 格式建议: 业务类型:时间戳:实例标识:序列号
        // 示例: HEDGE:20260213223000123:A8B9:5678
        StringBuilder orderIdBuilder = new StringBuilder();
        orderIdBuilder.append(businessType).append(":")
                .append(timestamp).append(":")
                .append(instanceSuffix).append(":");

        return orderIdBuilder.toString();

    }

}

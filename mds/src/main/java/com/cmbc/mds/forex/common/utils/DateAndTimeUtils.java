package com.cmbc.mds.forex.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DateAndTimeUtils {

    private static final Logger log = LoggerFactory.getLogger(DateAndTimeUtils.class);

    // 定义输入格式：yyyyMMdd HH:mm:ss.SSS
    public static final DateTimeFormatter TIME_FORMATTER_MILLISECOND = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss.SSS");
    // 输入格式：yyyyMMdd HH:mm:ss
    public static final DateTimeFormatter TIME_FORMATTER_SECOND = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");

    /**
     * 将时间字符串从一种格式转换为另一种格式
     *
     * @param timeStr         原始时间字符串
     * @param inputFormatter  输入的时间格式
     * @param outputFormatter 输出的时间格式
     * @return 转换后的时间字符串。如果解析或转换失败，返回 null
     */
    public static String convert(String timeStr, DateTimeFormatter inputFormatter, DateTimeFormatter outputFormatter) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(timeStr, inputFormatter);
            return dateTime.format(outputFormatter);
        } catch (DateTimeParseException e) {
            log.warn("时间转换失败: 时间字符串 [{}] 无法被解析, 错误信息: {}", timeStr, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("时间转换发生未知异常: 时间字符串 [{}]", timeStr, e);
            return null;
        }
    }

    public static String getFormatTime(DateTimeFormatter formatter) {
        LocalDateTime dateTime = LocalDateTime.now();
        return dateTime.format(formatter);
    }

    /**
     * 校验时间字符串是否符合指定的格式
     *
     * @param timeStr   需要校验的时间字符串
     * @param formatter 对应的 DateTimeFormatter
     * @return true 如果符合且合法；false 如果不符合或不合法
     */
    public static boolean isValidFormat(String timeStr, DateTimeFormatter formatter) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return false;
        }
        try {
            // 尝试解析，如果不抛出异常，说明格式完全匹配且时间逻辑合法
            LocalDateTime.parse(timeStr, formatter);
            return true;
        } catch (DateTimeParseException e) {
            // 格式不匹配或时间不合法（如 2月30日）会走到这里
            return false;
        }
    }
}
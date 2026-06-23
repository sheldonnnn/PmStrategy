package com.cmbc.mds.forex.common.utils;

import org.springframework.util.StringUtils;

public class SymbolUtils {
    public static String formatSymbol(String rawSymbol) {
        if (!StringUtils.hasText(rawSymbol)) return rawSymbol;
        rawSymbol = rawSymbol.trim().toUpperCase();
        // 如果长度为6且没有斜杠，则插入斜杠 (例如: USDCNY -> USD/CNY)
        if (rawSymbol.length() == 6 && !rawSymbol.contains("/")) {
            return rawSymbol.substring(0, 3) + "/" + rawSymbol.substring(3);
        }
        return rawSymbol;
    }
}

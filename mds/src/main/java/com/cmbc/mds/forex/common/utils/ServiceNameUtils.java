package com.cmbc.mds.forex.common.utils;

public class ServiceNameUtils {
    public static String getPrefixServiceName(String serviceName) {
        if (serviceName == null) {
            return null;
        }
        int length;
        // 德商行，特殊情况处理
        if (serviceName.contains("COBA")) {
            length = 4;
        } else {
            length = serviceName.indexOf("-");
            if (length < 0) {
                length = serviceName.length();
            }
        }

        return serviceName.substring(0, length).toUpperCase();
    }

    public static String getPrefixTransportName(String transport) {
        if (transport == null) {
            return null;
        }
        return transport.replace("_MARKET_DATA", "");
    }
}

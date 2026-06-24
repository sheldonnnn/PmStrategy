package com.cmbc.oms.domain.order.model.enums;

public enum OrderPriceBaseEnum {
    START_ORDER("A", "激进", "对手价第一档行情"),
    FS_ORDER("D", "防守", "场内品种计算最优买卖价差\n" +
            "1、递延合约，价差≤TD_SPREAD(默认值0.2元/克)，下单对手价；\n" +
            "2、递延合约价差>TD_SPREAD按市场中间价挂单；\n" +
            "3、期货合约买卖价差≤SHF_SPREAD(默认值0.1元/克)，直接按对手价下单;\n" +
            "4、期货价差大于spread按市场中间价挂单；\n" +
            "5、场外品种只能吃单不涉及"),
    FS_ZHONG_ORDER("N", "中性", "场内品种排队价格选择：最优档；次优档；\n" +
            "场外只能吃单不涉及");

    private final String code;
    private final String description;
    private final String returnMessage;

    OrderPriceBaseEnum(String code, String description, String returnMessage) {
        this.code = code;
        this.description = description;
        this.returnMessage = returnMessage;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
    public String getReturnMessage() { return returnMessage; }

    public static String fromTradeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        for (OrderPriceBaseEnum variety : values()) {
            if (variety.getCode().equals(code.trim())) {
                return variety.getDescription() + "模式";
            }
        }
        return null;
    }
}

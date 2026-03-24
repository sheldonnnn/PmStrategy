package com.cmbc.oms.cash;

import org.springframework.stereotype.Component;

/**
 * 积存金自动平盘策略特定的头组路由实现。
 * 后续可根据 businessType、domesticType 等将其路由至指定的头寸组中。
 */
@Component
public class MagpHedgeFolderRouter implements IPositionFolderRouter {

    public static final String FOLDER_MAGP_HEDGE = "MagpHedge";

    @Override
    public boolean supports(OrderUpdate event) {
        // 初步写死：可以根据业务线的特殊标识来判断
        // 假设业务标识 businessType 为 "AccumulativeGold" 或等价标识，此处根据需要进行扩充
        // 暂且认为当前只有积存金业务的话，默认无条件支持，或者可添加逻辑：
        // return "AccumulativeGold".equals(event.getBusinessType());
        return true; 
    }

    @Override
    public String route(OrderUpdate event) {
        // 满足支持条件后，固定落到积存金平盘对应头组 "MagpHedge" 中
        return FOLDER_MAGP_HEDGE;
    }

}

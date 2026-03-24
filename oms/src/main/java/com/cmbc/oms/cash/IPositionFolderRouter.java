package com.cmbc.oms.cash;

/**
 * 头寸组 (Folder) 路由接口。
 * 用于根据订单事件或请求等上下文信息，动态决定其应该归属的资金头寸管理组 (folderId)。
 */
public interface IPositionFolderRouter {

    /**
     * 判断当前路由规则是否支持处理该订单事件
     */
    boolean supports(OrderUpdate event);

    /**
     * 根据订单事件计算出对应的 folderId
     */
    String route(OrderUpdate event);

}

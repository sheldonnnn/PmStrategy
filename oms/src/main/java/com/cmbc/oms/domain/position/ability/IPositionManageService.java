package com.cmbc.oms.domain.position.ability;

import com.cmbc.oms.domain.event.RspTraderPosiAllQryEvent;
import com.cmbc.oms.domain.event.RspTraderQryStorageEvent;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.entity.NewOrder;

/**
 * @author chendaqian
 * @date 2026/2/25
 * @time 18:20
 * @description
 */
public interface IPositionManageService {

    // 订单持仓校验
    public void newOrderCheck(NewOrder order);

    public void freezePosition(NewOrder order);

    /**现货转换为持仓信息*/
    public void spotToPosition(RspTraderQryStorageEvent rspTraderQryStorage, String type);

    /**
     * 合约转换为持仓信息
     * @param rspTraderPosiAllQry 交易员持仓查询响应对象
     * @param type 操作类型，用于标识缓存操作类型
     */
    public void contractToPosition(RspTraderPosiAllQryEvent rspTraderPosiAllQry, String type);

    /** 成交事件更新持仓 */
    public void updatePositionByOrder(ExecutionReport event);
}

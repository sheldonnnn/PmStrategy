package com.cmbc.oms.domain.facade.apama;

/**
 * @author chendaqian
 * @date 2026/3/11
 * @time 16:41
 * @description 设置apama库存/持仓查询响应事件监听器
 */
public interface SetApamaPositionEventListener {
    void setRspTraderQryStorageEventListener();
    
    void setRspTraderPosiAllQryEventListener();
}

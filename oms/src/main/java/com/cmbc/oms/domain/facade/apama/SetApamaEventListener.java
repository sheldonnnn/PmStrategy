package com.cmbc.oms.domain.facade.apama;

/**
 * @author chendaqian
 * @date 2026/2/4
 * @time 18:07
 * @description 设置Apama事件监听器
 */
public interface SetApamaEventListener {
    public void orderUpdateEventListener(Class clazz);
    
    // 异常订单事件监听
    public void exceptionOrderEventListener(Class clazz);
    
}

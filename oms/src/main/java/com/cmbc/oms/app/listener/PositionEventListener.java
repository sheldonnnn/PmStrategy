package com.cmbc.oms.app.listener;

import com.cmbc.oms.domain.facade.apama.SetApamaPositionEventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @author chendaqian
 * @date 2026/3/10
 * @time 16:42
 * @description 持仓事件监听器
 */
@Component
public class PositionEventListener {
    @Autowired
    private SetApamaPositionEventListener setApamaEventListener;

    @PostConstruct
    public void onApplicationEvent() {
        // 设置查询持仓返回事件监听 现货
        setApamaEventListener.setRspTraderQryStorageEventListener();
        // 设置查询持仓返回事件监听 期货
        setApamaEventListener.setRspTraderPosiAllQryEventListener();
        
        // 发送查询持仓\库存事件 通过其他方式触发，不直接通过监听器
        //positionManageService.initPosition();
    }
}

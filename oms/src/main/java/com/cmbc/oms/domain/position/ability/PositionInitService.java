package com.cmbc.oms.domain.position.ability;

import com.cmbc.oms.domain.event.ReqTraderPosiAllQryEvent;
import com.cmbc.oms.domain.event.ReqTraderQryStorageEvent;
import com.cmbc.oms.domain.event.RspTraderPosiAllQryEvent;
import com.cmbc.oms.domain.event.RspTraderQryStorageEvent;
import com.cmbc.oms.domain.facade.apama.SendEventToApama;
import com.cmbc.oms.domain.order.ability.factory.ReqTraderPosiAllQryEventFactory;
import com.cmbc.oms.infrastructure.cache.BasicParamCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * @author chendaqian
 * @date 2026/3/19
 * @time 10:25
 * @description 初始化持仓服务 - 专门处理初始化场景
 */
@Service
public class PositionInitService {
    private static final Logger log = LoggerFactory.getLogger(PositionInitService.class);

    private static final String INIT_POSITION = "INIT_POSITION";
    private static final String COMPARE_POSITION = "COMPARE_POSITION";

    @Autowired
    private IPositionManageService positionManageService;

    @Autowired
    private SendEventToApama sendEventToApama;
    @Autowired
    private BasicParamCacheManager basicParamCacheManager;
    @Autowired
    private ReqTraderPosiAllQryEventFactory factory;
    @Autowired
    private PositionRequestManager positionRequestManager;

    @PostConstruct
    public void init() {
        // 初始化持仓，用于应用启动前 用户管理台已登录情况
        String traderNo = basicParamCacheManager.getDimpleUserInfo();
        initializePosition(traderNo);
    }

    // 执行初始化逻辑
    public void initializePosition(String traderNo) {
        log.info("开始初始化持仓管理");
        // 创建初始化请求并注册
        if(traderNo == null){
            log.error("dimple用户信息为空，请检查配置");
            return;
        }
        ReqTraderQryStorageEvent qryStorageEvent = factory.createReqTraderQryStorageEvent(traderNo, "INIT_");
        // 为初始化请求添加特殊标识
        String initStorageRequestId = qryStorageEvent.getUniqueId();
        positionRequestManager.addInitRequestId(initStorageRequestId);
        sendEventToApama.sendEventToApama(qryStorageEvent);
        log.info("初始化现货持仓查询请求已发送，ID: {}", initStorageRequestId);

        ReqTraderPosiAllQryEvent qryPosiAllQryEvent = factory.createReqTraderPosiAllQryEvent(traderNo, "INIT_");
        // 为初始化请求添加特殊标识
        String initPosiRequestId = qryPosiAllQryEvent.getUniqueId();
        positionRequestManager.addInitRequestId(initPosiRequestId);
        sendEventToApama.sendEventToApama(qryPosiAllQryEvent);
        log.info("初始化期货持仓查询请求已发送，ID: {}", initPosiRequestId);
    }

    // 处理初始化场景的现货持仓响应
    public void handleSpotPositionResponse(RspTraderQryStorageEvent event) {
        positionManageService.spotToPosition(event, INIT_POSITION);
    }

    // 处理初始化场景的期货持仓响应
    public void handleContractPositionResponse(RspTraderPosiAllQryEvent event) {
        positionManageService.contractToPosition(event, INIT_POSITION);
    }
}

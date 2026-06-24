package com.cmbc.oms.domain.position.ability;

import com.cmbc.oms.controller.dto.DimpleUserReq;
import com.cmbc.oms.infrastructure.cache.BasicParamCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author chendaqian
 * @date 2026/4/13
 * @time 11:34
 * @description dimple账号登录管理
 */
@Service
@Slf4j
public class DimpleUserManageService {
    @Autowired
    private BasicParamCacheManager basicParamCacheManager;

    @Autowired
    private PositionInitService positionInitService;

    /**
     * 观礼台更新dimple用户信息
     *
     * @param request
     */
    public void lisenUpadateDimpleUserImfo(DimpleUserReq request) {
        // 置空 -- 用户信息缓存
        if(!basicParamCacheManager.userInfoCache.contains(request.getDimpleUser())){
            // 插入对应的用户信息
            basicParamCacheManager.userInfoCache.add(request.getDimpleUser());
        }
        // 发送合约信息查询请求 todo
        // 发送期货、库存信息查询请求
        positionInitService.initializePosition(basicParamCacheManager.userInfoCache.getFirst());
    }

    /**
     * 管理台处理登出事件
     * @param request
     */
    public void lisenUpadateCacheLogout(DimpleUserReq request) {
        // 置空 -- 用户信息缓存
        basicParamCacheManager.userInfoCache.remove(request.getDimpleUser());
    }

    /**
     * 管理台更新合约信息
     * @param 
     */
    public void updateContractInfo() {
        // 置空 -- 用户信息缓存
        basicParamCacheManager.lisenUpadateContactInfo();
    }
}

package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;
import lombok.Data;

/**
 * @author chendaqian
 * @date 2026/3/31
 * @time 16:42
 * @description dimple帐户登出事件
 */
@Data
@EventField(name = "com.finesys.client.ClientLogoutReq")
public class DimpleUserLogout {
    /**
     * 用户ID
     */
    @EventField(name = "userID")
    private String userName;
    /**
     * 登录事件的ID
     */
    @EventField(name = "uniqueID")
    private String uniqueID;
    /**
     * dimple用户名
     */
    @EventField(name = "TraderNo")
    private String dimpleUser;
}

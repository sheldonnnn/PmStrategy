package com.cmbc.oms.domain.event;

import com.alibaba.fastjson.annotation.JSONField;
import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;
import lombok.Data;

import java.util.Date;

/**
 * @author chendaqian
 * @date 2026/3/31
 * @time 16:43
 * @description dimple帐户登录事件
 */
@Data
@EventField(name = "com.finesys.client.ClientLoginReq")
public class DimpleUserLogIn {
    /**
     * 用户ID
     */
    @EventField(name = "userID")
    private String userId;
    /**
     * 登录事件的ID
     */
    @EventField(name = "uniqueID")
    private String uniqueID;
    /**
     * dimple用户名
     */
    @EventField(name = "TraderNo")
    @JSONField(name = "traderNo")
    private String dimpleUser;
    /**
     * dimple密码
     */
    @EventField(name = "password")
    private String dimplePass;
    /**
     * 会员号
     */
    private String memberId;
    /**
     * 客户号
     */
    private String clientId;
    /**
     * dimple登录状态
     */
    @JSONField(name = "isLoginCode")
    private String loginStatus;

    /**
     * 登录登出标记
     */
    private String loginOrLogout;
    /**
     * dimple登录时间
     */
    private Date loginTime;
    /**
     * 用户名
     */
    private String userName;

    private String userList;
}

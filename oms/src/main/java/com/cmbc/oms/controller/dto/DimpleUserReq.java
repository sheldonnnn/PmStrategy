package com.cmbc.oms.controller.dto;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.Date;

/**
 * Dimple帐号实体
 * @Author: ZH
 * @Date: Created on 15:29 2018/2/9.
 */
@Data
public class DimpleUserReq {

    /**
     * 用户ID
     */
    private String userId;
    /**
     * 登录事件的ID
     */
    private String uniqueID;
    /**
     * dimple用户名
     */
    @JSONField(name = "traderNo")
    private String dimpleUser;
    /**
     * dimple密码
     */
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

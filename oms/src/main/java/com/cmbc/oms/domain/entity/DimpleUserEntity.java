package com.cmbc.oms.domain.entity;

import lombok.Data;

import java.util.Date;

/**
 * dimple帐号实体
 * @Author: ZH
 * @Date: Created on 15:29 2018/2/9.
 */
@Data
public class DimpleUserEntity{
    private String id;
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

    private String remarks;

    private String delFlag;

    private String createId;

    private String updateId;

    private Date createDate;

    private Date updateDate;
}

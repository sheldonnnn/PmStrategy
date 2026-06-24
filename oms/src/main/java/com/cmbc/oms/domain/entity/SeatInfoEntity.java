package com.cmbc.oms.domain.entity;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 席位信息
 *
 * @author sunits
 * @email 112433877@qq.com
 * @date 2022-04-13 09:51:49
 */
@Data
public class SeatInfoEntity {
    private String id;

    private String remarks;

    private String delFlag;

    private String createId;

    private String updateId;
    private Date createDate;

    private Date updateDate;

    //dimple用户
    private String dimpleUser;
    //交易所代码
    private String exchCode;
    //交易所名称
    private String exchName;
    //席位号
    private String seatNo;
    //客户号
    private String clientId;
    //扩展参数
    private String extraParam;
    //合约号 抽出项
    private String contractId;
    //合约号列表
    private List<String> symbols;
    //查询席位时，合约号是否为必须参数 0：非必须
    private String contractIsMust;
}

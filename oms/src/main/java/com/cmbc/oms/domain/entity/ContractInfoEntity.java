package com.cmbc.oms.domain.entity;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class ContractInfoEntity {

    private String exchCode;
    private String exchName;
    private String contractID;
    private String currency;
    private String contractName;
    private String varietyId;
    private BigDecimal unit;
    private String measureUnit;
    private double tick;
    private long maxHand;
    private long minHand;
    private long maxMarketOrderVolume;
    private long minMarketOrderVolume;
    private double refPrice;
    private String contractStatus;
    private String endDeliveryDate;
    private double riseLimit;
    private double fallLimit;
    private Integer showOrder;
    private String alias;
    private boolean isSuccess;
    private boolean bIsLast;
    private String contractType;
    private List<String> varietyIds;
    private String subscriptionStatus;
    private String extraParams;
    private String groupByExch;
    private String domesticType;
    private String exchCodeStr;
    private String selectType;
    @JSONField(
            serialize = false,
            deserialize = false
    )
    private String[] exchCodeArray;
    private String isHistoryContract;
    private int accuracy;
    private int stepPosition;
    private String property1;
    private String property2;
    private String property3;

    private String remarks;

    private String delFlag;

    private String createId;

    private String updateId;

    private Date createDate;

    private Date updateDate;
}

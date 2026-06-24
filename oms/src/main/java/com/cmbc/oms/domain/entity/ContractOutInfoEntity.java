package com.cmbc.oms.domain.entity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 境外合约实体
 */
@Data
public class ContractOutInfoEntity {
    private String id;

    private String contractId;

    private String contractName;

    private String contractType;

    private String varietyid;

    private BigDecimal unit;

    private String measureUnit;

    private double tick;

    private String currency;

    private int accuracy;

    private int stepPosition;
    private String subscriptionStatus;

    private String property1;

    private String property2;

    private String property3;

    private String operation;

    private Map<String, String> extraParas;
    public List<String> getIds;
    private String createBy;
    private String updateBy;
    private Date createDate;
    private Date updateDate;
}

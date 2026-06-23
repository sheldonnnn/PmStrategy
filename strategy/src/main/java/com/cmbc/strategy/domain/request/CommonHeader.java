package com.cmbc.strategy.domain.request;

import lombok.Data;

@Data
public class CommonHeader {
    private String seqNum; // 流水号
    private String operatorId;// 操作人员ID
    private String operatorName;// 操作人员名称
    private String domainCode; // 多租户编码
    private String authBody;//鉴权密文


}

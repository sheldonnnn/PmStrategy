package com.cmbc.oms.domain.position.model.entity;

import lombok.Data;

/**
 * @author chendaqian
 * @date 2026/3/31
 * @time 16:48
 * @description 交易员账户信息
 */
@Data
public class TraderNoClientMember {
    private String traderNo;/*委托席位号 必填;*/
    private String memberId;/*会员号 必填;*/
    private String clientId;/*客户号 必填;*/
    private String exchCode;/*交易所代码 必填;*/
}

package com.cmbc.mds.forex.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

// 订单拒单事件
@Setter
@Getter
@ToString
@AllArgsConstructor
public class OrderRejectTypeEvent {

    private String providerName; // 外资行名称
    private String symbol; // 币种
    private String type; // 处理类型
    private String delFalg; // 是否删除
    Map<String, String> params; // 扩展字段

}

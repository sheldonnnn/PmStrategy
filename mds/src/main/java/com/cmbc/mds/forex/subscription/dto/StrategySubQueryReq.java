package com.cmbc.mds.forex.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StrategySubQueryReq {
    private String traderId;   // 可选：用于鉴权
    private String strategyId; // 必填
}
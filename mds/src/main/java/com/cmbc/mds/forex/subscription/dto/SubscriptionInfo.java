package com.cmbc.mds.forex.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubscriptionInfo {
    private String provider;
    private String currencyPair;
    private String topicKey; // 原始Key，方便调试
}
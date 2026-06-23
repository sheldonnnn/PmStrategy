package com.cmbc.mds.forex.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TraderSubReq {
    private String source;
    private String provider;
    private String symbol;
    private String traderId;
}

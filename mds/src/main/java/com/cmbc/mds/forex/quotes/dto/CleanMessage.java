package com.cmbc.mds.forex.quotes.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CleanMessage extends BaseMsg {
    private String provider;
    private String symbol;

    private Depth data;

    @Override public String toString() { return "CleanMsg(" + data + ")"; }
}

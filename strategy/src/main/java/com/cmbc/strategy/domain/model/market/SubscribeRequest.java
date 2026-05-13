package com.cmbc.strategy.domain.model.market;

import lombok.Data;

import java.util.List;

@Data
public class SubscribeRequest {
    private String symbol;
    private String exchId;
    private String counterParty;

}

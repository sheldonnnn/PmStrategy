package com.cmbc.strategy.domain.model.market;

import lombok.Data;

import java.util.List;

@Data
public class SubscribeRequest {

    private List<String> sources;
    private List<String> providers;
    private String symbol;
    private String subscribeType;
    private String instanceId;

}

package com.cmbc.strategy.domain.model.market;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class PriceProviderInfo {

    private String providerName;

    // 对应 Apama: sequence<integer> quantity
    private List<BigDecimal> quantity = new ArrayList<>();

    // 对应 Apama: dictionary<string,string> priceAttributes
    private Map<String, String> priceAttributes = new HashMap<>();

}

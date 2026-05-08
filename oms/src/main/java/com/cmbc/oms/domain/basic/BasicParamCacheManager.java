package com.cmbc.oms.domain.basic;

import com.cmbc.oms.domain.order.model.ContractInfoBasic;

import java.util.HashMap;
import java.util.Map;

public class BasicParamCacheManager {

    private final Map<String, ContractInfoBasic> contractInfoCache = new HashMap<>();
    public Map<String, ContractInfoBasic> getContractInfo() {
        return contractInfoCache;
    }

}

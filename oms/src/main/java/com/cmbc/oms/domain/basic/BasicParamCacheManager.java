package com.cmbc.oms.domain.basic;

import com.cmbc.oms.domain.order.model.ContractInfoBasic;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
@Service
public class BasicParamCacheManager {

    private final Map<String, ContractInfoBasic> contractInfoCache = new HashMap<>();
    public Map<String, ContractInfoBasic> getContractInfo() {
        return contractInfoCache;
    }

    @PostConstruct
    private void init(){
        //加载合约信息到contractInfoCache缓存中
    }

}

package com.cmbc.mds.forex.quotes.adapter.impl;

import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.MQTranserBean;
import org.springframework.stereotype.Component;

@Component("Adapter_UBS")
public class UBSQuoteAdapter extends BaseMQTranserAdapter {
    @Override
    protected void doProviderSpecificHandle(Depth depth, MQTranserBean source) {
        if (log.isDebugEnabled()) {
            log.debug("执行 UBS 特定逻辑处理: Depth={}", depth.toString());
        }
    }
}
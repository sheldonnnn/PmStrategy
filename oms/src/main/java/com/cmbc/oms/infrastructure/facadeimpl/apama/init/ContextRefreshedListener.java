package com.cmbc.oms.infrastructure.facadeimpl.apama.init;

import com.cmbc.oms.infrastructure.facadeimpl.apama.CorrelatorUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * @Author: Cly
 * @Date: 2026/01/23  17:32
 * @Description:
 */
@Component
public class ContextRefreshedListener implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private CorrelatorUtils correlatorUtils;

    private String correlatorName = "PmStrategy";

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        System.out.println("=======================ContextRefreshedListener=======================");
        correlatorUtils.connectToOmsCorrelator(correlatorName);
        correlatorUtils.connectToPmCorrelator(correlatorName);
    }
}

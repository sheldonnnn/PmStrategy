package com.cmbc.mds.ksd.runner;

import com.cmbc.mds.ksd.service.KsdService;
import com.cmbc.mds.ksd.service.KsdService.GatewayResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class KsdAutoStartRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(KsdAutoStartRunner.class);

    private final KsdService ksdService;

    @Value("${ksd.gateway.check-on-start:true}")
    private boolean checkOnStart;

    @Value("${ksd.gateway.start-on-startup:false}")
    private boolean startOnStartup;

    public KsdAutoStartRunner(KsdService ksdService) {
        this.ksdService = ksdService;
    }

    @Override
    public void run(String... args) {
        if (!checkOnStart && !startOnStartup) {
            logger.info("KsdGateway startup check is disabled.");
            return;
        }

        if (checkOnStart) {
            GatewayResponse status = ksdService.status();
            logger.info("KsdGateway status check result: statusCode={}, body={}", status.statusCode(), status.body());
        }

        if (startOnStartup) {
            GatewayResponse start = ksdService.start();
            logger.info("KsdGateway start request result: statusCode={}, body={}", start.statusCode(), start.body());
        }
    }
}

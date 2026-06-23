package com.cmbc.strategy;

import com.cmbc.oms.app.service.OrderUpstreamAppService;
import com.cmbc.oms.controller.MgapHedgingOrder;
import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableEncryptableProperties
@MapperScan(basePackages = {"com.cmbc.strategy.dao", "com.cmbc.oms.infrastructure.dao"})
@ComponentScan(basePackages = {"com.finesys", "com.cmbc.strategy", "com.cmbc.mds", "com.cmbc.oms"})
public class PmStrategyApplication {

    public static void main(String[] args) {
        try {
            ConfigurableApplicationContext applicationContext = SpringApplication.run(PmStrategyApplication.class);

            // 获取需要的bean进行测试
            MgapHedgingOrder mgapHedgingOrder = 
                    (MgapHedgingOrder) applicationContext.getBean("mgapHedgingOrder");
            OrderUpstreamAppService orderUpstreamAppService = 
                    (OrderUpstreamAppService) applicationContext.getBean("orderUpstreamAppService");
            // SendEventToApamaService service = (SendEventToApamaService) applicationContext.getBean("sendEventToApamaService");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

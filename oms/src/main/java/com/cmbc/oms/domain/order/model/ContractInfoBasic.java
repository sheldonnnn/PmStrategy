package com.cmbc.oms.domain.order.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ContractInfoBasic {

    private String symbol;
    private String variety;

        private BigDecimal unit;

        private String measureUnit;

        private double tick;

        private String exchCode;

        private String domesticType;

        private String inventoryType;

        private String endDeliveryDate;

        private boolean isHistoryContract;

        private String currency;

        private Integer accuracy;

        private Integer stepPosition;

        private String straightDiscForkType;

        private String straightDisc;

        private String forkPlate;

        private Map<String, String> extraParas;

        private String contractType;

}

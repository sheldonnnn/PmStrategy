package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ContractInfoBasic {
    @EventField(name = "symbol", order = 1)
    private String symbol;
    @EventField(name = "varietyId", order = 2)
    private String varietyId;
    @EventField(name = "unit", order = 3)
    private BigDecimal unit;
    @EventField(name = "measureUnit", order = 4)
    private String measureUnit;
    @EventField(name = "tick", order = 5)
    private double tick;
    @EventField(name = "exchCode", order = 6)
    private String exchCode;
    @EventField(name = "domesticType", order = 7)
    private String domesticType;
    @EventField(name = "inventoryType", order = 8)
    private String inventoryType;
    @EventField(name = "endDeliveryDate", order = 9)
    private String endDeliveryDate;
    @EventField(name = "isHistoryContract", order = 10)
    boolean isHistoryContract;
    @EventField(name = "currency", order = 11)
    private String currency;
    @EventField(name = "accuracy", order = 12)
    private Integer accuracy;
    @EventField(name = "stepPosition", order = 13)
    private Integer stepPosition;
    @EventField(name = "straightDiscForkType", order = 14)
    private String straightDiscForkType;
    @EventField(name = "straightDisc", order = 15)
    private String straightDisc;
    @EventField(name = "forkPlate", order = 16)
    private String forkPlate;
    @EventField(name = "extraParas", order = 17)
    private Map<String, String> extraParas;

    private String contractType; //todo:目前未赋值

    // 由dimple转换
    public ContractInfoBasic transferContractInfo() { return new ContractInfoBasic(); }
}

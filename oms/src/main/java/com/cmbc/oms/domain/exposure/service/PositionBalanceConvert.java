package com.cmbc.oms.domain.exposure.service;

import com.cmbc.oms.domain.exposure.entity.PositionBalanceEntity;
import com.cmbc.oms.domain.exposure.model.PositionSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PositionBalanceConvert {

    PositionSnapshot toBo(PositionBalanceEntity positionBalanceEntity);
//    @Mapping(target = "tradeDate", expression = "java(java.time.LocalDate.now())", dateFormat = "yyyyMMdd")
    @Mapping(target = "updateTime", dateFormat = "yyyy-MM-dd HH:mm:ss.SSS")
    @Mapping(target = "createTime", dateFormat = "yyyy-MM-dd HH:mm:ss.SSS")
    @Mapping(target = "systemId", constant = "PM")
    PositionBalanceEntity toEntity(PositionSnapshot positionSnapshot);

}

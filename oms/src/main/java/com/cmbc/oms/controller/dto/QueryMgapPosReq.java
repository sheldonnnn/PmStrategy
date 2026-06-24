package com.cmbc.oms.controller.dto;

import lombok.Data;

/**
 * dimple帐号实体
 * @Author: ZH
 * @Date: Created on 15:29 2018/2/9.
 */
@Data
public class QueryMgapPosReq {

    /**
     * 汇率折算币种
     */
    private String fxSymbol;
}

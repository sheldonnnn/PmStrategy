package com.cmbc.mds.forex.quotes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 封装特定价格档位的汇总数量以及原始提供商数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceVolumeInfo {
    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 汇总的报价数量
     */
    private BigDecimal totalVolume;

    /**
     * 原始的分价源的数据列表
     */
    private List<ProviderInfo> providerData;
}

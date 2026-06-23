package com.cmbc.mds.forex.quotes.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ProviderInfo {
    private String quoteId; // 报价id
    private String source; // 通道
    private String provider; // 报价方
    private String sourceProviderKey;
    private List<BigDecimal> quantity; // 报价量

    private String transactionTime; // 报价时间
    Map<String,String> priceAttributes; // 额外参数

    /**
     * 统一控制底层 Map 的 Key 生成规则
     * 使用 "." 进行拼接，例如 "FXALL.BARC" 或 "UBS"
     */
    public String buildSourceProviderKey() {
        if (provider != null && !provider.trim().isEmpty() && !provider.equals(source)) {
            return source + "." + provider;
        }
        return source;
    }
}

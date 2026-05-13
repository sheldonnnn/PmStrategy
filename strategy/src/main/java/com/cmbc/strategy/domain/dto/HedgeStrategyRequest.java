package com.cmbc.strategy.domain.dto;

package com.cmbc.strategy.domain.dto;

import lombok.Data;
import java.util.Map;

/**
 * 对冲策略请求对象
 */
@Data
public class HedgeStrategyRequest {

    private String strategyId;      // 此次运行的唯一Trace ID (如 UUID)

    private String symbolConfigId;  // 对应 GOLD_STRATEGY_TIME_RULES 表的关联Group ID

    private String instanceId;

    private String account;         // 外资行账户

    private String userName;

    private String traderNo;

    private String tagCode;

    private String tagName;

    private String exchId;          // 交易源

    private String counterParty;    // 交易对手方

    private Map<String, ClientMemberInfo> clientMemberInfo;

}

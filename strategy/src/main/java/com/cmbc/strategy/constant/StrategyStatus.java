package com.cmbc.strategy.constant;

import org.springframework.util.StringUtils;

public enum StrategyStatus {

    /**
     * 0: 已创建 (Created)
     * 含义：策略实例已生成，配置已加载，等待启动。
     */
    CREATED("0", "0", "运行中", "已创建"),

    /**
     * 1: 平盘条件验证中 (Monitoring)
     * 含义：[运行态] 策略正在监控头寸，等待触发阈值。
     */
    MONITOR("1", "0", "运行中", "平盘条件验证"),

    /**
     * 2: 平盘下单执行中 (Hedging)
     * 含义：[运行态] 触发了信号，正在执行算法拆单交易。
     */
    HEDGE("2", "0", "运行中", "平盘下单执行"),

    /**
     * 3: 追单处理中 (Chasing)
     * 含义：[运行态] 算法结束后的激进补单阶段。
     */
    CHASE("3", "0", "运行中", "追单处理"),

    /**
     * 4: 中止/暂停 (Paused)
     * 含义：[挂起态] 由【管理端】发起。
     * 动作：所有定时任务暂停，活跃挂单已撤销，但策略上下文(Context)和累计数据保留。
     * 下一步：可接收"恢复(Resume)"指令回到 MONITORING，或接收"停止(Stop)"指令彻底结束。
     */
    PAUSED("4", "2", "已暂停", "已中止(暂停)"),

    /**
     * 10: 停止 (Stopped)
     * 含义：[终态] 正常结束（如日终自动停止或人工点击结束）。
     * 动作：资源完全释放，无法恢复，只能重新创建。
     */
    STOPPED("10", "1", "已停止", "已停止"),

    /**
     * 99: 异常熔断 (Meltdown)
     * 含义：[终态] 系统自动触发的硬风控（如行情中断、价格剧烈偏离）。
     */
    MELTDOWN("99", "1", "已停止", "异常熔断");

    private final String code;
    private final String finCode; // 管理端运行事列状态码值
    private final String finDescription; // 管理端运行事例描述
    private final String description;

    public String getCode() { return code; }
    public String getFinCode() { return finCode; }
    public String getFinDescription() { return finDescription; }

    public String getDescription() { return description; }

    StrategyStatus(String i, String finCode, String finDescription, String description) {
        this.code = i;
        this.finCode = finCode;
        this.finDescription = finDescription;
        this.description = description;
    }

    /**
     * 根据状态码获取订单状态枚举
     *
     * @param code 状态码
     * @return 订单状态枚举
     */
    public static StrategyStatus fromStatusCode(String code) {
        for (StrategyStatus status : StrategyStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据状态码获取订单状态枚举
     *
     * @param code 状态码
     * @return 订单状态枚举
     */
    public static String fromFinStatusCode(String code) {
        if (!StringUtils.hasText(code)){
            return null;
        }
        for (StrategyStatus status : StrategyStatus.values()) {
            if (status.getCode().equals(code)) {
                return String.valueOf(status.getFinCode());
            }
        }
        return null;
    }

    /**
     * 是否处于"干活"状态
     */
    public boolean canTrade() { return this == HEDGE || this == CHASE; }
}

package com.cmbc.oms.cash;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 头寸日结余额表 (期初快照表) Entity
 * 对应数据库表: QUANT_POSITION_BALANCE
 * 
 * 架构规范要求：
 * 1. 联合唯一业务主键：dateStr + folderId + symbol
 * 2. 仅在日终跑批时（EOD Batch）做 Insert 操作，作为明日的期初数据。
 * 3. 实时交易时段绝对禁止 Update 该表。
 * 4. 严禁落库各类 Frozen (冻结) 内存态指标。
 */
@Data
public class PositionBalanceEntity {

    /** 自增主键 */
    private Long id;

    /** 
     * 归属交易日切片 (如 "2026-03-17")
     * 这使得系统拥有了时间机器能力，可查阅过往任意一天的确切静态期初。
     */
    private String dateStr;

    /** 头寸隔离组 ID */
    private String folderId;

    /** 
     * 头寸唯一标识符 (通常由 folderId + "_" + symbol 拼接而成)
     * 主要作为冗余字段，极大地方便外围系统进行外键关联或单表查询。
     */
    private String positionId;

    // ================= 物理持仓基础维度 =================
    
    /** 多头实际持仓手数 */
    private BigDecimal longQty = BigDecimal.ZERO;
    
    /** 空头实际持仓手数 */
    private BigDecimal shortQty = BigDecimal.ZERO;

    // ================= 衍生财务克重与金额 =================
    
    /** 多头实际克重 (手数 * 乘数) */
    private BigDecimal longWeight = BigDecimal.ZERO;
    
    /** 空头实际克重 (手数 * 乘数) */
    private BigDecimal shortWeight = BigDecimal.ZERO;
    
    /** 多头成本金额 (克重 * 成交均价) */
    private BigDecimal longAmount = BigDecimal.ZERO;
    
    /** 空头成本金额 (克重 * 成交均价) */
    private BigDecimal shortAmount = BigDecimal.ZERO;

    // ================= 防重与审计基座 =================
    
    /** 
     * 重要防重水位线：
     * 记录当跑批生成此条“期初余额”时，最新被包含进去的成交流水号是多少。
     * 用于系统异地恢复或由昨终重算时，安全拦截丢弃已经包在这个快照里的历史由于网络延迟乱序传来的流水。
     */
    private String watermarkMatchNo;

    /** 此条快照实体被跑批程序生成的物理时间 */
    private Date createTime;
}

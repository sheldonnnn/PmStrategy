package com.cmbc.oms.cash;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 资金头寸组 (Folder) 实体。
 * 一个头组下包含多个交易品种 (Symbol) 的持仓快照。
 */
public class FolderPosition {

    private final String folderId;

    // 该头组下的所有合约的头寸快照 (Key: symbol)
    private final Map<String, PositionSnapshot> symbolPositions = new ConcurrentHashMap<>();

    public FolderPosition(String folderId) {
        this.folderId = folderId;
    }

    public String getFolderId() {
        return this.folderId;
    }

    /**
     * 获取单合约快照（不存在则初始化并返回）
     */
    public PositionSnapshot getOrCreateSnapshot(String symbol) {
        return symbolPositions.computeIfAbsent(symbol,
                k -> new PositionSnapshot(folderId, symbol));
    }

    /**
     * 单独获取某一个合约的快照引用（不存在返回 null，用于只读场景提速）
     */
    public PositionSnapshot getSnapshot(String symbol) {
        return symbolPositions.get(symbol);
    }

    /**
     * 提取该头组下所有已有合约的只读头寸集合
     */
    public List<ReadOnlyPosition> getAllPositions() {
        return symbolPositions.values().stream()
                .map(snap -> {
                    // 只读锁定某一瞬间，防止拷贝数据时发生脏读（一半冻结合约一半真实持仓）
                    synchronized (snap) {
                        return new ReadOnlyPosition(
                                folderId, snap.getSymbol(),
                                snap.getLongQty(), snap.getShortQty(),
                                snap.getFrozenLongQty(), snap.getFrozenShortQty(),
                                snap.getLongWeight(), snap.getShortWeight(),
                                snap.getLongAmount(), snap.getShortAmount(),
                                snap.getFrozenLongWeight(), snap.getFrozenShortWeight(),
                                snap.getFrozenLongAmount(), snap.getFrozenShortAmount()
                        );
                    }
                }).collect(Collectors.toList());
    }

    /**
     * 汇总当前资金池（头组）下所有衍生合约总量的头寸。
     * 策略在不需要区分合约（如只看黄金大盘总多空）时调用。
     */
    public ReadOnlyPosition getTotalPosition() {
        BigDecimal totalLongWeight = BigDecimal.ZERO;
        BigDecimal totalShortWeight = BigDecimal.ZERO;
        BigDecimal totalLongAmount = BigDecimal.ZERO;
        BigDecimal totalShortAmount = BigDecimal.ZERO;
        // 冻结的因为目前仅有手数，我们可以暂且相加或者传0，这里如果为了风控保守起见可以合并，但按金融意义上最好传0或者扩充frozenWeight
        BigDecimal totalFrozenLong = BigDecimal.ZERO;
        BigDecimal totalFrozenShort = BigDecimal.ZERO;
        BigDecimal totalFrozenLongWeight = BigDecimal.ZERO;
        BigDecimal totalFrozenShortWeight = BigDecimal.ZERO;
        BigDecimal totalFrozenLongAmount = BigDecimal.ZERO;
        BigDecimal totalFrozenShortAmount = BigDecimal.ZERO;

        for (PositionSnapshot snap : symbolPositions.values()) {
            synchronized (snap) {
                totalLongWeight = totalLongWeight.add(snap.getLongWeight());
                totalShortWeight = totalShortWeight.add(snap.getShortWeight());
                totalLongAmount = totalLongAmount.add(snap.getLongAmount());
                totalShortAmount = totalShortAmount.add(snap.getShortAmount());

                totalFrozenLong = totalFrozenLong.add(snap.getFrozenLongQty());
                totalFrozenShort = totalFrozenShort.add(snap.getFrozenShortQty());
                
                totalFrozenLongWeight = totalFrozenLongWeight.add(snap.getFrozenLongWeight());
                totalFrozenShortWeight = totalFrozenShortWeight.add(snap.getFrozenShortWeight());
                totalFrozenLongAmount = totalFrozenLongAmount.add(snap.getFrozenLongAmount());
                totalFrozenShortAmount = totalFrozenShortAmount.add(snap.getFrozenShortAmount());
            }
        }

        // 返回一个 symbol 为 "TOTAL" 的聚合后只读副本
        // 注：不同合约的手数 (qty) 和冻结手数相加无业务意义，此处仅为不产生 NullPointerException
        return new ReadOnlyPosition(
                folderId, "TOTAL",
                BigDecimal.ZERO, BigDecimal.ZERO,      // qty
                totalFrozenLong, totalFrozenShort,     // frozen qty
                totalLongWeight, totalShortWeight,     // weight
                totalLongAmount, totalShortAmount,     // amount
                totalFrozenLongWeight, totalFrozenShortWeight,
                totalFrozenLongAmount, totalFrozenShortAmount
        );
    }
}

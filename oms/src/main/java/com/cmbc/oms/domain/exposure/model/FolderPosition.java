package com.cmbc.oms.domain.exposure.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 资金头寸组 (Folder) 实体。
 * 一个头组下包含多个交易品种 (Symbol) 的持仓快照。
 */
@Data
public class FolderPosition {

    private final String folderId;
    // 该头组下的所有合约的头寸快照 (Key: symbol)
    private final Map<String, PositionSnapshot> symbolPositions = new ConcurrentHashMap<>();

    public FolderPosition(String folderId) {
        this.folderId = folderId;
    }

    /**
     * 获取单合约快照（不存在则初始化并返回）
     */
    public PositionSnapshot getOrCreateSnapshot(String symbol,BigDecimal unit, String domesticType) {
        return symbolPositions.computeIfAbsent(symbol,
                k -> new PositionSnapshot(folderId, symbol, unit, domesticType));
    }

    /**
     * 单独获取某一个合约的快照引用（不存在返回 null，用于只读场景提速）
     */
    public PositionSnapshot getSnapshot(String symbol) {
        return symbolPositions.get(symbol);
    }

    public void putSnapShot(PositionSnapshot snapShot){
        symbolPositions.put(snapShot.getSymbol(),snapShot);
    }

    /**
     * 汇总当前资金池（头组）下所有衍生合约总量的头寸。
     * 策略在不需要区分合约（如只看黄金大盘总多空）时调用。
     */
    public PositionSnapshot getTotalPosition() {
        PositionSnapshot totalPosition = new PositionSnapshot(folderId, "TOTAL", BigDecimal.ONE,"");
        for (PositionSnapshot snap : symbolPositions.values()) {
            synchronized (snap) {
                totalPosition.setLongWeight(totalPosition.getLongWeight().add(snap.getLongWeight()));
                totalPosition.setShortWeight(totalPosition.getShortWeight().add(snap.getShortWeight()));
                totalPosition.setLongAmount(totalPosition.getLongAmount().add(snap.getLongAmount()));
                totalPosition.setShortAmount(totalPosition.getShortAmount().add(snap.getShortAmount()));
                totalPosition.setFrozenLongWeight(totalPosition.getFrozenLongWeight().add(snap.getFrozenLongWeight()));
                totalPosition.setFrozenShortWeight(totalPosition.getFrozenShortWeight().add(snap.getFrozenShortWeight()));
                totalPosition.setFrozenLongAmount(totalPosition.getFrozenLongAmount().add(snap.getFrozenLongAmount()));
                totalPosition.setFrozenShortAmount(totalPosition.getFrozenShortAmount().add(snap.getFrozenShortAmount()));

            }
        }
        return totalPosition;

    }
}

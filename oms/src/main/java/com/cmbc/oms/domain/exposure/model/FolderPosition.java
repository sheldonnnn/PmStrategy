package com.cmbc.oms.domain.exposure.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FolderPosition {
    
    private final String folderId;
    private final Map<String, PositionSnapshot> symbolPositions= new ConcurrentHashMap<>();
    
    public FolderPosition(String folderId) { this.folderId = folderId; }
    
    public PositionSnapshot getSnapShot(String symbol) { return symbolPositions.get(symbol); }
    
    //获取单合约快照
    public PositionSnapshot getOrCreateSnapShot(String symbol, BigDecimal unit, String domesticType) {
        return symbolPositions.computeIfAbsent(symbol, (String key) -> new PositionSnapshot(folderId, symbol, unit, domesticType));
    }
    
    public void putSnapShot(PositionSnapshot positionSnapshot) {
        symbolPositions.put(positionSnapshot.getSymbol(), positionSnapshot);
    }
    
    public PositionSnapshot getTotalPosition(){
        PositionSnapshot totalPosition = new PositionSnapshot(folderId, "Total",BigDecimal.ONE, "");
        
        for (PositionSnapshot positionSnapshot : symbolPositions.values()) {
            synchronized (positionSnapshot){
                totalPosition.setLongWeight(totalPosition.getLongWeight().add(positionSnapshot.getLongWeight()));
                totalPosition.setShortWeight(totalPosition.getShortWeight().add(positionSnapshot.getShortWeight()));
                totalPosition.setLongAmount(totalPosition.getLongAmount().add(positionSnapshot.getLongAmount()));
                totalPosition.setShortAmount(totalPosition.getShortAmount().add(positionSnapshot.getShortAmount()));
                totalPosition.setFrozenLongWeight(totalPosition.getFrozenLongWeight().add(positionSnapshot.getFrozenLongWeight()));
                totalPosition.setFrozenShortWeight(totalPosition.getFrozenShortWeight().add(positionSnapshot.getFrozenShortWeight()));
                totalPosition.setFrozenLongAmount(totalPosition.getFrozenLongAmount().add(positionSnapshot.getFrozenLongAmount()));
                totalPosition.setFrozenShortAmount(totalPosition.getFrozenShortAmount().add(positionSnapshot.getFrozenShortAmount()));
            }
        }
        return totalPosition;
    }
    
    /**
     * 统计积存金平盘(量化平盘头寸信息)
     * @return
     */
    public Map<String, PositionSnapshot> getSymbolPositions() { return symbolPositions; }
    
    public String getFolderId() { return folderId; }
}

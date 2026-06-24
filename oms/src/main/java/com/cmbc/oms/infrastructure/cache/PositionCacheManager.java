package com.cmbc.oms.infrastructure.cache;

import com.cmbc.oms.domain.position.model.entity.Positions;
import com.cmbc.oms.domain.position.model.entity.TotalPosition;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author chendaqian
 * @date 2026/2/28
 * @time 15:18
 * @description 持仓缓存管理类
 */
@Service
@Data
public class PositionCacheManager {

    /**
     * 总持仓缓存，key: account+symbol
     * 存储账户和合约的总持仓信息，这是Apama脚本中实际使用的缓存
     */
    private final Map<String, TotalPosition> totalPositionCache = new ConcurrentHashMap<>();
    //总持仓管理缓存，key: account+symbol+flag(买卖方向)
    private final Map<String, Positions> totalPositionsManagerCache = new ConcurrentHashMap<>();

    // 明细持仓缓存，key:serviceID+account+symbol+userName+businessType+traderNo+flag(买卖方向)
    /**关键key值：服务id、账户、交易品种、前台用户、业务类型、交易员、买卖方向*/
    private final Map<String, Positions> positionCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, TotalPosition> compareTotalPositionCache = new ConcurrentHashMap<>();


    public void cacheCompareTotalPosition(String key, TotalPosition totalPosition){
        compareTotalPositionCache.put(key,totalPosition);
    }

    /**
     * 缓存总持仓信息
     * @param totalPosition 总持仓对象
     */
    public void cacheTotalPosition(String key, TotalPosition totalPosition) {
        totalPositionCache.put(key, totalPosition);
    }

    public void cacheTotalPositionManager(String key, Positions positions) {
        totalPositionsManagerCache.put(key, positions);
    }

    public void cachePosition(String key, Positions positions) {
        positionCache.put(key, positions);
    }

    public TotalPosition getCompareTotalPositionByKey(String key) {
        return compareTotalPositionCache.get(key);
    }


    /**
     * 根据账户和合约代码获取总持仓信息
     * 总持仓缓存，key: account+symbol
     * @return 总持仓对象，如果不存在返回null
     */
    public Positions getTotalPosition(String key,String side) {
        TotalPosition totalPosition = totalPositionCache.get(key);
        if (totalPosition == null) {
            return null;
        }else {
            if(BusinessConstant.BUY_SIDE.equals(side)){
                return totalPosition.getBuyPos();
            }else{
                return totalPosition.getSellPos();
            }
        }
    }


    // 清理比较持仓缓存 （每次定时比对前）
    public void clearCompareTotalPositionCache() { compareTotalPositionCache.clear(); }

    /**
     * 根据账户和合约代码获取总持仓信息
     * @return 总持仓对象，如果不存在返回null
     */
    public TotalPosition getTotalPosition(String key) { return totalPositionCache.get(key); }

    /**
     * 检查是否存在指定账户和合约的总持仓信息
     * @return 是否存在
     */
    public boolean hasTotalPosition(String key) { return totalPositionCache.containsKey(key); }

    /**
     * 移除指定账户和合约的总持仓信息
     */
    public void removeTotalPosition(String key) { totalPositionCache.remove(key); }

    /**
     * 获取总持仓缓存大小
     * @return 总持仓缓存数量
     */
    public int getTotalPositionCacheSize() { return totalPositionCache.size(); }

    /**
     * 清空所有持仓缓存
     */
    public void clearAllCache() { totalPositionCache.clear(); }


    /**
     * 获取总持仓缓存
     * @return 总持仓缓存Map
     */
    public Map<String, TotalPosition> getTotalPositionCache() { return totalPositionCache; }
    public ConcurrentHashMap<String, TotalPosition> getCompareTotalPosition() {
        return compareTotalPositionCache;
    }
}

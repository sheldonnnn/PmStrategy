package com.cmbc.oms.infrastructure.dao;

import com.cmbc.oms.domain.exposure.entity.PositionBalanceEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PositionBalanceMapper {

    /**
     * 根据条件查询头寸平衡信息
     * @param positionId 头寸快照ID
     * @param systemId 系统ID
     * @param folderId 归属头组
     * @param symbol 合约代码
     * @param statDate 交易日期
     * @return 头寸平衡信息列表
     */
    List<PositionBalanceEntity> getPositionBalanceByFolderId(
            @Param("positionId") String positionId,
            @Param("systemId") String systemId,
            @Param("folderId") String folderId,
            @Param("symbol") String symbol,
            @Param("statDate") String statDate
    );

    List<PositionBalanceEntity> getAllPositionBalance();

    /**
     * 根据头寸快照ID查询头寸平衡信息
     * @param positionId 头寸快照ID
     * @return 头寸平衡信息
     */
    PositionBalanceEntity getPositionBalanceByPositionId(@Param("positionId") String positionId);

    /**
     * 插入头寸平衡信息
     * @param positionBalanceEntity 头寸平衡实体
     */
    void insertPositionBalance(PositionBalanceEntity positionBalanceEntity);

    /**
     * 更新头寸平衡信息
     * @param positionBalanceEntity 头寸平衡实体
     */
    void updatePositionBalance(PositionBalanceEntity positionBalanceEntity);

    /**
     * 删除头寸平衡信息
     * @param positionId 头寸快照ID
     */
    void deletePositionBalance(@Param("positionId") String positionId);
}

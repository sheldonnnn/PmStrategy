package com.cmbc.strategy.dao;

import com.cmbc.strategy.domain.entity.SymbolSliceEntity;

import java.util.List;
import java.util.Map;

public interface SymbolSliceDao {

    public List<SymbolSliceEntity> selectById(String configId);

}

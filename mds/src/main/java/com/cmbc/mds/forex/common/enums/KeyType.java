package com.cmbc.mds.forex.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum KeyType {
    CLEAN_CACHE_KEY("CLEAN_CACHE_KEY"),
    MERGE_CACHE_KEY("MERGE_CACHE_KEY"),
    STRATEGY_SUB_KEY("STRATEGY_SUB_KEY"),
    TREADER_SUB_KEY("TREADER_SUB_KEY");

    private String type;
}

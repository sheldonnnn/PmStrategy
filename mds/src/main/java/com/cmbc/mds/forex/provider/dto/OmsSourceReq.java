package com.cmbc.mds.forex.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

// 外资行数据源状态变更请求
@Getter
@Setter
@AllArgsConstructor
public class OmsSourceReq {
    private String provider; // 数据源
    private String status; // 启停状态 0-停用 1-启用
    private String expireTime; // 行情过期时间
    private String createId; // 创建者ID
    private String Id; // 数据源ID
    private Map<String, String> extraParams; // 额外参数
}

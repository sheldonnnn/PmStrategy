package com.cmbc.mds.forex.provider.dto;

import lombok.*;

// 外资行连接状态相应
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class BankStateForWebRsp {
    private String BankName; // 外资行名称(对应Source)
    private String ConnectState; // 0-断开连接 1-已连接
    private String rspTime; // 响应时间：yyyyMMdd HH:mm:ss.SSS（用于 Web 展示）
    private long lastActiveTimeMs; // 最后活跃时间戳 epoch ms（用于看门狗超时计算）
}

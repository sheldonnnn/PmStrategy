package com.cmbc.mds.forex.quotes.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 外资行/数据源心跳响应实体
 * 对应报文中 MESSAGE_TYPE = "HeartbeatResponse"
 */
@Data
@ToString
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeartbeatResponse implements Serializable {

    /**
     * 连接状态
     * 兼容 JSON 字段: "connected"
     */
    @JsonAlias({"connected", "CONNECTED"})
    private Boolean connected;

    /**
     * 传输通道标识
     * 兼容 JSON 字段: "transport", "TRANSPORT"
     */
    @JsonAlias({"transport", "TRANSPORT"})
    private String transport;

    /**
     * 序列号
     */
    private Integer seqnum;
}

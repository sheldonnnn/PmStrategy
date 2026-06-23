package com.cmbc.mds.forex.quotes.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class MQTranserBean implements Serializable {
    /**
     * 消息类型（JSON序列化字段名映射为MESSAGE_TYPE）
     */
    @JsonAlias({"messageType", "MESSAGE_TYPE"})
    private String messageType;

    /**
     * 产品名称与市场编码组合
     */
    private String nameid;

    /**
     * 产品类型标志
     * SPT: 即期, FWD: 远期, SWAP: 掉期, RATE: 利率
     */
    private String tpfg;

    /**
     * 期限标识
     * 即期: 0, 远期: ON(隔夜), TN(次日)
     */
    private String term;

    /**
     * 币种名称（如USD/CNY等）
     */
    private String exnm;

    /**
     * 钞汇标志
     * 1: 汇, 2: 钞
     */
    private String cxfg;

    /**
     * 服务商标识（UBS/JPMC/COBA等）
     */
    private String serviceId;

    /**
     * 波动率类型必输字段
     */
    private String strike;

    /**
     * 数据有效性状态
     */
    private String stfg;

    /**
     * 可交易性标识
     * 0: 可交易价, 1: 参考价
     */
    private String trfg;

    /**
     * 起息日（格式：YYYYMMDD）
     */
    private String valueDay;

    /**
     * 外资行发送时间
     * 格式：HHmmss 或 YYYYMMDDHHmmss
     */
    private String intime;

    /**
     * 命令名称
     * 默认值：PRICE
     */
    private String conn;

    /**
     * 服务器本地时间
     * 格式：YYYYMMDDHHmmss
     */
    private String outtime;



    /**
     * 价格处理代码
     */
    private String prcd;

    /**
     * 序列号
     */
    private String sequ;

    /**
     * 交易对符号（如EUR/USD）
     */
    private String symbol;

    /**
     * 阶梯价格列表
     */
    private List<GradsPrice> gradsPriceList;



    /**
     * 连接状态（用于合并解析心跳响应）
     * 兼容 JSON 字段: "connected"
     */
    @JsonAlias({"connected", "CONNECTED"})
    private Boolean connected;

    /**
     * 传输通道标识（用于合并解析心跳响应）
     * 兼容 JSON 字段: "transport", "TRANSPORT"
     */
    @JsonAlias({"transport", "TRANSPORT"})
    private String transport;
}

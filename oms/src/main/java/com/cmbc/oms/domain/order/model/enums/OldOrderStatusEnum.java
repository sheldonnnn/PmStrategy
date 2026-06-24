package com.cmbc.oms.domain.order.model.enums;

/**
 * @author chendaqian
 * @date 2026/2/11
 * @time 11:10
 * @description 老订单状态枚举
 */
public enum OldOrderStatusEnum {

    ENTRUST( "0", "ENTRUST", "委托"),
    ENTRUST_SUCCESS( "1", "ENTRUST_SUCCESS", "委托成功"),
    ENTRUST_FAILURE( "-1", "ENTRUST_FAILURE", "委托失败，dimple拒单"),
    ENTRUST_DEAL( "2", "ENTRUST_DEAL", "部分成交"),
    ENTRUST_DEAL_ALL( "3", "ENTRUST_DEAL_ALL", "完全成交"),
    ENTRUST_WITHDRAWAL( "4", "ENTRUST_WITHDRAWAL", "委托撤单"),
    ENTRUST_WITHDRAWAL_SUCCESS( "5", "ENTRUST_WITHDRAWAL_SUCCESS", "撤单成功"),
    ENTRUST_WITHDRAWAL_FAILURE( "7", "ENTRUST_WITHDRAWAL_FAILURE", "撤单失败"),
    ENTRUST_RISK_REJECT_CANCEL( "10000", "ENTRUST_RISK_REJECT_CANCEL_ORDER", "风控撤单拒单"),
    REJECT_ORDER( "8", "REJECT_ORDER", "外资行拒单"),
    CLOSE_MARKET( "9", "CLOSE_MARKET", "闭市"),
    DATASOURCE_DISCONNECTED( "10", "DATASOURCE_DISCONNECTED", "数据源断开连接"),
    ENTRUST_WITHDRAWAL_REJECT( "12", "ENTRUST_WITHDRAWAL_REJECT", "撤单拒单"),
    FINAL( "11", "FINAL", "订单完成");

    // 9999 风控拒单
    // 1000 风控撤单拒单
    // -3 内部校验拒绝
//
//    /**授信拒单*/
//    constant string ENTRUST_CREDIT_REJECT_ORDER:="9998";
//    /**订单未委托确认或订单已完成，无法进行撤单！*/
//    constant string ENTRUST_40001:="40001";
//    /**已撤*/
//    constant string ENTRUST_40039:="40039";
//    /**无法撤单，订单已完全成交*/
//    constant string ENTRUST_40040:="40040";
//    /**响应超时*/
//    constant string ENTRUST_EXPIRE_TIME:="11";
//    /**网络异常*/
//    constant string ENTRUST_NETWORK_EXCEPTION:="-2";
//    /**自成交订单*/
//    constant string ENTRUST_SELF_CLOSING_ORDER:="10001";
//    /**撤单响应超时*/
//    constant string ENTRUST_WITHDRAWAL_EXPIRE_TIME:="13";
//    /**撤单网络异常*/
//    constant string ENTRUST_WITHDRAWAL_NETWORK_EXCEPTION:="-4";
//    /**用户撤单成功*/
//    constant string ENTRUST_WITHDRAWAL_USER_SUCCESS:="6";
//    /**异常订单状态 4 系统自动恢复*/
//    constant string EXCEPTION_ORDER_AUTO := "4";
//    /**异常订单状态 3 人工撤单*/
//    constant string EXCEPTION_ORDER_CANCEL := "3";
//    /**异常订单状态 2 人工成交*/
//    constant string EXCEPTION_ORDER_DEAL := "2";
//    /**异常订单状态 0 未处理*/
//    constant string EXCEPTION_ORDER_INIT := "0";
//    /**异常订单状态 1 人工拒单*/
//    constant string EXCEPTION_ORDER_REJECT := "1";
    private final String statusCode;
    private final String status;
    private final String statusName;

    OldOrderStatusEnum(String statusCode, String status,String statusName) {
        this.statusCode = statusCode;
        this.status = status;
        this.statusName = statusName;
    }

    /**
     * 根据状态码获取订单状态枚举
     * @param statusCode 状态码
     * @return 订单状态枚举
     */
    public static OldOrderStatusEnum fromStatusCode(String statusCode) {
        for (OldOrderStatusEnum status : OldOrderStatusEnum.values()) {
            if (status.getStatusCode().equals(statusCode)) {
                return status;
            }
        }
        return null;
    }

    public String getStatusCode() { return statusCode; }
    public String getStatus() { return status; }
    public String getStatusName() { return statusName; }

    @Override
    public String toString() {
        return "OldOrderStatusEnum{" +
                "statusCode='" + statusCode + '\'' +
                ", status='" + status + '\'' +
                ", statusName='" + statusName + '\'' +
                '}';
    }
}

package com.cmbc.oms.domain.order.model.enums;

/**
 * @author chendaqian
 * @date 2026/1/27
 * @time 16:52
 * @description 订单状态枚举，需要增加适配原apama的订单状态映射
 */
public enum OrderStatus {

    CREATED("-1", "创建", false, "订单登记", "不持久化"),
    INTERNAL_FAILED("-3", "内部拒单", true, "内部失败或本地校验失败", "持仓风控校验拒单，断开连接，自成交拒单等"),
    FAILED("-2", "失败", true, "委托出去产生的拒单", "拒单, 委托失败, 闭市场等"),
    NEW("0", "新订单/委托", false, "发送dimple，超时根据mktOrdId发起撤单", "0 委托, 4 撤单委托"),
    CONFIRMED("1", "委托确认", false, "委托确认订单", "1 委托确认"),
    PARTIAL_FILL("2", "部分成交", false, "是否带PARTIAL在状态控制逻辑上无区别，仅方便业务区分是否有成交", "2 部分成交"),
    FILLED("3", "全部成交", true, "", "3 成交"),
    CANCELLED("5", "全部撤销", true, "部分撤销", "5 撤单成交+原订单3 部分成交"),
    PARTIAL_CANCELLED("6", "部分撤销", true, "", "5 撤单成交+原订单3 部分成交");

    private final String statusCode;
    private final String statusName;
    private final boolean isFinal;
    private final String meaning;
    private final String oldSystemDict;

    OrderStatus(String statusCode, String statusName, boolean isFinal, String meaning, String oldSystemDict) {
        this.statusCode = statusCode;
        this.statusName = statusName;
        this.isFinal = isFinal;
        this.meaning = meaning;
        this.oldSystemDict = oldSystemDict;
    }

    public static OrderStatus fromOldStatusCode(String currentStatusCode, String oldStatusCode) {
        switch(oldStatusCode) {
            // todo 需要增加判断自成交订单并拒单校验处理
            case "0":
                return NEW;
            case "-1"://委托失败
            case "-2":// 网络异常
            case "8"://拒单
            case "11"://响应超时
                return FAILED;
            case "10"://外资行数据源断开连接
            case "9999"://风控委托拒单
            case "10001"://自成交拒单
            case "9"://闭市
                return INTERNAL_FAILED;
            case "1"://委托确认
                return CONFIRMED;
            case "5"://撤单成功
            case "6"://撤单成功
                if(PARTIAL_FILL.statusCode.equals(currentStatusCode)){
                    // 部分成交-> 部分撤销
                    return PARTIAL_CANCELLED;
                }
                return CANCELLED;
            case "10000"://风控撤单拒单
            case "12"://撤单拒单
                return fromStatusCode(currentStatusCode);
            case "2":
                return PARTIAL_FILL;
            case "3":
                return FILLED;
            default:
                throw new IllegalArgumentException("Invalid ErrorId code: " + oldStatusCode);
        }
    }

    /**
     * 根据状态码获取订单状态枚举
     * @param statusCode 状态码
     * @return 订单状态枚举
     */
    public static OrderStatus fromStatusCode(String statusCode) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.getStatusCode() .equals(statusCode)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 获取状态码
     * @return 状态码
     */
    public String getStatusCode() { return statusCode; }

    /**
     * 获取状态名称
     * @return 状态名称
     */
    public String getStatusName() { return statusName; }

    /**
     * 判断是否为终态
     * @return true表示终态，false表示非终态
     */
    public boolean isFinal() { return isFinal; }

    /**
     * 获取状态含义
     * @return 状态含义
     */
    public String getMeaning() { return meaning; }

    /**
     * 获取老系统库表字典
     * @return 老系统库表字典
     */
    public String getOldSystemDict() { return oldSystemDict; }

    /**
     * 判断是否为委托状态
     * @return true表示是委托状态，false表示不是
     */
    public boolean isOrderConfirmation() { return this == NEW || this == CONFIRMED; }

    /**
     * 判断是否为成交状态
     * @return true表示是成交状态，false表示不是
     */
    public boolean isFilled() { return this == PARTIAL_FILL || this == FILLED; }

    /**
     * 判断是否为撤销状态
     * @return true表示是撤销状态，false表示不是
     */
    public boolean isCancelled() { return this == CANCELLED || this == PARTIAL_CANCELLED; }

    @Override
    public String toString() {
        return "OrderStatusEnum{" +
                "statusCode='" + statusCode + '\'' +
                ", statusName='" + statusName + '\'' +
                ", isFinal=" + isFinal +
                ", meaning='" + meaning + '\'' +
                ", oldSystemDict='" + oldSystemDict + '\'' +
                '}';
    }
}

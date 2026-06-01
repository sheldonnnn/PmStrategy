package com.cmbc.oms.domain.exception;

import org.springframework.stereotype.Service;

@Service
public class ExceptionNotificationService {

    /*
    * 1.instanceId: 策略实例ID
    * 2.userName 用户名
    * 3.异常展示信息
    * 4.grade：1.正常消息提醒；2.预警提醒；3.异常提醒
    * 5.module：默认积存金平盘策略
    * 6.orderInfo：订单信息
    * */
    public void pushExceptionInfo(String instanceId, String userName, String info, Integer grade, String module,String orderInfo){


    }


}

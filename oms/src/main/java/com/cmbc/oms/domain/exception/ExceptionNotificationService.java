package com.cmbc.oms.domain.exception;

import com.alibaba.fastjson.JSONObject;
import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * @author cuijian
 * @date 2026/3/11
 * @time 14:13
 * @description 积存金异常信息推送模块
 */
@Service
public class ExceptionNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionNotificationService.class);
    @Autowired
    @Qualifier("restTemplateLong") // 默认指定长链接
    private RestTemplate restTemplate;

    @Value("${mgap.goldException.url}")
    String url;


    /**
     * 异常信息全局推送接口
     * 参数传递规则:
     * 1、instanceId 实例ID，必须传
     * 2、userName 用户名：上送时，全局异常信息推送到指定用户，上送为null，异常信息所有用户全部进行推送
     * 3、info，异常报错信息。必须上送，不允许为null
     * 4、grade: 枚举有三种，上送1：全局展示成功提醒，标志位绿色，上送2，全局展示警告提醒。标志位黄色，上送3：全局异常提醒，标志位红色
     * 5、module: 模块名称，上送为空，数据库存储为：积存金平盘模块，如果上送，存储上送数据值
     * 6、orderInfo: 订单信息：可以不上送，如果上送，可上送为JSON字符串格式，举例如下：
     * com.finesys.client.AlgoStartReq("chengxuhua-卖AU9999买mAuTD_00-100000-11-18-21", "chengxuhua", 
     * "lixiaofeng", "Au99.99", "mAu(T+D)", "3", "1", 1, -1, 100000, "", "", 15, "暂停", "chengxuhua-卖AU9999买mAuTD_00-100000-11-18-21",
     * "2", 120, "3", .02, 1000, 20, .02, .02, "0", "0", 300, 300, "1", "0", "1", 0, 0, "0", "1", 5, "2019-01-11 11:18:21")
     *
     * @param
     */
    public void pushExceptionInfo(String instanceId, String userName, String info, Integer grade, String module, String orderInfo) {

        if (StringUtils.isBlank(instanceId)) {
            logger.error("积存金异常信息提醒instanceId不能为空！！");
            return;
        }

        if (null == grade) {
            logger.error("积存金异常信息提醒grade不能为空！！");
            return;
        }

        if (StringUtils.isBlank(info)) {
            logger.error("积存金异常信息提醒info不能为空！！");
            return;
        }
        try {
            //参数组装
            Map<String, Object> request = this.buildMap(instanceId, userName, info, grade, module, orderInfo);
            //信息发送
            logger.info("积存金异常信息推送请求::{}", JSONObject.toJSONString(request));
            String mgapPosResponse = restTemplate.postForObject(url, request, String.class);
            logger.info("积存金异常信息推送响应::{}", mgapPosResponse);
        } catch (Exception e) {
            logger.error("积存金异常信息推送异常{}", e);
        }

    }

    private Map<String, Object> buildMap(String instanceId, String userName, String info, Integer grade, String module, String orderInfo) {
        Map<String, Object> request = new HashMap<>();

        request.put("userName", userName);
        request.put("serviceName", "GoldHedge");
        request.put("info", info);
        request.put("grade", null == grade ? 3 : grade);
        request.put("APAMA_EXCEPTION", "APAMA_EXCEPTION");
        request.put("module", StringUtils.isBlank(module) ? "积存金平盘策略" : module);
        request.put("strategyId", instanceId);
        request.put("orderInfo", orderInfo);
        return request;
    }
}

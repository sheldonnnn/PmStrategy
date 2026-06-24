package com.cmbc.oms.domain.position.ability;

import com.cmbc.oms.domain.order.ability.factory.ReqTraderPosAllQryEventFactory;
import com.cmbc.oms.domain.event.*;
import com.cmbc.oms.domain.exception.PositionCheckException;
import com.cmbc.oms.domain.facade.apama.CheckPositionTask;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.entity.NewOrder;
import com.cmbc.oms.domain.order.model.OrderStatus;
import com.cmbc.oms.domain.position.model.entity.InitPositions;
import com.cmbc.oms.domain.position.model.entity.Positions;
import com.cmbc.oms.domain.position.model.entity.TotalPosition;
import com.cmbc.oms.domain.position.model.entity.TraderNoClientMember;
import com.cmbc.oms.infrastructure.cache.BasicParamCacheManager;
import com.cmbc.oms.infrastructure.cache.OrderCacheManager;
import com.cmbc.oms.infrastructure.cache.PositionCacheManager;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import com.cmbc.oms.infrastructure.util.BasicPositionUtil;
import com.cmbc.oms.infrastructure.util.PositionLockProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * @author chendaqian
 * @date 2026/2/11
 * @time 16:03
 * @description 持仓管理服务
 */
@Service
public class PositionManageService implements IPositionManageService {
    private static final Logger log = LoggerFactory.getLogger(PositionManageService.class);

    @Autowired
    private PositionCacheManager positionCacheManager;
    @Autowired
    private PositionRequestManager positionRequestManager;
    @Autowired
    private OrderCacheManager orderCacheManager;
    // 持仓相关缓存
    @Autowired
    private BasicParamCacheManager basicParamCacheManager;
    @Autowired
    private ReqTraderPosAllQryEventFactory factory;
    @Autowired
    private SendEventToApama sendEventToApama;
    @Autowired
    private PositionLockProvider positionLockProvider;

    private static final String INIT_POSITION = "INIT_POSITION";
    private static final String COMPARE_POSITION = "COMPARE_POSITION";

    // 触发全量比对开关
    private volatile boolean isTriggerCompare = false;
    // 同步请求MAP, KEY: 交易员_合约_账号 value: null
    public final Map<String, Boolean> positionSynCache = new ConcurrentHashMap<>();

    // 更新持仓信息到dataView
    private void updateAndStoreTotalPosition(String key, TotalPosition totalPosition) {
        /*放入缓存*/
        if (Objects.isNull(totalPosition)) {
            positionCacheManager.cacheTotalPosition(key, totalPosition);
        }
        /*更新dataview*/
        // totalPositionDV.update(totalPosition);
        /*更新数据库*/
    }

    // 定时拉取持仓，用于定时主动查询Apama持仓并进行比对
    @Scheduled(cron = "0 0/5 * * * ? ") // 每5分钟执行一次
    public void comparePosition() {
        log.info("开始执行定时持仓比对任务");
        try {
            // 主动触发一次持仓比对查询(调dimp查询)
            // 主动触发一次同步完成后做比对(调内部)
            String traderNo = basicParamCacheManager.getDimpleUserInfo(); // 从配置获取
            if (Objects.isNull(traderNo)) {
                log.error("dimple用户信息为空，请检查配置");
                return;
            }
            // 比对请求前，先翻新上一次比对缓存
            // 为同步请求添加标识
            // String syncRequestId = qryStorageEvent.getUniqueId();
            // 根据场景选择添加不同的标识
            // if ("COMPARE".equals(type)) {
            //     positionRequestManager.addCompareRequestId(syncRequestId);
            // } else {
            //     positionRequestManager.addInitRequestId(syncRequestId);
            // }
            // sendEventToApama.sendEventToApama(qryStorageEvent);
        } catch (Exception e) {
             log.error("执行定时持仓比对任务异常", e);
        }
    }

    public void spotToPosition(RspTraderQryStorageEvent rspTraderQryStorage, String type) {
        log.debug("处理现货持仓响应。类型: {}, 请求ID: {}", type, rspTraderQryStorage.getUniqueId());
        // 交易品种
        String exchCode = "";
        // 合约大类
        String contractCategory = "";
        // 获取合约信息
        ContractInfoBasic contractInfo;
        Map<String, ContractInfoBasic> allContractInfo = basicParamCacheManager.getAllContractInfo();
        if (allContractInfo.containsKey(rspTraderQryStorage.getContractID())) {
            contractInfo = allContractInfo.get(rspTraderQryStorage.getContractID());
            exchCode = contractInfo.getExchCode();
            contractCategory = contractInfo.getVarietyId();
        } else {
            contractInfo = new ContractInfoBasic();
        }
        // 为贵金属对不进行统计缓存
        if (BusinessConstant.AG.equals(contractCategory)) {
            log.debug("白银类别为空，不进行统计缓存");
            return;
        }

        // todo transferContractInfo 转换合约信息
        // 会员号
        String memberID = rspTraderQryStorage.getMemberID();
        // 过滤条件
        String traderNoExchCode = memberID + exchCode;
        // 不存在对应映射信息
        Map<String, TraderNoClientMember> traderMemberIDCache = basicParamCacheManager.getTraderMemberInfo();
        if (!traderMemberIDCache.containsKey(traderNoExchCode)) {
            log.debug("交易会员内部信息缓存中不存在对应的交易员信息，直接返回");
            return;
        } else {
            // 会员内部信息
            TraderNoClientMember traderNoClientMember = traderMemberIDCache.get(traderNoExchCode);
            // 会员号和客户号相同时，加入缓存，否则不处理
            if (memberID.equals(traderNoClientMember.getMemberID())) {
                String symbol = rspTraderQryStorage.getContractID();
                // 账户
                String account = symbol;
                // 拼接出持仓的key:币种、账户
                String key = account + symbol;
                // 获取持仓管理对象
                String flag = BusinessConstant.BUY_SIDE; // 现货有库存，说明是多头=>买方向
                // 拼接持仓管理key,服务ID,账户、交易品种、前台用户、业务类型、交易员、买卖方向
                String keyPos = key + flag;
                Positions positionManager = getPosition(null, keyPos, flag, account, null, null, contractInfo); // 假设调用的getPosition
                BigDecimal offsetLastQty = BigDecimal.ZERO; // 昨日持仓量
                BigDecimal dimpleQty = BasicPositionUtil.orderQtyToMand(
                        BigDecimal.valueOf(rspTraderQryStorage.getAvailableStorage()),
                        contractInfo, BusinessConstant.KILO_GRAM);

                // 获取请求唯一ID
                String uniqueId = rspTraderQryStorage.getUniqueId();
                // 交易员号
                String traderNoFromEvent = rspTraderQryStorage.getTraderNo();
                // 客户编号
                String clientID = rspTraderQryStorage.getMemberID();
                // 交易员
                String memberId = rspTraderQryStorage.getMemberID();
                // 各种持仓量
                BigDecimal lastQty = BigDecimal.ZERO; // 上日持仓量
                BigDecimal todayQty = dimpleQty; // 今日持仓量
                BigDecimal qty = dimpleQty; // 可用持仓量
                BigDecimal offsetQty = dimpleQty; // 可平仓 减交量
                BigDecimal offsetTodayQty = dimpleQty; // 可平今 撤单数量
                BigDecimal frozenQty = BigDecimal.ZERO; // 冻结持仓量
                BigDecimal totalQty = dimpleQty; // 总持仓量
                BigDecimal avePrice = BigDecimal.ZERO; // 持仓均价
                BigDecimal lastPrice = BigDecimal.ZERO; // 最新价
                String tradePurpose = rspTraderQryStorage.getTradePurpose(); // 交易目的
                // 持仓盈亏
                BigDecimal amt = BigDecimal.ZERO;
                // 保证金
                BigDecimal margin = BigDecimal.ZERO;
                // 平仓盈亏
                BigDecimal profitFund = BigDecimal.ZERO;
                // 浮动盈亏
                BigDecimal floatProfitLoss = BigDecimal.ZERO;

                // 组装持仓信息
                try {
                    TotalPosition totalPosition = new TotalPosition();
                    totalPosition.setAccount(account);
                    totalPosition.setSymbol(symbol);
                    totalPosition.setContractInfo(contractInfo);

                    totalPosition.setTraderNo(traderNoFromEvent);
                    totalPosition.setBuyPos(positionManager);

                    // 根据比较标识存入缓存
                    if (COMPARE_POSITION.equals(type)) {
                        positionCacheManager.cacheCompareTotalPosition(key, totalPosition);
                    } else if (INIT_POSITION.equals(type)) {
                        // 初始化持仓
                        // route(totalPosition);
                        // 存储并更新dataview
                        updateAndStoreTotalPosition(key, totalPosition);
                        log.debug("更新局部信息. totalPosition={}, totalPositionCache={}",
                                totalPosition.toString(), totalPosition.toString());

                        // 如果是最后一次响应
                        // 待定逻辑
                        positionSynCache.put(BusinessConstant.SPOT, true);
                        if (INIT_POSITION.equals(type) || isTriggerCompare) {
                            // 调用比对方法
                            callbackCashPosition();
                        }
                    }

                } catch (Exception e) {
                    log.error("处理现货持仓响应异常", e);
                }
            }
        }
    }

    /**初始化持仓管理对象*/
    public void initTotalPositionManager(String key, String bsFlag, String account, ContractInfoBasic contractInfo,
                                         Positions positionManager, TotalPosition rspTotalPosition) {

        String traderNo = "admin";
        String userName = "admin";
        String businessType = "";
        InitPositions initPositions = new InitPositions(key, traderNo, bsFlag, rspTotalPosition,
                contractInfo, account, userName, businessType);
        positionManager.init(initPositions);
    }

    public void contractToPosition(RspTraderPosiAllQryEvent rspTraderPosiAllQry, String type) {
        log.debug("处理期货持仓响应。类型: {}, 请求ID: {}", type, rspTraderPosiAllQry.getUniqueId());
        // 交易品种
        String exchCode = "";
        // 合约大类
        String contractCategory = "";
        // 获取合约信息
        ContractInfoBasic contractInfo;
        Map<String, ContractInfoBasic> allContractInfo = basicParamCacheManager.getAllContractInfo();
        if (allContractInfo.containsKey(rspTraderPosiAllQry.getContractID())) {
            contractInfo = allContractInfo.get(rspTraderPosiAllQry.getContractID());
            exchCode = contractInfo.getExchCode();
            contractCategory = contractInfo.getVarietyId();
        } else {
            contractInfo = new ContractInfoBasic();
            log.debug("合约信息缓存中不存在对应的合约信息，直接返回");
            return;
        }

        // 上期所只存普通投机保值3 为 保值的
        if (BusinessConstant.SH_FUTURES_EXCHANGE.equals(exchCode)) {
            if (BusinessConstant.SH_FLAG.equals(rspTraderPosiAllQry.getShFlag())) {
                log.debug("上期所只存保值状态为保值，直接返回");
                return;
            }
        }

        // 为贵金属对不进行统计缓存
        if (BusinessConstant.AG.equals(contractCategory)) {
            log.debug("白银类别为空，不进行统计缓存");
            return;
        }

        // 合约转换 todo transferContractInfo
        // 会员号
        String memberID = rspTraderPosiAllQry.getMemberID();
        // 过滤条件
        String traderNoExchCode = memberID + exchCode;
        // 不存在对应映射信息
        Map<String, TraderNoClientMember> traderMemberIDCache = basicParamCacheManager.getTraderMemberInfo();
        if (!traderMemberIDCache.containsKey(traderNoExchCode)) {
            log.debug("交易会员内部信息缓存中不存在对应的交易员信息，直接返回");
            return;
        } else {
            // 会员内部信息
            TraderNoClientMember traderNoClientMember = traderMemberIDCache.get(traderNoExchCode);
            // 会员号和客户号相同时，加入缓存，否则不处理
            if (memberID.equals(traderNoClientMember.getMemberID())) {
                String symbol = rspTraderPosiAllQry.getContractID();
                // 账户
                String account = rspTraderPosiAllQry.getMemberID();
                // 拼接出持仓的key:币种、账户
                String key = account + symbol;
                // 获取持仓管理对象
                String flag = BusinessConstant.BUY_SIDE;
                // 买卖方向判断
                if (BusinessConstant.B_FLAG.equals(rspTraderPosiAllQry.getBsFlag())) {
                    flag = BusinessConstant.BUY_SIDE;
                } else {
                    flag = BusinessConstant.SELL_SIDE;
                }
                // 拼接持仓管理key,服务ID,账户、交易品种、前台用户、业务类型、交易员、买卖方向
                String keyPos = key + flag;
                Positions positionManager = getPosition(null, keyPos, flag, account, null, null, contractInfo); // 假设调用的getPosition

                String traderNoFromEvent = rspTraderPosiAllQry.getTraderNo();
                String memberId = rspTraderPosiAllQry.getMemberID();
                // 各种持仓量
                BigDecimal lastQty = BigDecimal.valueOf(rspTraderPosiAllQry.getLastQty()); // 上日持仓量
                BigDecimal todayQty = BigDecimal.valueOf(rspTraderPosiAllQry.getTodayQty()); // 今日持仓量
                BigDecimal offsetQty = BigDecimal.valueOf(rspTraderPosiAllQry.getOffsetQty()); // 可平仓 减交量
                BigDecimal qty = offsetQty; // 可用持仓量
                BigDecimal offsetLastQty = BigDecimal.valueOf(rspTraderPosiAllQry.getOffsetLastQty()); // 可平昨 未成交数量
                BigDecimal offsetTodayQty = BigDecimal.valueOf(rspTraderPosiAllQry.getOffsetTodayQty()); // 可平今 撤单数量
                BigDecimal frozenQty = BigDecimal.valueOf(rspTraderPosiAllQry.getFrozenQty()); // 冻结持仓量
                BigDecimal totalQty = BigDecimal.valueOf(rspTraderPosiAllQry.getTotalQty()); // 总持仓量
                BigDecimal avePrice = BigDecimal.valueOf(rspTraderPosiAllQry.getAvgPrice()); // 持仓均价
                BigDecimal lastPrice = BigDecimal.valueOf(rspTraderPosiAllQry.getLastPrice()); // 最新价
                String tradePurpose = rspTraderPosiAllQry.getUniqueId(); // 交易目的

                // 持仓盈亏
                BigDecimal amt = BigDecimal.ZERO;
                if (rspTraderPosiAllQry.getAmtStr() != null && !rspTraderPosiAllQry.getAmtStr().isEmpty()) {
                    amt = new BigDecimal(rspTraderPosiAllQry.getAmtStr().trim());
                }
                // 保证金
                BigDecimal margin = BigDecimal.ZERO;
                if (rspTraderPosiAllQry.getMarginStr() != null && !rspTraderPosiAllQry.getMarginStr().isEmpty()) {
                    margin = new BigDecimal(rspTraderPosiAllQry.getMarginStr().trim());
                }
                // 平仓盈亏
                BigDecimal profitFund = BigDecimal.ZERO;
                if (rspTraderPosiAllQry.getProfitFundStr() != null && !rspTraderPosiAllQry.getProfitFundStr().isEmpty()) {
                    profitFund = new BigDecimal(rspTraderPosiAllQry.getProfitFundStr().trim());
                }
                // 浮动盈亏
                BigDecimal floatProfitLoss = BigDecimal.ZERO;
                if (rspTraderPosiAllQry.getFloatProfitLossStr() != null
                        && !rspTraderPosiAllQry.getFloatProfitLossStr().isEmpty()) {
                    floatProfitLoss = new BigDecimal(rspTraderPosiAllQry.getFloatProfitLossStr().trim());
                }

                // 组装持仓信息(基础)
                TotalPosition rspTotalPosition = new TotalPosition().createTotalPosition(
                        traderNoFromEvent, account, symbol, flag, qty, lastQty,
                        todayQty, offsetQty, offsetLastQty, offsetTodayQty, frozenQty,
                        totalQty, amt, margin, avePrice, lastPrice,
                        profitFund, floatProfitLoss, tradePurpose, contractInfo);

                // 初始化持仓
                initTotalPositionManager(keyPos, flag, account, contractInfo, positionManager, rspTotalPosition);
                // 更新统计持仓信息
                // totalPositionManager.setAvgPrice(avePrice); //持仓平均价
                // totalPositionManager.setProfitFund(profitFund); //持仓盈亏
                // totalPositionManager.setMargin(margin); //平仓盈亏
                positionCacheManager.cachePositionManager(keyPos, positionManager);
                TotalPosition totalPosition;
                if (positionCacheManager.getTotalPositionCache().containsKey(key)) {
                    totalPosition = positionCacheManager.getTotalPosition(key);
                    if (totalPosition.getContractInfo() == null || "".equals(totalPosition.getContractInfo().getSymbol())) {
                        totalPosition.setContractInfo(contractInfo);
                    }
                } else {
                    totalPosition = new TotalPosition();
                    totalPosition.setAccount(account);
                    totalPosition.setSymbol(symbol);
                    totalPosition.setContractInfo(contractInfo);
                }
                totalPosition.setTraderNo(traderNoFromEvent);
                if (BusinessConstant.BUY_SIDE.equals(flag)) {
                    totalPosition.setBuyPos(positionManager);
                } else {
                    totalPosition.setSellPos(positionManager);
                }

                // 根据比较标识存入缓存
                if (COMPARE_POSITION.equals(type)) {
                    positionCacheManager.cacheCompareTotalPosition(key, totalPosition);
                } else if (INIT_POSITION.equals(type)) {
                    // 初始化持仓
                    // route(totalPosition);
                    // 存储并更新dataview
                    updateAndStoreTotalPosition(key, totalPosition);
                    log.debug("更新局部信息. totalPosition={}, totalPositionCache={}",
                            totalPosition.toString(), totalPosition.toString());
                }

                // 如果是最后一次响应
                if (rspTraderPosiAllQry.isBIsLast()) {
                    // 待定逻辑
                    positionSynCache.put(BusinessConstant.CONTRACT, true);
                    // 触发比对一次
                    if (INIT_POSITION.equals(type) || isTriggerCompare) {
                        // 调用比对方法
                        callbackCashPosition();
                    }
                }
            }
        }
    }

    private void callbackCashPosition() {
        log.debug("进入核对持仓方法");

        for (String pkey : positionSynCache.keySet()) {
            Boolean isT = positionSynCache.get(pkey);
            if (!isT) {
                return;
            }
        }
        log.debug("开始进行核对持仓逻辑处理");
        try {
            compareCashPosition();
        } catch (Exception e) {
            log.error("核对持仓逻辑处理时发生异常", e);
        }
    }

    /**核对本地计算总持仓与dimple是否一致*/
    private void compareCashPosition() {
        // 是否触发核对持仓功能
        this.setTriggerCompare(true);
        ConcurrentHashMap<String, TotalPosition> compareTotalPositionCache = positionCacheManager.getCompareTotalPosition();
        Map<String, TotalPosition> totalPositionCache = positionCacheManager.getTotalPositionCache();
        if (totalPositionCache.isEmpty() || compareTotalPositionCache.isEmpty()) {
            log.error("totalPositionCache or compareTotalPositionCache is null");
            return;
        }

        log.debug("isTriggerCompare=compareTotalPositionCache==>" + totalPositionCache.toString());
        log.debug("isTriggerCompare=compareTotalPositionCache==>" + compareTotalPositionCache.toString());

        Set<String> keys = compareTotalPositionCache.keySet();
        for (String key : keys) {
            // 比对持仓信息
            if (!totalPositionCache.containsKey(key)) {
                log.error("key " + key + " not found in totalPositionCache");
                continue;
            }
            // 持仓信息
            TotalPosition totalPosition = totalPositionCache.get(key);
            // Dimple持仓信息
            TotalPosition dimpleTotalPosition = compareTotalPositionCache.get(key);

            // 买方持仓比对
            BigDecimal buyTotalQty = totalPosition.getBuyPos().getOffsetQty();
            // 卖方持仓比对
            BigDecimal sellTotalQty = totalPosition.getSellPos().getOffsetQty();
            // dimple买方持仓
            BigDecimal dimpleBuyTotalQty = dimpleTotalPosition.getBuyPos().getOffsetQty();
            // dimple卖方持仓
            BigDecimal dimpleSellTotalQty = dimpleTotalPosition.getSellPos().getOffsetQty();

            boolean isConsistent = true;
            StringBuilder comparisonMessage = new StringBuilder("合约【" + totalPosition.getSymbol()
                    + "】账户【" + totalPosition.getAccount() + "】");

            if (!Objects.equals(buyTotalQty, dimpleBuyTotalQty)) {
                isConsistent = false;
                comparisonMessage.append("买头寸变化持仓与dimple持仓不一致。");
            }
            if (!Objects.equals(sellTotalQty, dimpleSellTotalQty)) {
                isConsistent = false;
                comparisonMessage.append("卖头寸变化持仓与dimple持仓不一致。");
            }
            if (isConsistent) { comparisonMessage.append("对比结果一致。"); }
            log.info("定时持仓对比结果：{}", comparisonMessage.toString());

            // 发送比对信息，并展示 todo
            // ...
        }

        // 持仓同步需要
        doCallbackSynchronizedPosition();
        // 核对完成后改为false，避免多次核对无任何核对问题
        this.setTriggerCompare(false);
    }

    private void doCallbackSynchronizedPosition() {
        log.debug("进入同步持仓方法");
        for (String pkey : positionSynCache.keySet()) {
            Boolean isPositionTrue = positionSynCache.get(pkey);
            if (!isPositionTrue) {
                return;
            }
        }
        try {
            ConcurrentHashMap<String, TotalPosition> compareTotalPositionCache = positionCacheManager.getCompareTotalPosition();
            Map<String, TotalPosition> totalPositionCache = positionCacheManager.getTotalPositionCache();

            if (totalPositionCache.size() >= compareTotalPositionCache.size()) {
                // ***当本地持仓缓存大于等于dimple缓存**//
                Set<String> keys = totalPositionCache.keySet();
                for (String key : keys) {
                    // synchronizePositions(key);
                }
            } else {
                // ***dimple持仓名，以dimple为准循环**//
                Map<String, TotalPosition> totalPositionCacheNew = positionCacheManager.getTotalPositionCache();
                // ...
            }
        } catch (Exception e) {
            log.error("同步持仓逻辑处理时发生异常", e);
        }
    }

    public void updateTotalPositionQtyByZero(TotalPosition total) {
        // **总持仓明细**//
        Positions buyPos = new Positions();
        buyPos.setOffsetQty(BigDecimal.ZERO);      // 可平仓 减交量*/
        buyPos.setOffsetLastQty(BigDecimal.ZERO);  // 可平昨 未日持仓量*/
        buyPos.setOffsetTodayQty(BigDecimal.ZERO); // 可平今 持仓量*/
        buyPos.setTodayFreezeQty(BigDecimal.ZERO); // 今日冻结持仓量
        buyPos.setLastdayFreezeQty(BigDecimal.ZERO); // 昨日冻结持仓量
        buyPos.setQuantity(BigDecimal.ZERO); // 累计持仓量
        buyPos.setUseQty(BigDecimal.ZERO); // 可用持仓
        buyPos.setFreezeQty(BigDecimal.ZERO); // 冻结持仓量
        total.setBuyPos(buyPos);

        // **总持仓明细**//
        Positions sellPos = new Positions();
        sellPos.setOffsetQty(BigDecimal.ZERO);      // 可平仓 减交量*/
        sellPos.setOffsetLastQty(BigDecimal.ZERO);  // 可平昨 未日持仓量*/
        sellPos.setOffsetTodayQty(BigDecimal.ZERO); // 可平今 持仓量*/
        sellPos.setTodayFreezeQty(BigDecimal.ZERO); // 今日冻结持仓量
        sellPos.setLastdayFreezeQty(BigDecimal.ZERO); // 昨日冻结持仓量
        sellPos.setQuantity(BigDecimal.ZERO); // 累计持仓量
        sellPos.setUseQty(BigDecimal.ZERO); // 可用持仓
        sellPos.setFreezeQty(BigDecimal.ZERO); // 冻结持仓量
        total.setSellPos(sellPos);

        // todo 发送事件
        // route total;
        // **放置缓存**//
        // **拼接key值=会员号+合约号**
        String key = total.getAccount() + total.getSymbol();
        positionCacheManager.cacheTotalPosition(key, total);
        log.debug("同步更新初始总持仓信息。totalPosition=>" + total);
    }

    /**
     * 根据订单更新持仓
     */
    public void updatePositionByOrder(ExecutionReport orderManage) {
        // 交易品种
        String symbol = orderManage.getSymbol();
        // 缓存使用合约情况
        // useContractCache.add(symbol, 1);
        // 境内外交易
        String domesticType = orderManage.getDomesticType();
        // 账户
        String account = "";
        if (BusinessConstant.DOMESTIC_TYPE_INNER.equals(domesticType)) {
            account = orderManage.getMemberId();
        } else {
            // 交易对手。必须填。一外进行信息。UBS-Fix、JPMC-Fix等
            account = orderManage.getCounterParty();
        }

        // 如果账户为空，则不计算持仓信息
        if (Objects.isNull(account)) {
            return;
        }

        // 确定锁定的key值 保证这个key的下面是绝对单行执行
        Lock lock = positionLockProvider.getLock(account + symbol);
        lock.lock();

        try {
            String orderId = orderManage.getOrderId();
            int status = Integer.parseInt(orderManage.getStatus());

            // 恢复是否还原订单。还原的则无需进行处理
            String restore = orderManage.getExtraParas() != null ? orderManage.getExtraParas().getOrDefault("Restore", "False") : "False";
            if ("True".equals(restore)) {
                return;
            }

            // 服务编号。必须有。区内服务
            String serviceId = "OmsRiskService";
            // 交易对手。必须填。一外进行信息。UBS-Fix、JPMC-Fix等
            String counterParty = orderManage.getCounterParty();
            // 交易员账户
            String traderNo = orderManage.getTraderNo();
            // 前台用户名
            String userName = orderManage.getUserName();

            // 业务类型：境内套利 境内 境外外套利
            String businessType = orderManage.getBusinessType();
            // 交易目的
            String tradePurpose = orderManage.getTradePurpose();
            // 复制
            ContractInfoBasic contractInfo = basicParamCacheManager.getContractInfo(symbol);
            if (contractInfo == null || contractInfo.getSymbol().isEmpty()) {
                // assert contractInfo != null;
                return;
            }

            String flag = BusinessConstant.BUY_SIDE;
            // 获取持仓管理对象
            // 拼接key值，服务ID,账户、交易品种、前台用户、业务类型、交易员、买卖方向
            String buyKey = serviceId + account + symbol + userName + businessType + traderNo + flag;
            // 买方持仓
            Positions buyPositionManager = getPosition(traderNo, buyKey, flag, account, userName, businessType, contractInfo);
            // 获取卖方持仓
            flag = BusinessConstant.SELL_SIDE;
            String sellKey = serviceId + account + symbol + userName + businessType + traderNo + flag;
            Positions sellPositionManager = getPosition(traderNo, sellKey, flag, account, userName, businessType, contractInfo);
            
            String orderStatusCode = Integer.toString(status);
            // 买卖方向
            String bsFlag = orderManage.getSide();
            // 开平方向
            String eoFlag = orderManage.getOpenFlag();
            // 价格
            BigDecimal price = orderManage.getPrice();
            // 数量
            BigDecimal qty = orderManage.getOrderQty();
            // 合约的种类 1 合约 2 基础
            String contractType = contractInfo.getInventoryType();
            // 标记代码
            String tagCode = orderManage.getTagCode();
            boolean isJavaOrder = BusinessConstant.ORDER_TAG_TYPE_MGAPHEDGE.equals(tagCode);
            // 订单状态
            String orderState = "";
            boolean isOrderInCache = orderCacheManager.getChildStatusMap().containsKey(orderId);
            // 委托确认
            if (OrderStatus.CONFIRMED.getStatusCode().equals(orderStatusCode)) {
                // 收到委托确认了
                if (BusinessConstant.DOMESTIC_TYPE_INNER.equals(domesticType)) {
                    if (isOrderInCache) {
                        // 未在缓存中，则为新订单
                        orderState = BusinessConstant.NEW;
                    } else {
                        // 境内订单已确认状态
                        orderState = BusinessConstant.NEW;
                    }
                }
            } else if (OrderStatus.PARTIAL_FILL.getStatusCode().equals(orderStatusCode)) {
                // 部分成交
                if (BusinessConstant.DOMESTIC_TYPE_INNER.equals(domesticType)) {
                    if (isJavaOrder) {
                        if (isOrderInCache) {
                            orderState = BusinessConstant.DEAL;
                        } else {
                            orderState = BusinessConstant.DEAL;
                        }
                    } else {
                        orderState = BusinessConstant.DEAL;
                    }
                }
            } else if (OrderStatus.FILLED.getStatusCode().equals(orderStatusCode)) {
                // 全部成交
                if (BusinessConstant.DOMESTIC_TYPE_INNER.equals(domesticType)) {
                    // Java下订单。存在缓存中认为是有效成交订单
                    if (isJavaOrder) {
                        if (isOrderInCache) {
                            orderState = BusinessConstant.DEAL;
                        } else {
                            orderState = BusinessConstant.DEAL;
                        }
                    } else {
                        orderState = BusinessConstant.DEAL;
                    }
                } else {
                    orderState = BusinessConstant.DEAL;
                }
            }

            // 获取持仓
            // 拼接出持仓的key:币种、账户
            String key = account + symbol;
            flag = BusinessConstant.BUY_SIDE;
            // 买方持仓
            buyKey = key + flag;
            buyPositionManager = getPosition(traderNo, buyKey, flag, account, userName, businessType, contractInfo); // getTotalPositionManager logic
            log.info("更新买方持仓前。{} , {}", buyKey, buyPositionManager.toString());
            buyPositionManager.posUpdate(bsFlag, eoFlag, qty, price, orderState);
            positionCacheManager.cachePositionManager(buyKey, buyPositionManager);
            log.info("更新买方持仓后。{} , {}", buyKey, buyPositionManager.toString());
            // 卖方持仓
            flag = BusinessConstant.SELL_SIDE;
            sellKey = key + flag;
            sellPositionManager = getPosition(traderNo, sellKey, flag, account, userName, businessType, contractInfo); // getTotalPositionManager logic
            if (BusinessConstant.SPOT.equals(contractType)) {
                log.info("更新卖方(现货)持仓前。{} , {}", sellKey, sellPositionManager.toString());
                sellPositionManager.posUpdate(bsFlag, eoFlag, qty, price, orderState);
                positionCacheManager.cachePositionManager(sellKey, sellPositionManager);
                log.info("更新卖方(现货)持仓后。{} , {}", sellKey, sellPositionManager.toString());
            }

            // 总体持仓缓存。key: account+symbol
            TotalPosition totalPosition = new TotalPosition();
            if (positionCacheManager.getTotalPositionCache().containsKey(key)) {
                totalPosition = positionCacheManager.getTotalPositionCache().get(key);
            } else {
                totalPosition = totalPosition.initTotalPosition(contractInfo, account, traderNo);
                positionCacheManager.cacheTotalPosition(key, totalPosition);
            }

            totalPosition.setBuyPos(buyPositionManager);
            totalPosition.setSellPos(sellPositionManager);
            // route(totalPosition);
            // 存储并更新dataview
            updateAndStoreTotalPosition(key, totalPosition);

        } finally {
            lock.unlock();
        }
    }

    public Positions getPosition(String traderNo, String key, String bsFlag, String account, String userName, String businessType, ContractInfoBasic contractInfo) {
        Positions pos = new Positions();
        // 获取持仓**//
        if (positionCacheManager.getPositionCache().containsKey(key)) {
            pos = positionCacheManager.getPositionCache().get(key);
            // 确保持仓里面的合约信息是最新的信息//
            if(pos.getContractInfo()!=null && !Objects.equals(pos.getContractInfo().getSymbol(), "")) {
                pos.setContractInfo(contractInfo);
            }
        }else{
            TotalPosition totalPosition = new TotalPosition();
            InitPositions initPositions = new InitPositions(key, traderNo, bsFlag, totalPosition, contractInfo, account, userName, businessType);
            if(BusinessConstant.SPOT.equals(contractInfo.getInventoryType())){
                pos.init(initPositions);
            }
            if(positionCacheManager.getTotalPositionManagerCache().containsKey(key)){
                pos = positionCacheManager.getTotalPositionManagerCache().get(key);
                // 确保合约信息是最新的信息//
                if(pos.getContractInfo()!=null && !Objects.equals(pos.getContractInfo().getSymbol(), "")) {
                    pos.setContractInfo(contractInfo);
                }
            }else {
                TotalPosition totalPositionNew = new TotalPosition();
                String contractType = contractInfo.getInventoryType();
                if(positionCacheManager.getTotalPositionCache().containsKey(key)){
                    totalPositionNew = positionCacheManager.getTotalPositionCache().get(key);
                    if(BusinessConstant.SPOT.equals(contractType)){
                        if(BusinessConstant.BUY_SIDE.equals(bsFlag)){
                            pos = totalPositionNew.getBuyPos();
                        }else{
                            pos = totalPositionNew.getSellPos();
                        }
                    }else{
                        pos = totalPositionNew.getBuyPos();
                    }
                }else{
                    InitPositions initPositionsNew = new InitPositions(key, traderNo, bsFlag, totalPositionNew, contractInfo, account, userName, businessType);
                    pos.init(initPositionsNew);
                }
            }
            if(Objects.isNull(pos)){
                // 如果新加持仓为空 dimple不会响应，这里会造成上面取到null
                pos = new Positions();
                InitPositions initPositionsNew = new InitPositions(key, traderNo, bsFlag, totalPosition, contractInfo, account, userName, businessType);
                pos.init(initPositionsNew);
            }
        }
        return pos;
    }

    @Override
    public void newOrderCheck(NewOrder order) {
        String orderId = "";
        try {
            orderId = order.getOrderId();
            // 1. 记录日志信息
            log.info("receive New Order, 需要校验持仓信息,订单编号信息: " + orderId);
            // 2. 获取境内外标识
            String domesticType = order.getDomesticType();
            // 3. 如果是发送给dimple的订单，则需要进行持仓校验
            if (BusinessConstant.DOMESTIC_TYPE_INNER.equals(domesticType)) {
                log.info("订单是发送给dimple,需要进行持仓校验。订单编号: " + orderId);
                // 4. 保存报单持仓情况 todo
                // positionRequestManager.cacheParentRequest(order.getSymbol(), null);
                // 5. 校验持仓是否足够 -> 实现核心业务逻辑
                PositionCheckResult positionCheckResult = isPermissionOrder(order);
                if (!positionCheckResult.isPermission()) {
                    // 发送拒单
                    log.warn("OMS订单管理接收订单请求成功。验证持仓/库存失败，无法发送订单请求!订单编号: " + orderId);
                    throw new PositionCheckException(positionCheckResult.getErrorId(), positionCheckResult.getErrorMessage());
                }
            } else {
                // 8. 外发订单，先交易后结算，可以先不进行冻结，不校验持仓信息
                log.info("订单发送境外渠道，不进行持仓校验。订单编号: " + orderId);
            }
        } catch (Exception e) {
            log.error("订单持仓校验异常", e);
            throw new PositionCheckException(String.valueOf(OrderStatus.INTERNAL_FAILED.getStatusCode()), "订单持仓校验异常");
        }
    }

    @Override
    public void freezePosition(NewOrder order){
        try{
            log.debug("订单冻结持仓，订单编号: " + order.getOrderId());
            String domesticType = order.getDomesticType();
            if (BusinessConstant.DOMESTIC_TYPE_INNER.equals(domesticType)) {
                // 境内订单才需要下发解冻持仓位
                updatePos(order);
            }
        }catch (Exception e){
            log.error("订单冻结持仓异常", e);
        }
    }

    private PositionCheckResult isPermissionOrder(NewOrder order) {
        // 模拟Apama脚本中的OmsPositionOrder对象
        // PositionCheckResult omsPositionOrder = new PositionCheckResult(permission: true, errorId: "", errorMessage: "");
        // 模拟默认放行
        boolean isTrue = true;
        String errorId = "";
        String msg = "";
        return new PositionCheckResult(isTrue, errorId, msg);
    }

    private void updatePos(NewOrder newOrder) {
        // *交易品种*//
        String symbol = newOrder.getSymbol();
        // *境内外交易*//
        String domesticType = newOrder.getDomesticType();
        // *交易对手。必须填。一外进行信息。UBS-Fix、JPMC-Fix等*//
        String counterParty = newOrder.getExchCode() + ".FIX";
        // *账户*//
        String account = "";
        if (BusinessConstant.DOMESTIC_TYPE_INNER.equals(domesticType)) {
            account = newOrder.getMemberId();
        } else {
            account = counterParty;
        }
        // *获取持仓管理对象*//
        String flag = BusinessConstant.BUY_SIDE;
        Lock lock = positionLockProvider.getLock(account + symbol);
        lock.lock();
        try {
            String serviceId = "OmsRiskService";
            String traderNo = newOrder.getTraderNo();
            String userName = newOrder.getUserName();
            String businessType = newOrder.getBusinessType();
            String tradePurpose = newOrder.getTradePurpose();
            ContractInfoBasic contractInfo = basicParamCacheManager.getContractInfo(symbol);
            String buyKey = serviceId + account + symbol + userName + businessType + traderNo + flag;
            Positions buyPositionManager = getPosition(traderNo, buyKey, flag, account, userName, businessType, contractInfo);
            
            flag = BusinessConstant.SELL_SIDE;
            String sellKey = serviceId + account + symbol + userName + businessType + traderNo + flag;
            Positions sellPositionManager = getPosition(traderNo, sellKey, flag, account, userName, businessType, contractInfo);
            
            String bsFlag = newOrder.getSide();
            String eoFlag = newOrder.getOpenFlag();
            BigDecimal price = newOrder.getPrice();
            BigDecimal qty = newOrder.getOrderQty();
            String contractType = contractInfo.getInventoryType();
            String orderState = BusinessConstant.NEW;
            
            if (!BusinessConstant.SPOT.equals(contractType)) {
                log.debug("更新持仓。订单信息: {}", newOrder.toString());
                buyPositionManager.posUpdate(bsFlag, eoFlag, qty, price, orderState);
                positionCacheManager.cachePositionManager(buyKey, buyPositionManager);
                sellPositionManager.posUpdate(bsFlag, eoFlag, qty, price, orderState);
                positionCacheManager.cachePositionManager(sellKey, sellPositionManager);
            }
            // 更新总体持仓信息
            // updateTotalPosition(contractInfo, account, qty, price, bsFlag, eoFlag, traderNo, tradePurpose, orderState);
        } finally {
            lock.unlock();
        }
    }

    private static class PositionCheckResult {
        private final boolean permission;
        private final String errorId;
        private final String errorMessage;
        public PositionCheckResult(boolean permission, String errorId, String errorMessage) {
            this.permission = permission;
            this.errorId = errorId;
            this.errorMessage = errorMessage;
        }
        public boolean isPermission() { return permission; }
        public String getErrorId() { return errorId; }
        public String getErrorMessage() { return errorMessage; }
    }

    public boolean isTriggerCompare() { return isTriggerCompare; }
    public void setTriggerCompare(boolean triggerCompare) {
        this.isTriggerCompare = triggerCompare;
    }
}

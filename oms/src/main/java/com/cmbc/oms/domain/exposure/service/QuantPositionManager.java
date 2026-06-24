package com.cmbc.oms.domain.exposure.service;

import com.cmbc.common.util.ShardingThreadPool;
import com.cmbc.mds.forex.distribution.channel.PlxPricesHandler;
import com.cmbc.mds.forex.distribution.channel.impl.StrategyExecutionChannel;
import com.cmbc.mds.forex.quotes.dto.PlxPrices;
import com.cmbc.oms.domain.event.ContractInfoBasic;
import com.cmbc.oms.domain.exposure.entity.PositionBalanceEntity;
import com.cmbc.oms.domain.exposure.entity.PositionBalanceHistoryEntity;
import com.cmbc.oms.domain.exposure.model.FolderPosition;
import com.cmbc.oms.domain.exposure.model.PositionSnapshot;
import com.cmbc.oms.domain.facade.strategy.api.ExecutionReportListener;
import com.cmbc.oms.domain.facade.strategy.api.QuantPositionUpdateListener;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.infrastructure.cache.BasicParamCacheManager;
import com.cmbc.oms.infrastructure.dao.PositionBalanceHistoryMapper;
import com.cmbc.oms.infrastructure.dao.PositionBalanceMapper;
import com.cmbc.oms.infrastructure.facadeimpl.strategy.OmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 量化头寸平盘管理器
 * * 职责：量化自营头寸更新计算
 */
@Order(2)
@Slf4j
@Service
public class QuantPositionManager implements PlxPricesHandler, CommandLineRunner, ExecutionReportListener {
    
    // 内存头寸字典  key: folderId
    private final Map<String, FolderPosition> positionCache = new ConcurrentHashMap<>();
    
    // 依赖注入数据库操作类
    @Autowired
    private PositionBalanceMapper positionBalanceMapper;
    
    @Autowired
    private PositionBalanceConvert convert;
    @Autowired
    private BasicParamCacheManager basicParamCacheManager;
    @Autowired
    private StrategyExecutionChannel subscribeChannel;
    @Autowired
    private PositionBalanceHistoryMapper historyMapper;
    @Autowired
    private OmsService omsService;
    
    private final List<QuantPositionUpdateListener> listeners = new CopyOnWriteArrayList<>();
    public void registerListener(QuantPositionUpdateListener listener) { listeners.add(listener); }
    
    // 异步执行数量
    private final int SHARD_COUNT = 4;
    
    // 异步更新池
    private final ShardingThreadPool positionUpdatePool = new ShardingThreadPool(SHARD_COUNT, 
            "QuantPos-thread", 1000);
            
    // 订阅行情的合约号
    private final Map<String, Boolean> subscribeSymbols = new ConcurrentHashMap<>();
    //缓存行情快照供空头换算
    private ConcurrentHashMap<String, BigDecimal> mktPrices = new ConcurrentHashMap<>();
    
    //用到的头寸观察者
    @Autowired
    private List<IPositionFolderRouter> folderRouter;
    private Map<String, ContractInfoBasic> contractInfoCache;
    // 重复去重处理，最多允许缓存500条记录，超过淘汰最老的Order信息
    private final Map<String, Boolean> processedExchCache = 
            new LinkedHashMap<String, Boolean>(500, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 500;
        }
    };
    
    //同步单线程刷新库
    private final ExecutorService positionUpdatePoolExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "QuantPos-thread");
        t.setDaemon(true);
        return t;
    });
    
    @PreDestroy
    public void shutdown() {
        if(positionUpdatePoolExecutor != null) {
            positionUpdatePoolExecutor.shutdown();
        }
    }
    
    public void notifyPositionUpdate() {
        listeners.forEach(listener -> listener.onQuantPositionUpdate());
    }
    
    @Override
    public void run(String... args) {
        
        omsService.registerListener(this);
        // 1 基础信息加载
        this.contractInfoCache = basicParamCacheManager.getAllContractInfo();
        // 2 加载db到内存
        positionBalanceMapper.getAllPositionBalance().forEach(positionBalanceEntity -> {
            FolderPosition folderPosition = positionCache.computeIfAbsent(
                    positionBalanceEntity.getFolderId(), k -> new FolderPosition(positionBalanceEntity.getFolderId()));
            log.info("加载数据库头寸, {}", positionBalanceEntity);
            folderPosition.putSnapShot(convert.toBo(positionBalanceEntity));
            positionCache.put(folderPosition.getFolderId(), folderPosition);
            subscribeIfNeeded(positionBalanceEntity.getSymbol());
        });
        // 打印默认加载内容
        log.info("初始化头寸管理器，加载现存头寸完成，...contractInfoCache:{}", contractInfoCache);
    }
    
    private String determineFolder(ExecutionReport executionReport) {
        if (folderRouter == null || folderRouter.isEmpty()) {
            return "DEFAULT";
        }
        
        //todo: 增加失效路由断言，且优先抛转...
        for (IPositionFolderRouter router : folderRouter) {
            if (router.support(executionReport)) {
                return router.route(executionReport);
            }
        }
        
        return "DEFAULT";
    }
    
    //动态订阅机制
    private void subscribeIfNeeded(String symbol) {
        if (subscribeSymbols.putIfAbsent(symbol, true) == null) {
            try {
                String handlerId = "ExposureManager";
                subscribeChannel.registerBySymbol(symbol, handlerId, this);
                log.info("成功向分发渠道注册持仓监听, symbol: {}", symbol);
            } catch (Exception e) {
                subscribeSymbols.remove(symbol);
                log.error("向分发渠道注册持仓监听失败, symbol: {}", symbol, e);
            }
        }
    }
    
    // ************************* 3、暴露供调用的查询基础接口 *************************
    
    /**
     * (1) 获取查询指定头寸，如果不存在对象，返回新的零值头寸
     */
    public PositionSnapshot getPosition(String folderId, String symbol, String domesticType) {
        FolderPosition folderPosition = positionCache.get(folderId);
        if (folderPosition == null) {
            return buildNewPosition(folderId, symbol);
        }
        PositionSnapshot snapshot = folderPosition.getOrCreateSnapShot(symbol, contractInfoCache.get(symbol).getUnit(), domesticType);
        if (snapshot == null) {
            return buildNewPosition(folderId, symbol);
        }
        
        // 使用 synchronized 确保在此期间其它引发头寸变化了一次（比如释放了冻结的投机持仓）的中间态
        synchronized (snapshot) {
            return snapshot;
        }
    }
    
    // 获取汇总头寸报表
    public PositionSnapshot getTotalPosition(String folderId) {
        FolderPosition folderPosition = positionCache.computeIfAbsent(folderId, k -> new FolderPosition(folderId));
        return folderPosition.getTotalPosition();
    }
    
    public FolderPosition getFolderPosition(String folderId) {
        return positionCache.get(folderId); // 现存头寸盒子
    }
    
    // 获取初始化头寸
    private PositionSnapshot buildNewPosition(String folderId, String symbol) {
        return new PositionSnapshot(folderId, symbol, contractInfoCache.get(symbol).getUnit(), contractInfoCache.get(symbol).getDomesticType());
    }
    
    // ************************* 2、内部处理(事前) *************************
    
    /**
     * 事前冻结新单量，增加冻结量 (防超买超卖)
     */
    public void freezePosition(Object order) { // 这里用Object暂代NewOrder
        // 省略实现因为原图是NewOrder，这里假设有一个通用的order方法，这里为了完整性就简写
    }
    
    // ************************* 3、成交通知处理(事中) *************************
    
    /**
     * 处理 OMS 传过来的订单事件
     */
    @Override
    public void onAccept(ExecutionReport executionReport) {
        String routingKey = executionReport.getSymbol();
        //归属Folder可通过路由规则
        positionUpdatePool.execute(routingKey, () -> {
            log.info("收到报单回报事件，更新头寸，{}", executionReport);
            String folderId = determineFolder(executionReport);
            String symbol = executionReport.getSymbol();
            
            // 1、内部防重复处理
            //防重复，优先用 execId ，没值用 orderId + status
            String dedupKey = !StringUtils.isEmpty(executionReport.getExecId()) ?
                    executionReport.getExecId() : executionReport.getOrderId() + executionReport.getStatus();
                    
            if (processedExchCache.containsKey(dedupKey)) {
                log.info("已缓存处理回报事件，跳过, key:{}", dedupKey);
                return;
            } else {
                processedExchCache.put(dedupKey, true);
            }
            
            FolderPosition folderPosition = positionCache.computeIfAbsent(folderId, k -> new FolderPosition(folderId));
            //如果之前没有被缓存到，内存强制缓存一次防止越界
            if (contractInfoCache.isEmpty()) {
                contractInfoCache = basicParamCacheManager.getAllContractInfo();
            }
            PositionSnapshot snapshot = folderPosition.getOrCreateSnapShot(symbol, 
                    contractInfoCache.get(symbol).getUnit(), executionReport.getDomesticType());
            snapshot.setUpdateTime(LocalDateTime.now());
            BigDecimal dealQty = executionReport.getLastQty();
            log.info("准备执行释放并增加实持持仓, {}", snapshot);
            
            subscribeIfNeeded(executionReport.getSymbol());
            if ("BUY".equalsIgnoreCase(executionReport.getSide())) {
                snapshot.unfreezeAndAddLong(dealQty, executionReport.getLastAmt()); // 释放冻结量，增加多头
            } else {
                snapshot.unfreezeAndAddShort(dealQty, executionReport.getLastAmt());
            }
            snapshot.calFloatPnl(mktPrices.get(executionReport.getSymbol()));
            log.info("头寸更新完成, {}", snapshot);
            // 2、内存通知发布
            notifyPositionUpdate();
            // 3、异步落库
            persistPositionChanges(snapshot);
        });
    }
    
    @Override
    public void onCancel(ExecutionReport executionReport) {
        String routingKey = executionReport.getSymbol();
        //todo:后续可优化
        positionUpdatePool.execute(routingKey, () -> {
            log.info("收到撤单回报事件，更新头寸，{}", executionReport);
            handleUnfreeze(executionReport);
        });
    }
    
    @Override
    public void onReject(ExecutionReport executionReport) {
        String routingKey = executionReport.getSymbol();
        positionUpdatePool.execute(routingKey, () -> {
            log.info("收到废单回报事件，更新头寸，{}", executionReport);
            handleUnfreeze(executionReport);
        });
    }
    
    @Override
    public void onAck(ExecutionReport executionReport) {
        
    }
    
    public void handleUnfreeze(ExecutionReport executionReport) {
        String folderId = determineFolder(executionReport);
        String symbol = executionReport.getSymbol();
        
        // 1、内部防重复处理
        //防重复，优先用 execId ，没值用 orderId + status
        String dedupKey = !StringUtils.isEmpty(executionReport.getExecId()) ?
                executionReport.getExecId() : executionReport.getOrderId() + executionReport.getStatus();
        if (processedExchCache.containsKey(dedupKey)) {
            log.info("已缓存处理回报事件，跳过, key:{}", dedupKey);
            return;
        } else {
            processedExchCache.put(dedupKey, true);
        }
        
        FolderPosition folderPosition = positionCache.computeIfAbsent(folderId, k -> new FolderPosition(folderId));
        PositionSnapshot snapshot = folderPosition.getOrCreateSnapShot(symbol, 
                contractInfoCache.get(symbol).getUnit(), executionReport.getDomesticType());
        log.info("准备执行解冻操作, {}", snapshot);
        // 撤单或废单，只释放对应挂单量，不增加实持；需要重新获取剩余排队挂单
        BigDecimal remainingUnfilled = executionReport.getOrderQty().subtract(executionReport.getCumQty());
        if ("BUY".equalsIgnoreCase(executionReport.getSide())) {
            snapshot.unfreezeLong(remainingUnfilled);
        } else {
            snapshot.unfreezeShort(remainingUnfilled);
        }
        
        log.info("解冻/废单头寸更新完成, {}", snapshot);
        // 2、内存通知发布
        notifyPositionUpdate();
        // 3、异步落库
        persistPositionChanges(snapshot);
    }
    
    public void onExecutionReport(ExecutionReport executionReport) {
        //Todo: 增加了执行回报统一分发处理
        log.info("收到执行事件，订单状态:{}, {}", executionReport.getStatus(), executionReport);
        String folderId = determineFolder(executionReport);
        String symbol = executionReport.getSymbol();
        
        // 1、内部防重复处理
        //防重复，优先用 execId ，没值用 orderId + status
        String dedupKey = !StringUtils.isEmpty(executionReport.getExecId()) ?
                executionReport.getExecId() : executionReport.getOrderId() + executionReport.getStatus();
                
        synchronized (processedExchCache) {
            if (processedExchCache.containsKey(dedupKey)) {
                log.info("已缓存处理执行事件，跳过, key:{}", dedupKey);
                return;
            } else {
                processedExchCache.put(dedupKey, true);
            }
        }
        
        FolderPosition folderPosition = positionCache.computeIfAbsent(folderId, k -> new FolderPosition(folderId));
        // 如果之前没有被缓存到，内存强制缓存一次防止越界
        if (contractInfoCache.isEmpty()) {
            contractInfoCache = basicParamCacheManager.getAllContractInfo();
        }
        PositionSnapshot snapshot = folderPosition.getOrCreateSnapShot(symbol, contractInfoCache.get(symbol).getUnit(), 
                executionReport.getDomesticType());
        snapshot.setUpdateTime(LocalDateTime.now());
        BigDecimal dealQty = executionReport.getLastQty();
        log.info("准备执行计算分派头寸更新, {}", snapshot);
        if ("2".equals(executionReport.getStatus()) || "1".equals(executionReport.getStatus())) {
            subscribeIfNeeded(executionReport.getSymbol());
            if ("BUY".equalsIgnoreCase(executionReport.getSide())) {
                snapshot.unfreezeAndAddLong(dealQty, executionReport.getLastAmt()); // 释放冻结量，增加多头
            } else {
                snapshot.unfreezeAndAddShort(dealQty, executionReport.getLastAmt());
            }
            snapshot.calFloatPnl(mktPrices.get(executionReport.getSymbol()));
            
        } else if ("4".equals(executionReport.getStatus()) || "8".equals(executionReport.getStatus()) || "5".equals(executionReport.getStatus())) {
            // 撤单或废单，只释放对应挂单量，不增加实持；需要重新获取剩余排队挂单
            BigDecimal remainingUnfilled = executionReport.getOrderQty().subtract(executionReport.getCumQty());
            if ("BUY".equalsIgnoreCase(executionReport.getSide())) {
                snapshot.unfreezeLong(remainingUnfilled);
            } else {
                snapshot.unfreezeShort(remainingUnfilled);
            }
            
        } else {
            log.info("未知头寸变化：{}", snapshot);
            return;
        }
        log.info("头寸更新完成, {}", snapshot);
        // 2、内存通知发布
        notifyPositionUpdate();
        persistPositionChanges(snapshot);
    }
    
    // 异步落库
    private void persistPositionChanges(PositionSnapshot snapshot) {
        PositionBalanceEntity positionBalanceEntity;
        positionBalanceEntity = convert.toEntity(snapshot);
        try {
            positionBalanceMapper.updatePositionBalance(positionBalanceEntity);
            log.info("头寸数据刷新异步落库完成，{}", positionBalanceEntity);
        } catch (Exception e) {
            log.error("头寸数据刷新落库失败，{}", positionBalanceEntity, e);
        }
    }
    
    //接受行情报价通知
    @Override
    public void onPlxPrices(PlxPrices plxPrices) {
        try {
            if (plxPrices == null || plxPrices.getPrices() == null) {
                // log.info("收到空行情消息......");
                return;
            }
            if(plxPrices.getSymbol().equals("XAUUSD")){
                // log.info("收到行情消息: {}", JSONObject.toJSONString(plxPrices)); //todo 测试 日志
            }
            String symbol = plxPrices.getSymbol();
            
            String folderId = "MgapHedge";
            BigDecimal basePrice;
            if (plxPrices.getMktPx() != null) {
                basePrice = plxPrices.getMktPx();
            } else {
                log.info("收到无效行情 symbol {}, ", plxPrices.getSymbol());
                return;
            }
            mktPrices.put(plxPrices.getSymbol(), basePrice);
            for (FolderPosition folderPosition : positionCache.values()) {
                PositionSnapshot snapshot = folderPosition.getSnapShot(symbol);
                if (snapshot != null) {
                    snapshot.updateMarketData(basePrice);
                }
            }
            
        } catch (Exception e) {
            log.error("头寸管理器处理行情异常！", e);
        }
        
    }
    
    /**
     * 总部结转任务，每天 16:30:00 触发，保存当天的头寸快照。
     */
    @Scheduled(cron = "0 30 16 * * ?") //todo 暂时没落库测试
    public void persistDailyPositionSnapshot() {
        log.info("定时结转快照任务启动...");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        for (FolderPosition folderPosition : positionCache.values()) {
            for (PositionSnapshot snapshot : folderPosition.getSymbolPositions().values()) {
                PositionBalanceHistoryEntity historyEntity = new PositionBalanceHistoryEntity();
                synchronized (snapshot) {
                    // 我这里可以只更新快照信息 positionId
                    historyEntity.setPositionId(snapshot.getPositionId() + "_" + today);
                    historyEntity.setFolderId(snapshot.getFolderId());
                    historyEntity.setSymbol(snapshot.getSymbol());
                    historyEntity.setLongQty(snapshot.getLongQty());
                    historyEntity.setShortQty(snapshot.getShortQty());
                    historyEntity.setLongWeight(snapshot.getLongWeight());
                    historyEntity.setShortWeight(snapshot.getShortWeight());
                    historyEntity.setLongAmount(snapshot.getLongAmount());
                    historyEntity.setShortAmount(snapshot.getShortAmount());
                    historyEntity.setUpdateTime(snapshot.getUpdateTime());
                    historyEntity.setCreateTime(snapshot.getCreateTime());
                    historyEntity.setDomesticType(snapshot.getDomesticType());
                    historyEntity.setUnit(snapshot.getUnit());
                    // 结转日期
                    historyEntity.setStatisticDate(today);
                }
                try {
                    historyMapper.insertHistory(historyEntity);
                    log.info("保存历史结转头寸成功: {}", historyEntity.getPositionId());
                } catch (Exception e) {
                    log.error("保存历史结转头寸失败: {}", historyEntity.getPositionId(), e);
                }
            }
        }
        log.info("结转头寸快照任务处理完成");
    }
}

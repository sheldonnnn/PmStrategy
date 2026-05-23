package com.cmbc.oms.domain.exposure.service;

import com.cmbc.mds.distribution.PloyPrices;
import com.cmbc.mds.distribution.PloyPricesHandler;
import com.cmbc.oms.dao.PositionBalanceMapper;
import com.cmbc.oms.domain.basic.BasicParamCacheManager;
import com.cmbc.oms.domain.exposure.model.FolderPosition;
import com.cmbc.oms.domain.exposure.model.PositionSnapshot;
import com.cmbc.oms.domain.order.model.ContractInfoBasic;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.NewOrder;
import com.cmbc.oms.domain.order.model.OrderUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PreDestroy;
import com.cmbc.oms.domain.exposure.entity.PositionBalanceEntity;

import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
public class QuantPositionManager implements PloyPricesHandler, CommandLineRunner {


        // 内存头寸字典 Key: FolderId
        private final Map<String, FolderPosition> positionCache = new ConcurrentHashMap<>();

        // 依赖注入数据库操作层
         @Autowired private PositionBalanceMapper balanceMapper;
        // @Autowired private PositionFlowMapper flowMapper;
        @Autowired
        private PositionBalanceConvert convert;
        @Autowired
        private BasicParamCacheManager basicParamCacheManager;
        @Autowired
        private StrategyExecutionChannel subscribeChannel;
        @Autowired
        private ConcurrentHashMap<String,BigDecimal> mktPrices = new ConcurrentHashMap<>();
    
    
    
        // 订阅防重池
        private final ConcurrentHashMap<String, Boolean> subscribedSymbols = new ConcurrentHashMap<>();
        // 最新行情快照池
        private final ConcurrentHashMap<String, BigDecimal> marketPrices = new ConcurrentHashMap<>();

        // 使用 LinkedHashMap 实现的轻量级 LRU 缓存，最多保留最近的 10000 条回报记录
        private final Map<String, Boolean> processedExecCache = new java.util.LinkedHashMap<String, Boolean>(10000, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > 10000; // 超过 1 万条自动丢弃最旧的
            }
        };

        // 单线程异步落库执行器：保证落库按时间顺序执行，避免数据库并发锁冲突，也不阻塞行情/订单线程
        private final ExecutorService persistExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Position-Persist-Thread");
            t.setDaemon(true);
            return t;
        });

        @PreDestroy
        public void destroy() {
            if (persistExecutor != null) {
                persistExecutor.shutdown();
            }
        }

        @Autowired
        private List<IPositionFolderRouter> folderRouter;
        private Map<String, ContractInfoBasic> contractInfoCache;

        @Override
        public void run(String... args) {
            //1 查询合约乘数
            this.contractInfoCache = basicParamCacheManager.getContractInfo();
            balanceMapper.getAllPositionBalance().forEach(positionBalanceEntity -> {
                FolderPosition folderPosition = positionCache.computeIfAbsent(positionBalanceEntity.getFolderId(), k -> new FolderPosition(positionBalanceEntity.getFolderId()));
                folderPosition.putSnapShot(convert.toBo(positionBalanceEntity));
                positionCache.put(folderPosition.getFolderId(),folderPosition);
                subscribeIfNeeded(positionBalanceEntity.getSymbol());
            });
            log.info("初始化头寸管理组件，加载期初头寸完成...");
        }

        // 内部辅助方法，用来挑选匹配的 Router 以决策 Folder
        private String determineFolder(OrderUpdate event) {
            if (folderRouter == null || folderRouter.isEmpty()) {
                return "DEFAULT"; // Fallback to a default folder if none registered
            }
            for (IPositionFolderRouter router : folderRouter) {
                if (router.supports(event)) {
                    return router.route(event);
                }
            }
            return "DEFAULT";
        }

    private void subscribeIfNeeded(String symbol) {
        if (subscribedSymbols.putIfAbsent(symbol, Boolean.TRUE) == null) {
            try {
                // 【改变2】所有合约共享同一个 Handler ID 命名空间，并直接传入 this
                String handlerId = "PositionService_Global_PnL_Handler";

                subscribeChannel.registerBySymbol(symbol, handlerId, this);

                log.info("成功向 MDS 注册新增合约行情监听: {}", symbol);
            } catch (Exception e) {
                subscribedSymbols.remove(symbol);
                log.error("向 MDS 注册行情监听失败! Symbol: {}", symbol, e);
            }
        }
    }

        // ================== 1. 供策略调用的极速查询接口 ==================

        /**
         * O(1) 极速查询某个头组下，所有合约的可用头寸合计
         */
//        public List<ReadOnlyPosition> getAllPositions(String folderId) {
//            FolderPosition folderPosition = folderCache.get(folderId);
//            if (folderPosition == null) {
//                return java.util.Collections.emptyList();
//            }
//            return folderPosition.getAllPositions();
//        }

        /**
         * O(1) 极速查询某个头组下所有合约的【汇总大盘】头寸
         */
        public PositionSnapshot getTotalPosition(String folderId) {
            FolderPosition folderPosition = positionCache.computeIfAbsent(folderId, k -> new FolderPosition(folderId));
            return folderPosition.getTotalPosition();
        }

        /**
         * O(1) 极速查询可用头寸，返回不可变对象
         */
        public FolderPosition getFolderPosition(String folderId) {
            return positionCache.get(folderId);
        }

        // ================== 2. 冻结管理 (事前) ==================

        /**
         * 策略发单前调用，增加冻结量 (防超买超卖)
         */
        public void freezePosition(NewOrder newOrder) {
            String folderId = determineFolder(newOrder);
            FolderPosition folderPosition = positionCache.computeIfAbsent(folderId, k -> new FolderPosition(folderId));
            PositionSnapshot snapshot = folderPosition.getOrCreateSnapshot(newOrder.getSymbol(), contractInfoCache.get(newOrder.getSymbol()).getUnit(),newOrder.getDomesticType());

            if ("0".equalsIgnoreCase(newOrder.getSide())) {
                snapshot.freezeLong(newOrder.getQuantity(), newOrder.getPrice()); // 默认无价格冻结
            } else {
                snapshot.freezeShort(newOrder.getQuantity(), newOrder.getPrice());
            }
            log.info("头寸冻结完成，冻结后头寸数据: {}", snapshot);
        }

        // ================== 3. 成交与流水处理 (事后) ==================

        /**
         * 处理 OMS 传回的订单事件 (由各策略实例的 SingleThreadExecutor 异步单线程调用)
         */
        public void onExecutionReport(ExecutionReport executionReport) {
            log.info("处理订单事件,订单状态: {},{}", executionReport.getStatus(), executionReport);

            // --- 1. 内存级轻量防重处理 ---
            // 构造防重 Key：优先使用 execId，若没有则用 orderId + status
            String dedupKey = executionReport.getExecId() != null 
                    ? "EXEC_" + executionReport.getExecId() 
                    : "ORD_" + executionReport.getOrderId() + "_" + executionReport.getStatus();

            synchronized (processedExecCache) {
                if (processedExecCache.containsKey(dedupKey)) {
                    log.warn("检测到重复的订单回报推送，直接丢弃避免头寸双重计算! Key: {}", dedupKey);
                    return;
                }
                processedExecCache.put(dedupKey, Boolean.TRUE);
            }

            // 【修复】统一路由：保持与 freezePosition 相同的逻辑，避免冻结和解冻路由不一致导致冻结泄漏
            String folderId = determineFolder(executionReport);
            String symbol = executionReport.getSymbol(); // 假设Event里有此字段

            // 1. 幂等校验：查库判断 event.getMatchNo() 是否在 QUANT_POSITION_FLOW 已存在
            // if (flowMapper.exists(event.getMatchNo())) { return; }

            FolderPosition folderPosition = positionCache.computeIfAbsent(folderId, k -> new FolderPosition(folderId));
            PositionSnapshot snapshot = folderPosition.getOrCreateSnapshot(symbol, contractInfoCache.get(symbol).getUnit(), executionReport.getDomesticType());
            snapshot.setUpdateTime(LocalDateTime.now());

            BigDecimal dealQty = executionReport.getLastQty();
            log.info("交易更新前头寸: {}", snapshot);

            // 2. 内存原子更新  TODO 枚举统一管理
            if ("2".equals(executionReport.getStatus()) || "3".equals(executionReport.getStatus())) {
                subscribeIfNeeded(executionReport.getSymbol());
                if ("BUY".equalsIgnoreCase(executionReport.getSide())) {
                    snapshot.unfreezeAndAddLong(dealQty, executionReport.getLastAmt()); // 释放对应冻结，增加多头
                } else {
                    snapshot.unfreezeAndAddShort(dealQty, executionReport.getLastAmt());
                }
                snapshot.calFloatPnl(mktPrices.get(executionReport.getSymbol()));

            } else if ("6".equals(executionReport.getStatus()) || "5".equals(executionReport.getStatus()) || "-2".equals(executionReport.getStatus())) { // 撤单或者拒单处理
                // 撤单或废单：仅释放剩余冻结，不加头寸 TODO: 是否需要考虑部分成交
                BigDecimal remainingUnfilled = executionReport.getOrderQty().subtract(executionReport.getLastQty() == null ? BigDecimal.ZERO : executionReport.getLastQty());
                if ("BUY".equalsIgnoreCase(executionReport.getSide())) {
                    snapshot.unfreezeLong(remainingUnfilled);
                } else {
                    snapshot.unfreezeShort(remainingUnfilled);
                }

            } else {
                log.info("头寸无需更新");
                return;
            }

            log.info("头寸更新完成: {}", snapshot);

            // 3. 异步触发落库
            persistPositionChangesAsync(snapshot);
        }

        private void persistPositionChangesAsync(PositionSnapshot snapshot){
            if (snapshot == null) return;

            // 1. 拷贝当前内存切片：必须在 synchronized 块内获取瞬时快照状态
            // 避免提交到队列后，在等待落库的这段时间数据被后续行情/成交修改
            PositionBalanceEntity entity = new PositionBalanceEntity();
            synchronized (snapshot) {
                entity.setPositionId(snapshot.getPositionId());
                entity.setFolderId(snapshot.getFolderId());
                entity.setSymbol(snapshot.getSymbol());
                entity.setLongQty(snapshot.getLongQty());
                entity.setShortQty(snapshot.getShortQty());
                entity.setLongWeight(snapshot.getLongWeight());
                entity.setShortWeight(snapshot.getShortWeight());
                entity.setLongAmount(snapshot.getLongAmount());
                entity.setShortAmount(snapshot.getShortAmount());
                entity.setUpdateTime(snapshot.getUpdateTime());
                entity.setCreateTime(snapshot.getCreateTime());
                entity.setDomesticType(snapshot.getDomesticType());
                entity.setUnit(snapshot.getUnit());
            }

            // 2. 扔给单线程执行器异步排队落库
            persistExecutor.submit(() -> {
                try {
                    // 调用持久化层执行 UPSERT 操作（根据 positionId 存在则更新，不存在则插入）
                    balanceMapper.saveOrUpdate(entity);
                    // log.debug("异步落库头寸成功: {}", entity.getPositionId()); // 可作为调试用
                } catch (Exception e) {
                    log.error("异步落库头寸失败! PositionId: {}", entity.getPositionId(), e);
                    // 实盘如果持久化报错，系统通常依靠重启重建内存状态，或者对接告警系统人工介入
                }
            });
        }

    public void onPloyPrices(PloyPrices ployPrices) {
        try {
            if (ployPrices == null || ployPrices.getSymbol() == null) {
                log.warn("接收到的行情为空！");
                return;
            }

            if (!ployPrices.getSymbol().equals("XAUUSD")) {
//                log.info("接收到行情数据: {}", JSONObject.toJSONString(ployPrices)); // todo 测试日志
            }

            String symbol = ployPrices.getSymbol();
            BigDecimal basePrice;

            if (ployPrices.getMidPx() != null) {
                basePrice = ployPrices.getMidPx();
            } else if (ployPrices.getBestAskPx() != null) {
                basePrice = ployPrices.getBestAskPx();
            } else if (ployPrices.getBestBidPx() != null) {
                basePrice = ployPrices.getBestBidPx();
            } else {
                log.warn("无有效行情！symbol: {}", ployPrices.getSymbol());
                return;
            }

            mktPrices.put(ployPrices.getSymbol(), basePrice);

            // 恢复主动推模型：由于行情频次不高（十几笔/秒），直接在行情线程中更新计算，保证查询接口的极致性能
            for (FolderPosition folderPosition : positionCache.values()) {
                PositionSnapshot snapshot = folderPosition.getSnapshot(symbol);
                if (snapshot != null) {
                    snapshot.updateMarketData(basePrice);
                }
            }

        } catch (Exception e) {
            log.error("头寸管理行情处理异常！", e);
        }
    }

}

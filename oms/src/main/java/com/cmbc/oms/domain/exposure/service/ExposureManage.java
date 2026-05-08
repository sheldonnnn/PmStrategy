package com.cmbc.oms.domain.exposure.service;

import com.cmbc.mds.distribution.PloyPrices;
import com.cmbc.mds.distribution.PloyPricesHandler;
import com.cmbc.oms.dao.PositionBalanceMapper;
import com.cmbc.oms.domain.basic.BasicParamCacheManager;
import com.cmbc.oms.domain.exposure.cash.*;
import com.cmbc.oms.domain.order.model.ContractInfoBasic;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.NewOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
public class ExposureManage implements PloyPricesHandler, CommandLineRunner {


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
            FolderPosition folderPosition = positionCache.computeIfAbsent(folderId,k->new FolderPosition());
            return folderPosition.getTotalPosition();
        }

        /**
         * O(1) 极速查询可用头寸，返回不可变对象
         */
        public FolderPosition getFolderPosition(String folderId) {
            FolderPosition folderPosition = positionCache.get(folderId);
//            if (folderPosition == null) {
//                return new ReadOnlyPosition(folderId, symbol,
//                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
//                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
//                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
//            }
//
//            PositionSnapshot snapshot = folderPosition.getSnapshot(symbol);
//            if (snapshot == null) {
//                return new ReadOnlyPosition(folderId, symbol,
//                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
//                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
//                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
//            }
//
//            // 使用 synchronized 确保在此瞬间拷贝时不发生只拷贝了一半（比如释放了冻结还没加持仓）的中间态
//            synchronized (snapshot) {
//                return new ReadOnlyPosition(
//                        folderId, symbol,
//                        snapshot.getLongQty(), snapshot.getShortQty(),
//                        snapshot.getFrozenLongQty(), snapshot.getFrozenShortQty(),
//                        snapshot.getLongWeight(), snapshot.getShortWeight(),
//                        snapshot.getLongAmount(), snapshot.getShortAmount(),
//                        snapshot.getFrozenLongWeight(), snapshot.getFrozenShortWeight(),
//                        snapshot.getFrozenLongAmount(), snapshot.getFrozenShortAmount()
//                );
//            }
            return folderPosition;
        }

        // ================== 2. 冻结管理 (事前) ==================

        /**
         * 策略发单前调用，增加冻结量 (防超买超卖)
         */
        public void freezePosition(NewOrder newOrder) {
            String folderId = "MgapHedge";
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
            String folderId = "MgapHedge";
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

            FolderPosition folderPosition = positionCache.get(folderId);
            if (folderPosition != null) {
                PositionSnapshot snapshot = folderPosition.getSnapshot(symbol);
                if (snapshot != null) {
                    snapshot.setMktPrice(ployPrices.getMidPx());
                    snapshot.setDepthUpdateTime(LocalDateTime.now());
                    snapshot.calFloatPnl(ployPrices.getMidPx());
                }
            }

        } catch (Exception e) {
            log.error("头寸管理行情处理异常！", e);
        }
    }

}

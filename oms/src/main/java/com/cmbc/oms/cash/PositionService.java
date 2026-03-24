package com.cmbc.oms.cash;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
public class PositionService {


        // 内存头寸字典 Key: FolderId
        private final Map<String, FolderPosition> folderCache = new ConcurrentHashMap<>();

        // 全局防重复水位线：记录已经被汇总进期初或重算的最新流水号
        private long currentWatermarkId = 0L;

        // 依赖注入数据库操作层
        // @Autowired private PositionBalanceMapper balanceMapper;
        // @Autowired private PositionFlowMapper flowMapper;

        @Autowired
        private List<IPositionFolderRouter> folderRouters;

        @PostConstruct
        public void init() {
            // TODO: 系统启动时，从 QUANT_POSITION_BALANCE 表加载当日期初数据到 positionCache
            log.info("初始化头寸管理组件，加载期初头寸完成...");
        }

        // 内部辅助方法，用来挑选匹配的 Router 以决策 Folder
        private String determineFolder(OrderUpdate event) {
            if (folderRouters == null || folderRouters.isEmpty()) {
                return "DEFAULT"; // Fallback to a default folder if none registered
            }
            for (IPositionFolderRouter router : folderRouters) {
                if (router.supports(event)) {
                    return router.route(event);
                }
            }
            return "DEFAULT";
        }

        // ================== 1. 供策略调用的极速查询接口 ==================

        /**
         * O(1) 极速查询某个头组下，所有合约的可用头寸合计
         */
        public List<ReadOnlyPosition> getAllPositions(String folderId) {
            FolderPosition folderPosition = folderCache.get(folderId);
            if (folderPosition == null) {
                return java.util.Collections.emptyList();
            }
            return folderPosition.getAllPositions();
        }

        /**
         * O(1) 极速查询某个头组下所有合约的【汇总大盘】头寸
         */
        public ReadOnlyPosition getTotalPosition(String folderId) {
            FolderPosition folderPosition = folderCache.get(folderId);
            if (folderPosition == null) {
                // 如果没有头组数据，返回一个全是 0 的大盘视角
                return new ReadOnlyPosition(folderId, "TOTAL",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            return folderPosition.getTotalPosition();
        }

        /**
         * O(1) 极速查询可用头寸，返回不可变对象
         */
        public ReadOnlyPosition getPosition(String folderId, String symbol) {
            FolderPosition folderPosition = folderCache.get(folderId);
            if (folderPosition == null) {
                return new ReadOnlyPosition(folderId, symbol,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }

            PositionSnapshot snapshot = folderPosition.getSnapshot(symbol);
            if (snapshot == null) {
                return new ReadOnlyPosition(folderId, symbol,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }

            // 使用 synchronized 确保在此瞬间拷贝时不发生只拷贝了一半（比如释放了冻结还没加持仓）的中间态
            synchronized (snapshot) {
                return new ReadOnlyPosition(
                        folderId, symbol,
                        snapshot.getLongQty(), snapshot.getShortQty(),
                        snapshot.getFrozenLongQty(), snapshot.getFrozenShortQty(),
                        snapshot.getLongWeight(), snapshot.getShortWeight(),
                        snapshot.getLongAmount(), snapshot.getShortAmount(),
                        snapshot.getFrozenLongWeight(), snapshot.getFrozenShortWeight(),
                        snapshot.getFrozenLongAmount(), snapshot.getFrozenShortAmount()
                );
            }
        }

        // ================== 2. 冻结管理 (事前) ==================

        /**
         * 策略发单前调用，增加冻结量 (防超买超卖)
         */
        public void freezePosition(String folderId, String strategyId, String symbol, String side, BigDecimal qty) {
            FolderPosition folderPosition = folderCache.computeIfAbsent(folderId, k -> new FolderPosition(folderId));
            PositionSnapshot snapshot = folderPosition.getOrCreateSnapshot(symbol);

            if ("BUY".equalsIgnoreCase(side)) {
                snapshot.freezeLong(qty, null); // 默认无价格冻结
            } else {
                snapshot.freezeShort(qty, null);
            }
            log.info("头寸冻结完成: {} {} qty={}", strategyId, side, qty);
        }

        // ================== 3. 成交与流水处理 (事后) ==================

        /**
         * 处理 OMS 传回的订单事件 (由各策略实例的 SingleThreadExecutor 异步单线程调用)
         */
        public void handleOrderEvent(OrderUpdate event) {
            // A. 【特权通道】无锁处理后台发来的“影子倒换”事件
            if (event instanceof RecalculateCmdEvent) {
                RecalculateCmdEvent cmd = (RecalculateCmdEvent) event;
                // 单点内存引用替换！瞬间完成交接
                folderCache.put(cmd.getShadowPosition().getFolderId(), cmd.getShadowPosition());
                // 抬高生命水位线，以此作为后续实时流水的重叠过滤标准
                this.currentWatermarkId = cmd.getMaxProcessedMatchNo();
                log.info("【重置完成】影子头组载入成功！{} 当前水位线推进至 {}", 
                         cmd.getShadowPosition().getFolderId(), this.currentWatermarkId);
                return;
            }

            // B. 【防重防污染通道】水位线拦截
            // 假设 MatchNo 是在数据库自增的流水 ID (为演示方便转为 long)
            if (event.getMatchNo() != null) {
                try {
                    long currentMatchId = Long.parseLong(event.getMatchNo());
                    if (currentMatchId <= currentWatermarkId) {
                        log.warn("水位线拦截: 流水 {} 已经被包含在最新快照/重算中，安全丢弃！", currentMatchId);
                        return;
                    }
                } catch (NumberFormatException e) {
                    log.error("流水解析异常，建议使用递增数字主键作为水位线", e);
                }
            }

            String folderId = determineFolder(event);
            String symbol = event.getSymbol(); // 假设Event里有此字段

            // 1. 幂等校验: 查库判断 event.getMatchNo() 是否在 QUANT_POSITION_FLOW 已存在
            // if (flowMapper.exists(event.getMatchNo())) { return; }

            FolderPosition folderPosition = folderCache.computeIfAbsent(folderId, k -> new FolderPosition(folderId));
            PositionSnapshot snapshot = folderPosition.getOrCreateSnapshot(symbol);

            // 回复使用的 filledQty，并且如果上层调用方没有提供有效订单价则传入 null 避免异常
            BigDecimal filledQty = event.getFilledQty();

            // 2. 内存原子更新
            if ("FILLED".equals(event.getStatus()) || "PARTIALLY_FILLED".equals(event.getStatus())) {
                if ("BUY".equalsIgnoreCase(event.getSide())) {
                    snapshot.unfreezeAndAddLong(filledQty, filledQty, null); // 释放对应冻结，增加多头
                } else {
                    snapshot.unfreezeAndAddShort(filledQty, filledQty, null);
                }
            }
            else if ("CANCELED".equals(event.getStatus()) || "REJECTED".equals(event.getStatus())) {
                // 撤单或废单：仅释放剩余冻结量，不加持仓
                BigDecimal remainingUnfilled = event.getOrderQty().subtract(event.getAccumulatedFillQty());
                if ("BUY".equalsIgnoreCase(event.getSide())) {
                    snapshot.unfreezeLong(remainingUnfilled);
                } else {
                    snapshot.unfreezeShort(remainingUnfilled);
                }
            }

            // 3. 异步触发落库 (插入 FLOW, 乐观锁更新 BALANCE)
            // persistPositionChangesAsync(snapshot, event);
        }

        // ================== 4. 管理端手工/跑批重算 (事后/异步) ==================

        /**
         * 模拟管理端触发头寸重算（由 Web Tomcat 独立线程调用，不堵塞实时交易）
         */
        public void triggerRecalculate(String folderId) {
            log.info("启动后台耗时重算任务: {}", folderId);
            // 1. 在完全独立的局部内存里，new 一个影子 Folder
            FolderPosition shadowPosition = new FolderPosition(folderId);
            long maxFoundMatchNo = 0L;

            try {
                // 2. 模拟耗时查库：查当天之前的 "期初余额"
                // List<PositionBalanceEntity> balances = balanceMapper.getBalances(folderId, "T-1");
                // ... 累加到 shadowPosition 中

                // 3. 模拟耗时查库：查该 folderId 今天的 "所有日内成交流水"
                // List<OrderUpdateEntity> flows = flowMapper.getTodayFlows(folderId);
                // for(OrderUpdateEntity flow : flows) {
                //     PositionSnapshot snap = shadowPosition.getOrCreateSnapshot(flow.getSymbol());
                //     snap.unfreezeAndAddLong(flow.getFilledQty(), ...);
                //     maxFoundMatchNo = Math.max(maxFoundMatchNo, flow.getMatchNo()); 
                // }
                
                // 为了演示，假装我们查到了 100 笔单子且最新流水号是 9999
                maxFoundMatchNo = 9999L; 
                Thread.sleep(2000); // 假装很慢，完全不会卡住真正的业务
                
            } catch (Exception e) {
                log.error("后台重算查询失败，影子副本直接被 GC，不影响线上任何内存数据", e);
                return; 
            }

            // 4. 重算成功，封装为特权指令扔给上面那个单线程的 handleOrderEvent 去处理更新
            RecalculateCmdEvent cmdEvent = new RecalculateCmdEvent(shadowPosition, maxFoundMatchNo);
            
            // 下方代码仅为架构模拟，在您真正的系统里，应被推入那个处理 OrderUpdate 的单线程队列里去排队
            // orderUpdateDisruptor.publishEvent(cmdEvent);
            this.handleOrderEvent(cmdEvent); // 直调模拟排队
        }

}

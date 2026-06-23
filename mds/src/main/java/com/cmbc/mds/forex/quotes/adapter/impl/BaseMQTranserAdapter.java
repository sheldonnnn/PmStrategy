package com.cmbc.mds.forex.quotes.adapter.impl;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.common.utils.ServiceNameUtils;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.GradsPrice;
import com.cmbc.mds.forex.quotes.dto.MQTranserBean;
import com.cmbc.mds.forex.quotes.adapter.AbstractQuoteAdapter;
import org.springframework.util.StringUtils;
import com.cmbc.mds.forex.common.utils.SymbolUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理 MQTranserBean 的通用适配器基类
 * 包含原 QuoteReceiver 中的 convertToDepth 逻辑
 */
public abstract class BaseMQTranserAdapter extends AbstractQuoteAdapter<MQTranserBean> {

    @Override
    protected Depth convertToDepth(MQTranserBean bean, String source, String provider) {
        if (bean.getGradsPriceList() == null || bean.getGradsPriceList().isEmpty()) {
            log.warn("行情数据缺少价格阶梯列表: {}", bean.getNameid());
            return null;
        }

        Depth depth = new Depth();

        // 1. 基础字段映射
        String originalSymbol = StringUtils.hasText(bean.getSymbol()) ? bean.getSymbol() : bean.getExnm();
        depth.setSymbol(SymbolUtils.formatSymbol(originalSymbol));
        // 设置原始服务名
        depth.setProvider(provider);
        depth.setSource(source);
        depth.setServiceName(bean.getServiceId());
        depth.setCreateTime(System.currentTimeMillis());

        List<GradsPrice> priceList = bean.getGradsPriceList();
        int levelCount = priceList.size();

        // 2. 初始化列表
        List<BigDecimal> bidPrices = new ArrayList<>(levelCount);
        List<BigDecimal> askPrices = new ArrayList<>(levelCount);
        List<BigDecimal> bidQuantities = new ArrayList<>(levelCount);
        List<BigDecimal> asksQuantities = new ArrayList<>(levelCount);

        // 临时列表用于聚合
        List<String> askOriginator = new ArrayList<>(levelCount);
        List<String> bidOriginator = new ArrayList<>(levelCount);
        List<String> askForwardPointsList = new ArrayList<>(levelCount);
        List<String> askSpotRateList = new ArrayList<>(levelCount);
        List<String> bidForwardPointsList = new ArrayList<>(levelCount);
        List<String> bidSpotRateList = new ArrayList<>(levelCount);
        List<String> askSwapPointList = new ArrayList<>(levelCount);
        List<String> bidSwapPointList = new ArrayList<>(levelCount);
        List<String> ask2PriceList = new ArrayList<>(levelCount);
        List<String> bid2PriceList = new ArrayList<>(levelCount);

        String firstBidQuoteId = null;
        String firstAskQuoteId = null;

        Map<String, String> extraParams = new ConcurrentHashMap<>();

        // 3. 属性拷贝
        if (StringUtils.hasText(bean.getExnm())) {
            extraParams.put(BaseConstants.KEY_EXNM, bean.getExnm());
        }
        if (StringUtils.hasText(bean.getNameid())) {
            extraParams.put(BaseConstants.KEY_NAMEID, bean.getNameid());
        }
        if (StringUtils.hasText(bean.getTpfg())) {
            extraParams.put(BaseConstants.KEY_TPFG, bean.getTpfg());
        }
        if (StringUtils.hasText(bean.getTerm())) {
            extraParams.put(BaseConstants.KEY_TERM, bean.getTerm());
        }
        if (StringUtils.hasText(bean.getCxfg())) {
            extraParams.put(BaseConstants.KEY_CXFG, bean.getCxfg());
        }
        if (StringUtils.hasText(bean.getStfg())) {
            extraParams.put(BaseConstants.KEY_STFG, bean.getStfg());
        }
        if (StringUtils.hasText(bean.getTrfg())) {
            extraParams.put(BaseConstants.KEY_TRFG, bean.getTrfg());
        }
        if (StringUtils.hasText(bean.getStrike())) {
            extraParams.put(BaseConstants.KEY_STRIKE, bean.getStrike());
        }
        if (StringUtils.hasText(bean.getValueDay())) {
            extraParams.put(BaseConstants.KEY_VALUEDAY, bean.getValueDay());
        }
        if (StringUtils.hasText(bean.getIntime())) {
            extraParams.put(BaseConstants.KEY_INTIME, bean.getIntime());
        }
        if (StringUtils.hasText(bean.getOuttime())) {
            extraParams.put(BaseConstants.KEY_OUTTIME, bean.getOuttime());
        }

        String serviceName = bean.getServiceId();

        // 遍历价格阶梯
        for (int i = 0; i < priceList.size(); i++) {
            GradsPrice gp = priceList.get(i);
            String idx = String.valueOf(i + 1);

            // A. 价格与数量按买卖方向成对写入，单侧失败不影响另一侧
            boolean validBid = appendSideIfValid(
                    "bid", gp.getBid(), gp.getBidSize(), bidPrices, bidQuantities, i + 1);
            boolean validAsk = appendSideIfValid(
                    "ask", gp.getAsk(), gp.getAskSize(), askPrices, asksQuantities, i + 1);

            if (validBid) {
                // B. Bid 层级扩展字段
                if (StringUtils.hasText(gp.getBidCurrency())) {
                    extraParams.put(BaseConstants.KEY_BID_PREFIX + idx + BaseConstants.KEY_CURRENCY_SUFFIX,
                            gp.getBidCurrency());
                }
                if (StringUtils.hasText(gp.getBidExpireTime())) {
                    extraParams.put(BaseConstants.KEY_BID_PREFIX + idx + BaseConstants.KEY_EXPIRE_TIME_SUFFIX,
                            gp.getBidExpireTime());
                }

                // C. Bid 聚合列表收集
                if (StringUtils.hasText(gp.getBidEntryOriginator())) {
                    bidOriginator.add(gp.getBidEntryOriginator());
                }
                if (StringUtils.hasText(gp.getBidForwardPoints())) {
                    bidForwardPointsList.add(gp.getBidForwardPoints());
                }
                if (StringUtils.hasText(gp.getBidEntrySpotRate())) {
                    bidSpotRateList.add(gp.getBidEntrySpotRate());
                }
                if (StringUtils.hasText(gp.getBidEntrySwapPoints())) {
                    bidSwapPointList.add(gp.getBidEntrySwapPoints());
                }
                if (StringUtils.hasText(gp.getBid2())) {
                    bid2PriceList.add(gp.getBid2());
                }

                // D. Bid 行情源报价ID
                if (firstBidQuoteId == null && StringUtils.hasText(gp.getBidSq())) {
                    firstBidQuoteId = gp.getBidSq();
                }
            }

            if (validAsk) {
                // B. Ask 层级扩展字段
                if (StringUtils.hasText(gp.getAskCurrency())) {
                    extraParams.put(BaseConstants.KEY_ASK_PREFIX + idx + BaseConstants.KEY_CURRENCY_SUFFIX,
                            gp.getAskCurrency());
                }
                if (StringUtils.hasText(gp.getAskExpireTime())) {
                    extraParams.put(BaseConstants.KEY_ASK_PREFIX + idx + BaseConstants.KEY_EXPIRE_TIME_SUFFIX,
                            gp.getAskExpireTime());
                }

                // C. Ask 聚合列表收集
                if (StringUtils.hasText(gp.getAskEntryOriginator())) {
                    askOriginator.add(gp.getAskEntryOriginator());
                }
                if (StringUtils.hasText(gp.getAskForwardPoints())) {
                    askForwardPointsList.add(gp.getAskForwardPoints());
                }
                if (StringUtils.hasText(gp.getAskEntrySpotRate())) {
                    askSpotRateList.add(gp.getAskEntrySpotRate());
                }
                if (StringUtils.hasText(gp.getAskSwapPoints())) {
                    askSwapPointList.add(gp.getAskSwapPoints());
                }
                if (StringUtils.hasText(gp.getAsk2())) {
                    ask2PriceList.add(gp.getAsk2());
                }

                // D. Ask 行情源报价ID
                if (firstAskQuoteId == null && StringUtils.hasText(gp.getAskSq())) {
                    firstAskQuoteId = gp.getAskSq();
                }
            }
        }

        // 4. 特殊服务名处理 (LC-FIX)
        if (BaseConstants.SERVICE_LC_FIX.equals(serviceName)) {
            String marketId = null;
            if (BaseConstants.VAL_MARKET_ID_FXQDM.equals(marketId)) {
                serviceName = BaseConstants.SERVICE_LCQDM_FIX;
            } else {
                serviceName = BaseConstants.SERVICE_LCODM_FIX;
            }
            depth.setProvider(serviceName);
        }
        extraParams.put(BaseConstants.SERVICE_NAME_KEY1, serviceName);

        // 5. 聚合列表转 String 放入 ExtraParams
        if (!askOriginator.isEmpty()) {
            extraParams.put(BaseConstants.KEY_ASK_ORIGINATOR, askOriginator.toString());
        }
        if (!bidOriginator.isEmpty()) {
            extraParams.put(BaseConstants.KEY_BID_ORIGINATOR, bidOriginator.toString());
        }
        if (!askForwardPointsList.isEmpty()) {
            extraParams.put(BaseConstants.CONST_ASK_FWD_POINTS, askForwardPointsList.toString());
        }
        if (!bidForwardPointsList.isEmpty()) {
            extraParams.put(BaseConstants.CONST_BID_FWD_POINTS, bidForwardPointsList.toString());
        }
        if (!askSpotRateList.isEmpty()) {
            extraParams.put(BaseConstants.CONST_ASK_SPOT_RATE, askSpotRateList.toString());
        }
        if (!bidSpotRateList.isEmpty()) {
            extraParams.put(BaseConstants.CONST_BID_SPOT_RATE, bidSpotRateList.toString());
        }
        if (!askSwapPointList.isEmpty()) {
            extraParams.put(BaseConstants.CONST_ASK_SWAP_POINTS, askSwapPointList.toString());
        }
        if (!bidSwapPointList.isEmpty()) {
            extraParams.put(BaseConstants.CONST_BID_SWAP_POINTS, bidSwapPointList.toString());
        }
        if (!ask2PriceList.isEmpty()) {
            extraParams.put(BaseConstants.KEY_ASK2_SEQ, ask2PriceList.toString());
        }
        if (!bid2PriceList.isEmpty()) {
            extraParams.put(BaseConstants.KEY_BID2_SEQ, bid2PriceList.toString());
        }

        // 6. 设置最终对象

        if (firstBidQuoteId != null) {
            depth.setQuoteId(firstBidQuoteId);
        } else if (firstAskQuoteId != null) {
            depth.setQuoteId(firstAskQuoteId);
        } else {
            depth.setQuoteId(null);
        }
        depth.setBidPrices(bidPrices);
        depth.setAskPrices(askPrices);
        depth.setBidQuantities(bidQuantities);
        depth.setAskQuantities(asksQuantities);
        depth.setExtraParams(extraParams);

        if (bidPrices.size() != bidQuantities.size() || askPrices.size() != asksQuantities.size()) {
            log.error("行情价格与数量列表长度不一致: source={}, provider={}, symbol={}",
                    source, provider, depth.getSymbol());
            return null;
        }

        if (bidPrices.isEmpty() && askPrices.isEmpty()) {
            return null;
        }

        return depth;
    }

    private boolean appendSideIfValid(
            String side,
            String priceText,
            String quantityText,
            List<BigDecimal> prices,
            List<BigDecimal> quantities,
            int level) {
        boolean hasPrice = StringUtils.hasText(priceText);
        boolean hasQuantity = StringUtils.hasText(quantityText);

        if (!hasPrice && !hasQuantity) {
            return false;
        }
        if (!hasPrice || !hasQuantity) {
            log.warn("Level {} {} 行情缺少价格或数量: price={}, quantity={}",
                    level, side, priceText, quantityText);
            return false;
        }

        try {
            BigDecimal price = new BigDecimal(priceText);
            BigDecimal quantity = new BigDecimal(quantityText);
            prices.add(price);
            quantities.add(quantity);
            return true;
        } catch (NumberFormatException e) {
            log.warn("Level {} {} 行情数字转换异常: price={}, quantity={}",
                    level, side, priceText, quantityText);
            return false;
        }
    }
}

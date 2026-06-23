package com.cmbc.mds.forex.quotes.adapter.impl;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.common.constants.InterConstants;
import com.cmbc.mds.forex.quotes.adapter.AbstractQuoteAdapter;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.DimpleKsdQuoteEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component("Adapter_DIMPLE")
public class DimpleQuoteAdapter extends AbstractQuoteAdapter<DimpleKsdQuoteEvent> {

    private static final DateTimeFormatter KSD_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
    private static final int ZERO_DEPTH_LEVEL_COUNT = 10;
    private static final double MIN_VALID_PRICE = 0.000001D;

    @Override
    protected Depth convertToDepth(DimpleKsdQuoteEvent payload, String source, String provider) {
//        if (payload.getKsdSeqNo() == 0) {
//            log.debug("忽略Dimple初始化报文，SeqNo=0");
//            return null;
//        }

        boolean tenLevelAllZero = isTenLevelAllZero(payload);

        if (isEmpty(payload.getBidPrices()) && isEmpty(payload.getAskPrices())) {
            log.warn("Dimple行情缺少买卖盘数据，SeqNo={}", payload.getKsdSeqNo());
            return null;
        }

        Depth depth = new Depth();
        depth.setQuoteId(String.valueOf(payload.getKsdSeqNo()));
        depth.setSymbol(payload.getSymbol());
        depth.setSource(source);
        depth.setProvider(provider);
        depth.setServiceName(source);
        depth.setCreateTime(System.currentTimeMillis());

        Long eventTime = parseKsdTime(payload);
        depth.setTransactionTime(eventTime);
        depth.setSendingTime(eventTime);

        depth.setBidPrices(toPriceList(payload.getBidPrices(), payload.getBidVolumes()));
        depth.setBidQuantities(toQuantityList(payload.getBidPrices(), payload.getBidVolumes()));
        depth.setAskPrices(toPriceList(payload.getAskPrices(), payload.getAskVolumes()));
        depth.setAskQuantities(toQuantityList(payload.getAskPrices(), payload.getAskVolumes()));

        Map<String, String> extraParams = new ConcurrentHashMap<>();
        extraParams.put(InterConstants.EXTRA_KEY_VALUE_TRADE_MODE, BaseConstants.TRADE_MODE_ODM);
        extraParams.put(InterConstants.EXTRA_KEY_VALUE_ISONEMAKER, BaseConstants.IS_ONE_MAKER_YES);
        extraParams.put(BaseConstants.SERVICE_NAME_KEY1, provider);
        if (StringUtils.hasText(payload.getTradingDay())) {
            extraParams.put(InterConstants.EXTRA_KEY_VALUE_TRADE_DATE, payload.getTradingDay());
        }
        if (StringUtils.hasText(payload.getUpdateTime())) {
            extraParams.put(InterConstants.EXTRA_KEY_VALUE_GEN_TIME, payload.getUpdateTime());
        }
        depth.setExtraParams(extraParams);

        if (depth.getBidPrices().isEmpty() && depth.getAskPrices().isEmpty() && !tenLevelAllZero) {
            return null;
        }
        return depth;
    }

    private List<BigDecimal> toPriceList(List<Double> prices, List<Integer> volumes) {
        List<BigDecimal> result = new ArrayList<>();
        if (prices == null) {
            return result;
        }
        for (int i = 0; i < prices.size(); i++) {
            Double price = prices.get(i);
            int volume = volumes != null && i < volumes.size() && volumes.get(i) != null ? volumes.get(i) : 0;
            if (price != null && price > MIN_VALID_PRICE && volume > 0) {
                result.add(BigDecimal.valueOf(price).setScale(4, RoundingMode.DOWN));
            }
        }
        return result;
    }

    private List<BigDecimal> toQuantityList(List<Double> prices, List<Integer> volumes) {
        List<BigDecimal> result = new ArrayList<>();
        if (prices == null) {
            return result;
        }
        for (int i = 0; i < prices.size(); i++) {
            Double price = prices.get(i);
            int volume = volumes != null && i < volumes.size() && volumes.get(i) != null ? volumes.get(i) : 0;
            if (price != null && price > MIN_VALID_PRICE && volume > 0) {
                result.add(BigDecimal.valueOf(volume));
            }
        }
        return result;
    }

    private boolean isTenLevelAllZero(DimpleKsdQuoteEvent payload) {
        return isSideTenLevelAllZero(payload.getBidPrices(), payload.getBidVolumes())
                && isSideTenLevelAllZero(payload.getAskPrices(), payload.getAskVolumes());
    }

    private boolean isSideTenLevelAllZero(List<Double> prices, List<Integer> volumes) {
        if (prices == null || volumes == null
                || prices.size() < ZERO_DEPTH_LEVEL_COUNT
                || volumes.size() < ZERO_DEPTH_LEVEL_COUNT) {
            return false;
        }

        for (int i = 0; i < ZERO_DEPTH_LEVEL_COUNT; i++) {
            Double price = prices.get(i);
            Integer volume = volumes.get(i);
            if (price == null || Math.abs(price) > MIN_VALID_PRICE) {
                return false;
            }
            if (volume == null || volume != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private Long parseKsdTime(DimpleKsdQuoteEvent payload) {
        String dateStr = payload.getTradingDay();
        String timeStr = payload.getUpdateTime();
        if (!StringUtils.hasText(dateStr)) {
            dateStr = java.time.LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        if (!StringUtils.hasText(timeStr)) {
            timeStr = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }

        try {
            LocalDateTime ldt = LocalDateTime.parse(dateStr.trim() + " " + timeStr.trim(), KSD_DATE_TIME_FORMATTER);
            return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException | NullPointerException e) {
            log.warn("Dimple时间解析失败 [{} {}]，使用系统当前时间", dateStr, timeStr);
            return System.currentTimeMillis();
        }
    }

    @Override
    protected void doProviderSpecificHandle(Depth depth, DimpleKsdQuoteEvent source) {
        log.debug("Dimple适配完成：SeqNo={}, Symbol={}, Levels={}",
                source.getKsdSeqNo(), depth.getSymbol(), depth.getBidPrices().size());
    }
}

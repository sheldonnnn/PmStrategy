package com.cmbc.mds.forex.quotes.adapter.impl;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.common.constants.InterConstants;
import com.cmbc.mds.forex.common.utils.DateAndTimeUtils;
import com.cmbc.mds.forex.common.utils.SymbolUtils;
import com.cmbc.mds.forex.quotes.adapter.AbstractQuoteAdapter;
import com.cmbc.mds.forex.quotes.dto.CmdsQuotePayload;
import com.cmbc.mds.forex.quotes.dto.Depth;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("Adapter_CMDS")
public class CMDSQuoteAdapter extends AbstractQuoteAdapter<CmdsQuotePayload> {

    @Override
    protected Depth convertToDepth(CmdsQuotePayload payload, String source, String provider) {
        List<CmdsQuotePayload.CmdsGradsPrice> gradsPrices = payload == null ? null : payload.getGradsPrices();
        if (gradsPrices == null || gradsPrices.isEmpty()) {
            log.warn("[CMDS] Quote missing gradsPrices, skipped. payload={}", payload);
            return null;
        }

        String symbol = normalizeSymbol(extractSymbol(payload));
        if (!hasText(symbol)) {
            log.warn("[CMDS] Quote missing exnm, skipped. payload={}", payload);
            return null;
        }

        Depth depth = new Depth();
        depth.setQuoteId(text(payload.getRunningNumber()));
        depth.setSymbol(symbol);
        depth.setSource(source);
        depth.setProvider(provider);
        depth.setServiceName(BaseConstants.SERVICE_NAME_CMDS);
        depth.setCreateTime(System.currentTimeMillis());

        List<BigDecimal> bidPrices = new ArrayList<>(gradsPrices.size());
        List<BigDecimal> askPrices = new ArrayList<>(gradsPrices.size());
        List<BigDecimal> bidQuantities = new ArrayList<>(gradsPrices.size());
        List<BigDecimal> askQuantities = new ArrayList<>(gradsPrices.size());

        // 当前 Map 只在单线程内组装 Depth，后续作为普通扩展参数读取，无需 ConcurrentHashMap。
        Map<String, String> extraParams = new HashMap<>(16 + gradsPrices.size() * 6);
        copyTopLevelExtraParams(payload, extraParams);

        int bidIndex = 1;
        int askIndex = 1;
        for (CmdsQuotePayload.CmdsGradsPrice level : gradsPrices) {
            if (level == null) {
                continue;
            }
            BigDecimal bid = decimal(level.getBid(), "bid");
            BigDecimal bidSize = decimal(level.getBidSize(), "bidSize");
            if (bid != null && bidSize != null) {
                bidPrices.add(bid);
                bidQuantities.add(bidSize);
                putLevelExtra(extraParams, BaseConstants.KEY_BID_PREFIX, bidIndex, level.getBidDate(), "Date");
                putLevelExtra(extraParams, BaseConstants.KEY_BID_PREFIX, bidIndex, level.getBidTime(), "Time");
                putLevelExtra(extraParams, BaseConstants.KEY_BID_PREFIX, bidIndex, level.getLevel(), "Level");
                bidIndex++;
            }

            BigDecimal ask = decimal(level.getAsk(), "ask");
            BigDecimal askSize = decimal(level.getAskSize(), "askSize");
            if (ask != null && askSize != null) {
                askPrices.add(ask);
                askQuantities.add(askSize);
                putLevelExtra(extraParams, BaseConstants.KEY_ASK_PREFIX, askIndex, level.getAskDate(), "Date");
                putLevelExtra(extraParams, BaseConstants.KEY_ASK_PREFIX, askIndex, level.getAskTime(), "Time");
                putLevelExtra(extraParams, BaseConstants.KEY_ASK_PREFIX, askIndex, level.getLevel(), "Level");
                askIndex++;
            }
        }

        if (bidPrices.isEmpty() && askPrices.isEmpty()) {
            log.warn("[CMDS] Quote has no valid bid/ask prices, skipped. quoteId={}", depth.getQuoteId());
            return null;
        }

        depth.setBidPrices(bidPrices);
        depth.setBidQuantities(bidQuantities);
        depth.setAskPrices(askPrices);
        depth.setAskQuantities(askQuantities);
        depth.setExtraParams(extraParams);
        return depth;
    }

    private void copyTopLevelExtraParams(CmdsQuotePayload payload, Map<String, String> extraParams) {
        putIfPresent(extraParams, BaseConstants.SERVICE_NAME_KEY1, BaseConstants.SERVICE_NAME_CMDS);
        putIfPresent(extraParams, BaseConstants.KEY_EXNM, text(payload.getExnm()));
        putIfPresent(extraParams, BaseConstants.KEY_TPFG, text(payload.getTpfg()));
        putIfPresent(extraParams, BaseConstants.KEY_TERM, text(payload.getTerm()));
        putIfPresent(extraParams, BaseConstants.KEY_CXFG, text(payload.getCxfg()));
        putIfPresent(extraParams, "prcd", text(payload.getPrcd()));
        putIfPresent(extraParams, "key", text(payload.getKey()));
        putIfPresent(extraParams, InterConstants.EXTRA_KEY_VALUE_QUOTE_TYPE_NAME, text(payload.getQuoteType()));

        String quoteType = text(payload.getQuoteType());
        String tradeMode = "ODM".equalsIgnoreCase(quoteType)
                ? BaseConstants.TRADE_MODE_ODM
                : BaseConstants.TRADE_MODE_QDM;
        putIfPresent(extraParams, InterConstants.EXTRA_KEY_VALUE_TRADE_MODE, tradeMode);

        String timestamp = normalizeTimestamp(text(payload.getTime()));
        putIfPresent(extraParams, InterConstants.EXTRA_KEY_VALUE_TIME, timestamp);
        putIfPresent(extraParams, InterConstants.EXTRA_KEY_VALUE_TIMESTAMP, timestamp);
    }

    private String extractSymbol(CmdsQuotePayload payload) {
        return payload == null ? null : text(payload.getExnm());
    }

    private String normalizeSymbol(String rawSymbol) {
        if (!hasText(rawSymbol)) {
            return null;
        }
        return SymbolUtils.formatSymbol(rawSymbol);
    }

    private String normalizeTimestamp(String rawTime) {
        if (!hasText(rawTime)) {
            return DateAndTimeUtils.getFormatTime(DateAndTimeUtils.TIME_FORMATTER_MILLISECOND);
        }
        String time = rawTime.trim();
        if (DateAndTimeUtils.isValidFormat(time, DateAndTimeUtils.TIME_FORMATTER_MILLISECOND)) {
            return time;
        }
        if (DateAndTimeUtils.isValidFormat(time, DateAndTimeUtils.TIME_FORMATTER_SECOND)) {
            String converted = DateAndTimeUtils.convert(time, DateAndTimeUtils.TIME_FORMATTER_SECOND,
                    DateAndTimeUtils.TIME_FORMATTER_MILLISECOND);
            if (converted != null) {
                return converted;
            }
        }
        return DateAndTimeUtils.getFormatTime(DateAndTimeUtils.TIME_FORMATTER_MILLISECOND);
    }

    private void putLevelExtra(Map<String, String> extraParams, String sidePrefix, int index, String rawValue,
            String targetSuffix) {
        String value = text(rawValue);
        if (hasText(value)) {
            extraParams.put(sidePrefix + index + "_" + targetSuffix, value);
        }
    }

    private void putIfPresent(Map<String, String> extraParams, String key, String value) {
        if (hasText(value)) {
            extraParams.put(key, value);
        }
    }

    private BigDecimal decimal(String value, String fieldName) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            log.warn("[CMDS] Numeric field conversion failed. field={}, value={}", fieldName, text);
            return null;
        }
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

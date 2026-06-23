package com.cmbc.mds.forex.quotes.dto;

import java.util.ArrayList;
import java.util.List;

public class DimpleKsdQuoteEvent {
    private String source;
    private String provider;
    private long ksdSeqNo;
    private boolean last;
    private String symbol;
    private String tradingDay;
    private String updateTime;
    private double openPrice;
    private double preClosePrice;
    private double upperLimitPrice;
    private double lowerLimitPrice;
    private long gatewayReceiveTime;
    private List<Double> bidPrices = new ArrayList<>();
    private List<Integer> bidVolumes = new ArrayList<>();
    private List<Double> askPrices = new ArrayList<>();
    private List<Integer> askVolumes = new ArrayList<>();

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public long getKsdSeqNo() {
        return ksdSeqNo;
    }

    public void setKsdSeqNo(long ksdSeqNo) {
        this.ksdSeqNo = ksdSeqNo;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getTradingDay() {
        return tradingDay;
    }

    public void setTradingDay(String tradingDay) {
        this.tradingDay = tradingDay;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(double openPrice) {
        this.openPrice = openPrice;
    }

    public double getPreClosePrice() {
        return preClosePrice;
    }

    public void setPreClosePrice(double preClosePrice) {
        this.preClosePrice = preClosePrice;
    }

    public double getUpperLimitPrice() {
        return upperLimitPrice;
    }

    public void setUpperLimitPrice(double upperLimitPrice) {
        this.upperLimitPrice = upperLimitPrice;
    }

    public double getLowerLimitPrice() {
        return lowerLimitPrice;
    }

    public void setLowerLimitPrice(double lowerLimitPrice) {
        this.lowerLimitPrice = lowerLimitPrice;
    }

    public long getGatewayReceiveTime() {
        return gatewayReceiveTime;
    }

    public void setGatewayReceiveTime(long gatewayReceiveTime) {
        this.gatewayReceiveTime = gatewayReceiveTime;
    }

    public List<Double> getBidPrices() {
        return bidPrices;
    }

    public void setBidPrices(List<Double> bidPrices) {
        this.bidPrices = bidPrices;
    }

    public List<Integer> getBidVolumes() {
        return bidVolumes;
    }

    public void setBidVolumes(List<Integer> bidVolumes) {
        this.bidVolumes = bidVolumes;
    }

    public List<Double> getAskPrices() {
        return askPrices;
    }

    public void setAskPrices(List<Double> askPrices) {
        this.askPrices = askPrices;
    }

    public List<Integer> getAskVolumes() {
        return askVolumes;
    }

    public void setAskVolumes(List<Integer> askVolumes) {
        this.askVolumes = askVolumes;
    }
}

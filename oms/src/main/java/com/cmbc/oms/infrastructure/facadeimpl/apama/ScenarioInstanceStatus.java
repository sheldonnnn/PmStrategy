package com.cmbc.oms.infrastructure.facadeimpl.apama;

import com.apama.services.scenario.IScenarioDefinition;
import com.apama.services.scenario.internal.ScenarioInstance;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.beans.PropertyChangeEvent;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * @Author: Cly
 * @Date: 2026/01/23  11:34
 * @Description:
 */
@Component
public class ScenarioInstanceStatus implements IScenarioInstanceStatus {

    private final static Logger logger = LoggerFactory.getLogger(ScenarioInstanceStatus.class);

    private ApplicationContext context = null; // SpringContextHolder.getApplicationContext();

    @Override
    public void instanceAdded(PropertyChangeEvent event) {
        putInstanceStateCache(event);
    }

    @Override
    public void instanceDied(PropertyChangeEvent event) { deleteInstanceStateCache(event); }

    @Override
    public void instanceEdited(PropertyChangeEvent event) {
    }

    @Override
    public void instanceUpdated(PropertyChangeEvent event) { putInstanceStateCache(event); }

    @Override
    public void instanceRemoved(PropertyChangeEvent event) {
        deleteInstanceStateCache(event);
    }

    @Override
    public void instanceStateChange(PropertyChangeEvent event) {
    }

    @Override
    public void instanceStateDiscoveryStatus(PropertyChangeEvent event) {
    }

    @Override
    public void instanceOther(PropertyChangeEvent event) {
    }

    /**
     * 新增和更新dataView条目
     * @param event
     */
    private void putInstanceStateCache(PropertyChangeEvent event) {
        //获取新值
        ScenarioInstance newValue = (ScenarioInstance) event.getNewValue();
        IScenarioDefinition definition = newValue.getScenarioDefinition();
        //判断dataView的key是否被监听
        if (!ApamaConstant.DataViewIgnoreEnum.getNameset().contains(definition.getDisplayName())) {
            return;
        }

        HashMap<String, Object> dataItem = getMapByEvent(newValue, definition);
        String key = String.format("%s%s", BusinessConstant.SCENARIO_DEFINITION_DATA, definition.getDisplayName());
        DataViewItem item = new DataViewItem();

        /**
         * 获取dataView得rowid
         */
        String itemRowId = getDataViewRowId(definition.getDisplayName(), dataItem);
        if (StringUtils.isEmpty(itemRowId)) {
            itemRowId = String.valueOf(newValue.getId());
        }

        //更新dataView得rowid值
        item.setRowId(itemRowId);

        /**
         * 统一缓存的hash key值
         */
        // if (cacheService.get(key, item.getRowId()) == null) {
        //     item.setType(DataViewEventType.ADD);
        // } else {
        //     item.setType(DataViewEventType.UPDATE);
        // }

        //更新缓存的值
        // cacheService.put(key, item.getRowId(), dataItem);

        item.setRow(dataItem);
        item.setDisplayName(definition.getDisplayName());
        DataViewItemEvent itemEvent = new DataViewItemEvent(item);
        if (context != null) context.publishEvent(itemEvent);
    }

    /**
     * 删除和销毁dataView条目
     * 
     * @param event
     */
    private void deleteInstanceStateCache(PropertyChangeEvent event) {
        // if(!cacheService.isAlive()) {
        //     logger.error("====================>{} 处于非服务状态，不能进行缓存的操作", cacheService.name());
        //     return;
        // }

        ScenarioInstance newValue = (ScenarioInstance) event.getNewValue();
        IScenarioDefinition definition = newValue.getScenarioDefinition();
        HashMap<String, Object> dataItem = getMapByEvent(newValue, definition);
        String key = String.format("%s%s", BusinessConstant.SCENARIO_DEFINITION_DATA, definition.getDisplayName());

        /**
         * 获取dataView得rowid
         */
        String itemRowId = getDataViewRowId(definition.getDisplayName(), dataItem);
        if (StringUtils.isEmpty(itemRowId)) {
            itemRowId = String.valueOf(newValue.getId());
        }

        //删除缓存得dv得row
        logger.debug("====================>删除交易系统同步的DataView,dv名称:{}, rowId:{},oldRowId:{}",
                definition.getDisplayName(), itemRowId, newValue.getId());

        DataViewItem item = new DataViewItem();
        item.setDisplayName(definition.getDisplayName());
        //更新dataView得rowid值
        item.setRowId(itemRowId);
        item.setType(DataViewEventType.DELETE);
        item.setRow(dataItem);
        DataViewItemEvent itemEvent = new DataViewItemEvent(item);
        if (context != null) context.publishEvent(itemEvent);
    }

    private HashMap<String, Object> getMapByEvent(ScenarioInstance newValue, IScenarioDefinition definition) {
        LinkedHashSet<String> keys = (LinkedHashSet<String>) definition.getOutputParameterNames();
        HashMap<String, Object> dataItem = new HashMap<>(keys.size());
        for (String k : keys) {
            dataItem.put(k, newValue.getValue(k));
        }
        return dataItem;
    }

    /**
     * 获取dataView得rowid
     * 
     * @param dvName
     * @param dataItem
     * @return
     */
    private String getDataViewRowId(final String dvName, final Map<String, Object> dataItem) {
        String dataItemKey = "";
        if (ApamaConstant.DataViewEnum.CREDIT_INFO_DATAVIEW.getName().equals(dvName)) {
            // 外汇行权信主键设定
            dataItemKey = String.valueOf(dataItem.get("id"));
        } else if (ApamaConstant.DataViewEnum.MAKER_INNER_RFQ_STATE_DATAVIE.getName().equals(dvName)) {
            // 外汇行权信主键设定
            dataItemKey = String.valueOf(dataItem.get("rfqId"));
        } else if (ApamaConstant.DataViewEnum.INNER_QUOTATION_RFQ_DEPTH_DATAVIE.getName().equals(dvName)) {
            // 外汇行权信主键设定
            dataItemKey = String.valueOf(dataItem.get("rfqId"));
        } else if (ApamaConstant.DataViewEnum.MAKER_INNER_RFQ_OFFER_USER_DATAVIE.getName().equals(dvName)) {
            // 外汇行权信主键设定
            dataItemKey = String.format("%s%s", dataItem.get("rfqId"), dataItem.get("offerUserName"));
        }
        /* 交易系统通知信息的dataView自己生成rowid */
        if (ApamaConstant.DataViewEnum.OPERATE_NOTICE_DATAVIEW.getName().equals(dvName)) {
            String myrowid = java.util.UUID.randomUUID().toString();
            dataItemKey = myrowid;
        }

        return dataItemKey;
    }
}

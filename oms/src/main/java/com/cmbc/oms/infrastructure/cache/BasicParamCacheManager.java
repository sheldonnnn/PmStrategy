package com.cmbc.oms.infrastructure.cache;

import com.cmbc.oms.domain.entity.ContractInfoEntity;
import com.cmbc.oms.domain.entity.ContractOutInfoEntity;
import com.cmbc.oms.domain.entity.DimpleUserEntity;
import com.cmbc.oms.domain.entity.SeatInfoEntity;
import com.cmbc.oms.domain.event.ContractInfoBasic;
import com.cmbc.oms.domain.position.model.entity.TraderNoClientMember;
import com.cmbc.oms.infrastructure.dao.ContractInfoMapper;
import com.cmbc.oms.infrastructure.dao.ContractOutInfoMapper;
import com.cmbc.oms.infrastructure.dao.DimpleUserMapper;
import com.cmbc.oms.infrastructure.dao.SeatInfoMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author cuijian
 * @date 2026/2/28
 * @time 15:18
 * @description 基础参数缓存管理类
 */
@Service
public class BasicParamCacheManager {
    private static final Logger log = LoggerFactory.getLogger(BasicParamCacheManager.class);

    @Autowired
    private ContractInfoMapper contractInfoMapper;
    @Autowired
    private ContractOutInfoMapper contractOutInfoMapper;
    @Autowired
    private DimpleUserMapper dimpleUserMapper;
    @Autowired
    private SeatInfoMapper seatInfoMapper;

    // 用于存储合约信息缓存 key 为 symbol
    public volatile Map<String, ContractInfoBasic> contractInfoCache = new ConcurrentHashMap<>(); // 合约参数缓存管理

    // 用于存储合约信息缓存 -- 主要用于原子级别的更新操作  key 为 symbol
    public final Map<String, ContractInfoBasic> newContractInfoCache = new ConcurrentHashMap<>(); // 合约
    // 用于存储交易员对应的memberId和clientId的缓存
    public final Map<String, TraderNoClientMember> traderMemberIdCache = new ConcurrentHashMap<>();

    // 用于存储登录的交易员用户信息的缓存
    public final List<String> userInfoCache = new CopyOnWriteArrayList<>();


    /**
     * 缓存初始化
     */
    @PostConstruct
    private void init() {
        cacheTraderMemberInfo();
        // dimple登录用户初始化
        cacheLogin();
        cacheLogout();
        cacheContractInfo(false); // 境内合约信息
        cacheContractOutInfo(false); // 境外合约信息
    }

    /**
     * 获取合约信息
     * symbol 合约编号
     */
    public ContractInfoBasic getContractInfo(String symbol) {
        ContractInfoBasic contractInfoBasic = contractInfoCache.get(symbol);
        if (null == contractInfoBasic) {
            // 触发数据库检索
            this.lisenUpadateContactInfo();
            // 重新获取合约信息
            return contractInfoCache.get(symbol);
        }
        return contractInfoBasic;
    }

    /**
     * 获取所有合约信息
     * symbol 合约编号
     */
    public Map<String, ContractInfoBasic> getAllContractInfo() {
        if (null == contractInfoCache) {
            // 重新对数据库进行物理统一次逻辑检索操作
            this.lisenUpadateContactInfo();
        }
        // 返回合约信息
        return contractInfoCache;
    }

    /**
     * 获取交易员所对应的memberId和clientId
     * key = MemberId+exchCode
     */
    public Map<String, TraderNoClientMember> getTraderMemberInfo() { return traderMemberIdCache; }


    /**
     * 获取登录的Dimple用户信息
     */
    public String getDimpleUserInfo() {
        if (userInfoCache.isEmpty()) {
            log.error("DIMPLE账号未登录");
            return null;
        } else {
            return userInfoCache.getFirst();
        }
    }


    /**
     * 获取交易员所对应的memberId和clientId
     */
    public void cacheTraderMemberInfo() {
        log.debug("开始缓存交易员会员信息");
        List<SeatInfoEntity> list = seatInfoMapper.findList();
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        // 开始缓存加载
        for (SeatInfoEntity seatInfoEntity : list) {
            // 模拟获取交易员信息，实际应该从数据库中获取
            TraderNoClientMember traderNoClientMember = new TraderNoClientMember();
            String key = seatInfoEntity.getSeatNo() + seatInfoEntity.getExchCode();
            traderNoClientMember.setTraderNo(seatInfoEntity.getDimpleUser());
            traderNoClientMember.setMemberId(seatInfoEntity.getSeatNo());
            traderNoClientMember.setClientId(seatInfoEntity.getClientId());
            traderNoClientMember.setExchCode(seatInfoEntity.getExchCode());
            traderMemberIdCache.put(key, traderNoClientMember);
        }
        log.debug("交易员会员信息缓存完成");
    }

    /**
     * 缓存登录的交易员用户信息
     */
    public void cacheLogin() {
        // 查询数据库已经登录的用户信息，进行缓存
        List<DimpleUserEntity> logoinDimpleUser = dimpleUserMapper.getLogoinDimpleUser();
        if (CollectionUtils.isEmpty(logoinDimpleUser)) {
            return;
        }
        for (DimpleUserEntity dimpleUserEntity : logoinDimpleUser) {
            userInfoCache.add(dimpleUserEntity.getDimpleUser());
        }
    }

    /**
     * 缓存登出的交易员用户信息
     */
    public void cacheLogout() {
        
    }
    
    /**
     * 缓存境内合约信息
     */
    public void cacheContractInfo(boolean synContrct) {
        // 初始化 查询数据库
        List<ContractInfoEntity> contractInfo = contractInfoMapper.getContractInfo();
        for (ContractInfoEntity contractInfoEntity : contractInfo) {
            ContractInfoBasic contractInfoBasic = new ContractInfoBasic();
            contractInfoBasic.setSymbol(contractInfoEntity.getContractID());
            contractInfoBasic.setVarietyId(contractInfoEntity.getVarietyID());
            contractInfoBasic.setUnit(contractInfoEntity.getUnit());
            contractInfoBasic.setMeasureUnit(contractInfoEntity.getMeasureUnit());
            contractInfoBasic.setTick(contractInfoEntity.getTick());
            contractInfoBasic.setExchCode(contractInfoEntity.getExchCode());
            contractInfoBasic.setDomesticType(contractInfoEntity.getDomesticType());
            contractInfoBasic.setInventoryType(contractInfoEntity.getContractType());
            contractInfoBasic.setEndDeliveryDate(contractInfoEntity.getEndDeliveryDate());
            contractInfoBasic.setHistoryContract("1".equals(contractInfoEntity.getIsHistoryContract()) ? 
                    Boolean.TRUE : Boolean.FALSE);
            contractInfoBasic.setCurrency(contractInfoEntity.getCurrency());
            contractInfoBasic.setAccuracy(contractInfoEntity.getAccuracy());
            contractInfoBasic.setStepPosition(contractInfoEntity.getStepPosition());
            contractInfoBasic.setStraightDiscForkType(null); //todo
            contractInfoBasic.setStraightDisc(null); //todo
            contractInfoBasic.setForkPlate(null); //todo
            contractInfoBasic.setExchCode(contractInfoEntity.getExchCode());
            contractInfoBasic.setExtraParas(contractInfoEntity.getExtraParams()); //todo
            if (synContrct) {
                // 刷新新缓存
                newContractInfoCache.put(contractInfoBasic.getSymbol(), contractInfoBasic);
            }else{
                contractInfoCache.put(contractInfoBasic.getSymbol(), contractInfoBasic);
            }
        }
    }


    /**
     * 缓存境外合约信息
     */
    public void cacheContractOutInfo(boolean synContrct) {
        // 初始化 查询数据库
        List<ContractOutInfoEntity> contractOutInfo = contractOutInfoMapper.getContractOutInfo();
        for (ContractOutInfoEntity contractOutInfoEntity : contractOutInfo) {
            ContractInfoBasic contractInfoBasic = new ContractInfoBasic();
            contractInfoBasic.setSymbol(contractOutInfoEntity.getContractID());
            contractInfoBasic.setVarietyId(contractOutInfoEntity.getVarietyID());
            contractInfoBasic.setUnit(contractOutInfoEntity.getUnit());
            contractInfoBasic.setMeasureUnit(contractOutInfoEntity.getMeasureUnit());
            contractInfoBasic.setTick(contractOutInfoEntity.getTick());
            contractInfoBasic.setExchCode(null);
            contractInfoBasic.setDomesticType(contractOutInfoEntity.getDomesticType());
            contractInfoBasic.setInventoryType(contractOutInfoEntity.getContractType());
            contractInfoBasic.setEndDeliveryDate(null);
            contractInfoBasic.setHistoryContract(Boolean.FALSE);
            contractInfoBasic.setCurrency(contractOutInfoEntity.getCurrency());
            contractInfoBasic.setAccuracy(contractOutInfoEntity.getAccuracy());
            contractInfoBasic.setStepPosition(contractOutInfoEntity.getStepPosition());
            contractInfoBasic.setStraightDiscForkType(contractOutInfoEntity.getProperty1());
            contractInfoBasic.setStraightDisc(contractOutInfoEntity.getProperty2());
            contractInfoBasic.setForkPlate(contractOutInfoEntity.getProperty3());
            contractInfoBasic.setExtraParas(contractOutInfoEntity.getExtraParas());
            if (synContrct) {
                // 刷新新缓存
                newContractInfoCache.put(contractInfoBasic.getSymbol(), contractInfoBasic);
            }else{
                contractInfoCache.put(contractInfoBasic.getSymbol(), contractInfoBasic);
            }
        }
    }

    // 提供接口给管理台
    public void lisenUpadateContactInfo() {
        // 新缓存置空
        newContractInfoCache.clear();
        // 监听合约信息更新请求，合约信息全量更新
        cacheContractInfo(true); // 境内合约信息
        cacheContractOutInfo(true); // 境外合约信息
        // 原子替换
        this.contractInfoCache = newContractInfoCache;
    }
}

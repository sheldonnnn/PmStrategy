package com.cmbc.oms.controller;

import com.cmbc.oms.controller.dto.QueryMgapPosReq;
import com.cmbc.oms.domain.exposure.dto.MgapPositionResponse;
import com.cmbc.oms.domain.exposure.dto.PositionDataResponse;
import com.cmbc.oms.domain.exposure.dto.QueryMgapPosResponse;
import com.cmbc.oms.domain.exposure.service.MgapClientPositionService;
import com.cmbc.oms.domain.exposure.vo.SymbolPositionVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/gold/accumulated")
public class MgapController {

    @Autowired
    private MgapClientPositionService mgapClientPositionService;

    @PostMapping("/querySummary")
    public QueryMgapPosResponse summary(@RequestBody QueryMgapPosReq request) {
//        log.debug("开始返回积存金头寸汇总信息与净敞口损益信息");
        //调用外部接口
        try {
            PositionDataResponse positionDataResponse = mgapClientPositionService.getPositionSummary(request.getFxSymbol());
            return new QueryMgapPosResponse(positionDataResponse, positionDataResponse.isConnected());
        } catch (Exception e) {
            log.error("积存金头寸前端查询失败", e);
            return new QueryMgapPosResponse(null, false);
        }
    }

    /**
     * 查询量化平盘头寸信息
     */
    @PostMapping("/querySymbolPositions")
    public MgapPositionResponse querySymbolPositions() {
        List<SymbolPositionVo> hedgedSymbolPosition = null;
        try {
            hedgedSymbolPosition = mgapClientPositionService.getHedgePositionSummaryByMarket();
        } catch (Exception e) {
            log.error("查询量化平盘头寸信息失败:", e);
            return new MgapPositionResponse(e.getMessage());
        }
        return new MgapPositionResponse(hedgedSymbolPosition);
    }
}

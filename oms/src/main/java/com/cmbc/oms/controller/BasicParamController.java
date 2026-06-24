package com.cmbc.oms.controller;

import com.cmbc.oms.controller.dto.BasicParamResponse;
import com.cmbc.oms.controller.dto.DimpleUserReq;
import com.cmbc.oms.domain.position.ability.DimpleUserManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础参数更新监控控制器
 */
@RestController
@RequestMapping("/gold/basicParam")
public class BasicParamController {

    @Autowired
    private DimpleUserManageService dimpleUserManageService;

    /**
     * 更新dimple用户信息
     *
     * @return
     */
    @PostMapping(value = "updateDimpleLogin")
    public BasicParamResponse updateDimpleUser(@RequestBody DimpleUserReq request) {
        BasicParamResponse apiResponse = new BasicParamResponse();
        try {
            dimpleUserManageService.lisenUpadateDimpleUserImfo(request);
            apiResponse.setCode("0");
            apiResponse.setMessage("更新成功!");
        } catch (Exception e) {
            apiResponse.setCode("1");
            apiResponse.setMessage("更新失败!");
        }
        return apiResponse;
    }

    /**
     * 合约信息更新
     *
     * @return
     */
    @PostMapping(value = "updateDimpleLogon")
    public BasicParamResponse updateDimpleLogon(@RequestBody DimpleUserReq request) {
        BasicParamResponse apiResponse = new BasicParamResponse();
        try {
            dimpleUserManageService.lisenUpadateCacheLogout(request);
            apiResponse.setCode("0");
            apiResponse.setMessage("更新成功!");
        } catch (Exception e) {
            apiResponse.setCode("1");
            apiResponse.setMessage("更新失败!");
        }
        return apiResponse;
    }
}

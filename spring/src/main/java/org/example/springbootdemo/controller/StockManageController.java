package org.example.springbootdemo.controller;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.example.springbootdemo.dto.ApiResult;
import org.example.springbootdemo.dto.ReplenishDTO;
import org.example.springbootdemo.service.ReplenishService;
import org.example.springbootdemo.vo.ReplenishVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/stock")
public class StockManageController {

    @Resource
    private ReplenishService replenishService;

    @PostMapping("/replenish")
    public ApiResult<ReplenishVO> replenish(@Valid @RequestBody ReplenishDTO dto) {
        return replenishService.replenish(dto);
    }
}
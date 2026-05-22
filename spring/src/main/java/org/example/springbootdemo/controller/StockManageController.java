package org.example.springbootdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * 库存管理控制器
 * 提供库存查询、添加、编辑和补货功能
 */
@Tag(name = "库存管理", description = "库存查询、添加、编辑和补货操作")
@RestController
@RequestMapping("/admin/stock")
public class StockManageController {

    @Resource
    private ReplenishService replenishService;

    /**
     * 商品补货（增加库存）
     * @param dto 补货信息
     * @return 补货结果
     */
    @Operation(summary = "商品补货", description = "批量增加商品库存，同步更新 Redis 和 MySQL")
    @PostMapping("/replenish")
    public ApiResult<ReplenishVO> replenish(@Valid @RequestBody ReplenishDTO dto) {
        return replenishService.replenish(dto);
    }
    

}
package org.example.springbootdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.example.springbootdemo.util.ApiResult;
import org.example.springbootdemo.dto.OrderDTO;
import org.example.springbootdemo.service.OrderProcessingService;
import org.example.springbootdemo.vo.OrderVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单管理控制器
 * 提供订单创建和取消功能
 */
@Tag(name = "订单管理", description = "订单创建、取消等操作")
@RestController
@RequestMapping("/orders")
public class OrderController {
    @Resource
    private OrderProcessingService orderProcessingService;

    /**
     * 创建订单（批量扣减库存）
     * @param orderDTO 订单信息，包含订单号、平台ID和商品列表
     * @return 订单处理结果
     */
    @Operation(summary = "创建订单", description = "提交订单并原子性扣减Redis库存，订单明细异步持久化到数据库")
    @PostMapping("/add")
    public ApiResult<OrderVO> createOrder(@Valid @RequestBody OrderDTO orderDTO){
        return orderProcessingService.processOrder(orderDTO);
    }

    /**
     * 取消订单（支持部分失败）
     * @param orderDTO 订单信息，包含订单号、平台ID和商品列表
     * @return 取消结果详情
     */
    @Operation(summary = "取消订单", description = "回滚Redis库存并更新数据库状态，允许部分商品取消失败")
    @PostMapping("/cancel")
    public ApiResult<String> cancelOrder(@Valid @RequestBody OrderDTO orderDTO){
        return orderProcessingService.cancelOrder(orderDTO);
    }
}

package org.example.springbootdemo.controller;

import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springbootdemo.util.ApiResult;
import org.example.springbootdemo.entity.OrderDetail;
import org.example.springbootdemo.entity.ProductStock;
import org.example.springbootdemo.query.OrderQuery;
import org.example.springbootdemo.query.ProductStockQuery;
import org.example.springbootdemo.service.OrderDetailService;
import org.example.springbootdemo.service.ProductStockService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板控制器 —— 订单/库存分页查询
 * <p>路径映射变更：</p>
 * <ul>
 *   <li>/dashboard/getOrders → /dashboard/orders</li>
 *   <li>/dashboard/getStocks → /dashboard/stocks</li>
 *   <li>/dashboard/getUsers  → 已迁移至 StaffController /staff/list</li>
 *   <li>/dashboard/updateUsers → 已迁移至 StaffController /staff/update</li>
 * </ul>
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "数据看板", description = "订单、库存分页查询")
public class DashboardController {

    @Resource
    private OrderDetailService orderDetailService;

    @Resource
    private ProductStockService productStockService;

    @Operation(summary = "订单列表", description = "分页条件查询订单明细")
    @PostMapping("/orders")
    public ApiResult<PageInfo<OrderDetail>> getOrders(@RequestBody OrderQuery orderQuery) {
        PageInfo<OrderDetail> pageInfo = orderDetailService.find(orderQuery);
        return ApiResult.success(pageInfo);
    }

    @Operation(summary = "库存列表", description = "分页条件查询商品库存")
    @PostMapping("/stocks")
    public ApiResult<PageInfo<ProductStock>> getStocks(@RequestBody ProductStockQuery productStockQuery) {
        PageInfo<ProductStock> pageInfo = productStockService.find(productStockQuery);
        return ApiResult.success(pageInfo);
    }
}

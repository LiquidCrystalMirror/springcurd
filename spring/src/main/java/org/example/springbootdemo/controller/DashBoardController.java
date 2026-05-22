package org.example.springbootdemo.controller;

import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.example.springbootdemo.dto.ApiResult;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.entity.OrderDetail;
import org.example.springbootdemo.entity.ProductStock;
import org.example.springbootdemo.query.OrderQuery;
import org.example.springbootdemo.query.ProductStockQuery;
import org.example.springbootdemo.query.UserQuery;
import org.example.springbootdemo.service.OrderDetailService;
import org.example.springbootdemo.service.ProductStockService;
import org.example.springbootdemo.service.UserService;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashBoardController {
    @Resource
    private UserService userService;
    
    @Resource
    private OrderDetailService orderDetailService;
    
    @Resource
    private ProductStockService productStockService;
    @PostMapping("/getUsers")
    public ApiResult<PageInfo<UserVO>> getUsers(@RequestBody UserQuery userQuery){
        PageInfo<UserVO> pageInfo = userService.find(userQuery);
        return ApiResult.success(pageInfo);
    }
    @PostMapping("/updateUsers")
    public ApiResult<Void> updateUsers(@RequestBody UserDTO userDTO){
        int result = userService.updateUser(userDTO);
        if (result>0) {
            return ApiResult.success();
        }else {
            return ApiResult.error(500,"更新失败");
        }
    }
    
    /**
     * 分页查询订单明细
     * @param orderQuery 查询条件，包含订单号、平台ID、商品ID等
     * @return 订单明细分页结果
     */
    @PostMapping("/getOrders")
    public ApiResult<PageInfo<OrderDetail>> getOrders(@RequestBody OrderQuery orderQuery){
        PageInfo<OrderDetail> pageInfo = orderDetailService.find(orderQuery);
        return ApiResult.success(pageInfo);
    }
    
    /**
     * 分页查询商品库存
     * @param productStockQuery 查询条件，包含商品ID、库存数量等
     * @return 商品库存分页结果
     */
    @PostMapping("/getStocks")
    public ApiResult<PageInfo<ProductStock>> getStocks(@RequestBody ProductStockQuery productStockQuery){
        PageInfo<ProductStock> pageInfo = productStockService.find(productStockQuery);
        return ApiResult.success(pageInfo);
    }
}

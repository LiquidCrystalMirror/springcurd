package org.example.springbootdemo.service;

import org.example.springbootdemo.util.ApiResult;
import org.example.springbootdemo.dto.OrderDTO;
import org.example.springbootdemo.vo.OrderVO;

public interface OrderProcessingService {
    ApiResult<OrderVO> processOrder(OrderDTO orderDTO);

    ApiResult<String> cancelOrder(OrderDTO orderDTO);
}

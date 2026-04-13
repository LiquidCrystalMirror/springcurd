package org.example.springbootdemo.service;

import com.github.pagehelper.PageInfo;
import org.example.springbootdemo.entity.OrderDetail;
import org.example.springbootdemo.query.OrderQuery;

public interface OrderDetailService {
    OrderDetail queryById(Long id);

    PageInfo<OrderDetail> find(OrderQuery orderQuery);

    int insert(OrderDetail orderDetail);

    int update(OrderDetail orderDetail);

    int deleteById(Long id);
}

package org.example.springbootdemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.springbootdemo.entity.OrderDetail;
import org.example.springbootdemo.query.OrderQuery;

import java.util.List;

@Mapper
public interface OrderDetailMapper {

    OrderDetail queryById(@Param("id") Long id);

    List<OrderDetail> find(OrderQuery query);

    int insert(OrderDetail orderDetail);

    int update(OrderDetail orderDetail);

    int deleteById(@Param("id") Long id);
}
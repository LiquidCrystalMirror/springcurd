package org.example.springbootdemo.service.imp;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.example.springbootdemo.entity.OrderDetail;
import org.example.springbootdemo.mapper.OrderDetailMapper;
import org.example.springbootdemo.query.OrderQuery;
import org.example.springbootdemo.service.OrderDetailService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderDetailServiceImpl implements OrderDetailService {
    @Resource private OrderDetailMapper orderDetailMapper;
    @Override
    public OrderDetail queryById(Long id) {
        return orderDetailMapper.queryById(id);
    }
    @Override
    public PageInfo<OrderDetail> find(OrderQuery query) {
        PageHelper.startPage(query.getPage(), query.getPageSize());
        List<OrderDetail> list = orderDetailMapper.find(query);
        return new PageInfo<>(list);
    }
    @Override
    public int insert(OrderDetail orderDetail) {
        return orderDetailMapper.insert(orderDetail);
    }
    @Override
    public int update(OrderDetail orderDetail) {
        return orderDetailMapper.update(orderDetail);
    }
    @Override
    public int deleteById(Long id) {
        return orderDetailMapper.deleteById(id);
    }
    @Override
    public OrderDetail findByOrderAndProduct(String orderNo, String platformId, Long productId){
        return orderDetailMapper.findByOrderAndProduct(orderNo, platformId, productId);
    }
    @Override
    public int updateStatusToCancelByProduct(String orderNo, String platformId, Long productId){
        return orderDetailMapper.updateStatusToCancelByProduct(orderNo, platformId, productId);
    }
}
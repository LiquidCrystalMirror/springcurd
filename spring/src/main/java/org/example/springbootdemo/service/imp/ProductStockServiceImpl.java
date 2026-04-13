package org.example.springbootdemo.service.imp;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.example.springbootdemo.entity.ProductStock;
import org.example.springbootdemo.mapper.ProductStockMapper;
import org.example.springbootdemo.query.ProductStockQuery;
import org.example.springbootdemo.service.ProductStockService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductStockServiceImpl implements ProductStockService {
    @Resource private ProductStockMapper productStockMapper;

    @Override
    public ProductStock queryById(Long id) {
        return productStockMapper.queryById(id);
    }
    @Override
    public PageInfo<ProductStock> find(ProductStockQuery query) {
        PageHelper.startPage(query.getPage(), query.getPageSize());
        List<ProductStock> list = productStockMapper.find(query);
        return new PageInfo<>(list);
    }
    @Override
    public int insert(ProductStock productStock) {
        return productStockMapper.insert(productStock);
    }
    @Override
    public int update(ProductStock productStock) {
        return productStockMapper.update(productStock);
    }
    @Override
    public int deleteById(Long id) {
        return productStockMapper.deleteById(id);
    }
}
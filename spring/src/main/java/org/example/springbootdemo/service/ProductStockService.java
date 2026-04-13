package org.example.springbootdemo.service;

import com.github.pagehelper.PageInfo;
import org.example.springbootdemo.entity.ProductStock;
import org.example.springbootdemo.query.ProductStockQuery;

public interface ProductStockService {
    ProductStock queryById(Long id);

    PageInfo<ProductStock> find(ProductStockQuery productStockQuery);

    int insert(ProductStock productStock);

    int update(ProductStock productStock);

    int deleteById(Long id);
}

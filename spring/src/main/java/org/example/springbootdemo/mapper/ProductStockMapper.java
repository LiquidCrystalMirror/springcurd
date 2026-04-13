package org.example.springbootdemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.springbootdemo.entity.ProductStock;
import org.example.springbootdemo.query.ProductStockQuery;

import java.util.List;

@Mapper
public interface ProductStockMapper {

    ProductStock queryById(@Param("id") Long id);

    List<ProductStock> find(ProductStockQuery query);

    int insert(ProductStock productStock);

    int update(ProductStock productStock);

    int deleteById(@Param("id") Long id);

    int updateStockWithVersion(@Param("productId") Long productId,
                               @Param("newStock") int newStock,
                               @Param("oldVersion") int oldVersion);
}
package org.example.springbootdemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.springbootdemo.entity.Product;

/**
 * 商品信息 Mapper 接口
 */
@Mapper
public interface ProductMapper {
    
    /**
     * 根据商品ID查询商品信息
     * @param productId 商品ID
     * @return 商品信息
     */
    Product queryById(@Param("productId") Long productId);
    
    /**
     * 插入商品信息
     * @param product 商品信息
     * @return 影响行数
     */
    int insert(Product product);
    
    /**
     * 更新商品信息
     * @param product 商品信息
     * @return 影响行数
     */
    int update(Product product);
    
    /**
     * 删除商品信息
     * @param productId 商品ID
     * @return 影响行数
     */
    int deleteById(@Param("productId") Long productId);
}

package org.example.springbootdemo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品信息实体类
 * 与 product_stock 表通过 product_id 外键关联
 */
@Data
@TableName("product")
public class Product {
    /**
     * 商品ID（主键）
     */
    @TableId("product_id")
    private Long productId;
    
    /**
     * 商品名称
     */
    private String productName;
}

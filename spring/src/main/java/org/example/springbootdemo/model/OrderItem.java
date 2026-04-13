package org.example.springbootdemo.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 订单项模型类
 * 用于表示订单中的单个商品项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 商品数量
     */
    private Integer quantity;
}

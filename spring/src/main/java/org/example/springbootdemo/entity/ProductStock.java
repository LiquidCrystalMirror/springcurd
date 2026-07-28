package org.example.springbootdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("product_stock")
public class ProductStock {
    @TableId(value = "product_id", type = IdType.INPUT)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long productId;
    private Integer stock;
    @Version
    private Integer version;
    private LocalDateTime updateTime;
    
    /**
     * 商品名称（非数据库字段，通过关联查询获取）
     */
    private String productName;
}
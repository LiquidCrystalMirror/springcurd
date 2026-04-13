package org.example.springbootdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("product_stock")
public class ProductStock {
    @TableId(value = "product_id", type = IdType.INPUT)
    private Long productId;
    private Integer stock;
    @Version
    private Integer version;
    private LocalDateTime updateTime;
}
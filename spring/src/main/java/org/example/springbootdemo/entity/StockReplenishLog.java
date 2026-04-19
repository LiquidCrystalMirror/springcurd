package org.example.springbootdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("stock_replenish_log")
public class StockReplenishLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String replenishNo;
    private Long productId;
    private Integer quantity;
    private Integer stockBefore;
    private Integer stockAfter;
    private Integer status;
    private LocalDateTime createTime;
}
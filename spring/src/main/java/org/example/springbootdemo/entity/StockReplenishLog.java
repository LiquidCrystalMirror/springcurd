package org.example.springbootdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("stock_replenish_log")
public class StockReplenishLog {
    @TableId(type = IdType.ASSIGN_ID)  // 雪花算法生成批次ID
    private Long id;
    private Long productId;
    private Integer quantity;
    private Integer stockBefore;
    private Integer stockAfter;
    private Integer status;
    private LocalDateTime createTime;
}
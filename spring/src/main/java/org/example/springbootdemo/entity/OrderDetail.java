package org.example.springbootdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("order_detail")
public class OrderDetail {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderNo;
    private String platformId;
    private Long productId;
    private Integer quantity;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
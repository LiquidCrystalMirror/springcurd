package org.example.springbootdemo.dto;


import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OrderDTO {
    private Long id;
    private String orderNo;
    private String platformId;
    private Long productId;
    private Integer quantity;
    private Byte status;
    private LocalDateTime createTime;
}

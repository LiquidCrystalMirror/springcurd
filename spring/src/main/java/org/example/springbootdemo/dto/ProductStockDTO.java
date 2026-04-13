package org.example.springbootdemo.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProductStockDTO {
    private Long productId;
    private Integer stock;
    private LocalDateTime updateTime;
}

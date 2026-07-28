package org.example.springbootdemo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class OrderItemDTO {
    @NotNull(message = "商品ID不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long productId;

    @Min(value = 1, message = "商品数量必须大于0")
    private Integer quantity;
}
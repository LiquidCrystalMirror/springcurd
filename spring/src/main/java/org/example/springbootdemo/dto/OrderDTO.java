package org.example.springbootdemo.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

@Data
public class OrderDTO {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotBlank(message = "平台标识不能为空")
    private String platformId;

    @NotEmpty(message = "订单商品列表不能为空")
    @Valid
    private List<OrderItemDTO> items;
}


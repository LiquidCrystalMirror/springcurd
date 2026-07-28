// dto/ReplenishItemDTO.java
package org.example.springbootdemo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReplenishItemDTO {
    @NotNull(message = "商品ID不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long productId;
    
    /**
     * 商品名称（用于校验，可选）
     */
    private String productName;
    
    @Min(value = 1, message = "补货数量必须大于0")
    private Integer quantity;
}
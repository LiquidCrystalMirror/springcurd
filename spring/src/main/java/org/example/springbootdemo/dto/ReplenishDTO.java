// dto/ReplenishDTO.java
package org.example.springbootdemo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class ReplenishDTO {
    @NotBlank(message = "补货单号不能为空")
    private String replenishNo;
    @NotEmpty(message = "补货商品列表不能为空")
    @Valid
    private List<ReplenishItemDTO> items;
}
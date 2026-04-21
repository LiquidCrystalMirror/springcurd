// dto/ReplenishDTO.java
package org.example.springbootdemo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class ReplenishDTO {
    // 移除 replenishNo，由后端自动生成雪花ID
    @NotEmpty(message = "补货商品列表不能为空")
    @Valid
    private List<ReplenishItemDTO> items;
}
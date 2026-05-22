package org.example.springbootdemo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 添加/编辑库存 DTO
 */
@Data
public class StockDTO {
    
    /**
     * 商品ID
     */
    @NotNull(message = "商品ID不能为空")
    private Long productId;
    
    /**
     * 商品名称（新增时必填，编辑时可选）
     */
    @NotBlank(message = "商品名称不能为空", groups = AddGroup.class)
    private String productName;
    
    /**
     * 库存数量
     */
    @NotNull(message = "库存数量不能为空")
    @Min(value = 0, message = "库存数量不能小于0")
    private Integer stock;
    
    /**
     * 校验组：新增
     */
    public interface AddGroup {}
    
    /**
     * 校验组：编辑
     */
    public interface UpdateGroup {}
}

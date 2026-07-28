package org.example.springbootdemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户审核 DTO（监管/系统管理员审核新注册用户）
 */
@Schema(description = "用户审核请求")
@Data
public class UserApproveDTO {

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "待审核的用户ID", example = "10001")
    private Integer userId;

    @NotNull(message = "审核结果不能为空")
    @Schema(description = "是否通过审核", example = "true")
    private Boolean approved;

    @Schema(description = "审核备注/拒绝原因", example = "信息不完整，请补充")
    private String remark;
}

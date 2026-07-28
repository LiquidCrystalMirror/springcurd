package org.example.springbootdemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内部员工 DTO（补货员/人事/监管/系统管理员）
 * <p>映射 staff 表，含审计字段</p>
 */
@Schema(description = "内部员工")
@Data
public class StaffDTO {
    @Schema(description = "员工ID", example = "10002")
    private Integer id;

    @Schema(description = "姓名", example = "李四")
    private String name;

    @Schema(description = "密码(加密)")
    private String password;

    @Schema(description = "状态：0=禁用, 1=启用, 2=待审核, 3=审核拒绝", example = "2")
    private Byte status;

    @Schema(description = "角色：2=补货员, 3=人事, 4=监管, 5=管理员", example = "2")
    private Byte roleId;

    @Schema(description = "创建人ID（人事）")
    private Integer creatorId;

    @Schema(description = "审核人ID（监管/管理员）")
    private Integer approverId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "审核时间")
    private LocalDateTime approveTime;
}

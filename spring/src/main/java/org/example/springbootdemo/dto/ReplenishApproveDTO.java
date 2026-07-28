package org.example.springbootdemo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 补货单审核请求 DTO
 */
@Data
public class ReplenishApproveDTO {

    /** 补货单ID（接受字符串，避免 JS 精度丢失） */
    @NotNull(message = "补货单ID不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /** 是否通过：true=审核通过, false=审核拒绝 */
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    /** 审核备注/拒绝原因（拒绝时必填） */
    @Size(max = 512, message = "备注不能超过512字")
    private String remark;
}

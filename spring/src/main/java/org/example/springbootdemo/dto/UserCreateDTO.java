package org.example.springbootdemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 人事创建用户 DTO
 * <p>人事创建用户时，后端自动设置 status=2(待审核) 和 creator_id=当前人事ID</p>
 */
@Schema(description = "人事创建用户请求")
@Data
public class UserCreateDTO {

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", example = "10001")
    private Integer id;

    @Schema(description = "用户昵称", example = "张三")
    private String name;

    @Schema(description = "用户密码", example = "123456")
    private String password;

    @NotNull(message = "角色ID不能为空")
    @Schema(description = "角色ID：1=购买用户, 2=补货员, 3=人事, 4=监管, 5=系统管理员", example = "1")
    private Byte roleId;
}

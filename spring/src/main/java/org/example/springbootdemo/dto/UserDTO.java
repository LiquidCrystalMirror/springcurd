package org.example.springbootdemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Schema(description = "用户数据传输对象")
@Data
public class UserDTO {
    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", example = "10001")
    private Integer id;
    @Schema(description = "用户昵称", example = "张三")
    private String name;
    @Schema(description = "用户状态", example = "1")
    private Byte status;
    @Schema(description = "用户角色ID", example = "1")
    private Byte roleId;
    @Schema(description = "用户密码", example = "123456")
    private String password;
}

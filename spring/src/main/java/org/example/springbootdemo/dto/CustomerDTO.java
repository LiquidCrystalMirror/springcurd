package org.example.springbootdemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 购买用户 DTO（C端消费者，自注册，无需审核）
 * <p>映射 customer 表</p>
 */
@Schema(description = "购买用户")
@Data
public class CustomerDTO {
    @Schema(description = "用户ID", example = "10001")
    private Integer id;

    @Schema(description = "昵称", example = "张三")
    private String name;

    @Schema(description = "密码(加密)", example = "e10adc3949ba59abbe56e057f20f883e")
    private String password;

    @Schema(description = "状态：0=禁用, 1=启用", example = "1")
    private Byte status;

    @Schema(description = "角色ID，固定为1", example = "1")
    private Byte roleId;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "收货地址")
    private String address;
}

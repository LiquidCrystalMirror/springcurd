package org.example.springbootdemo.vo;

import lombok.Data;

@Data
public class UserVO {

    private Integer id;
    private String name;
    private Byte status;
    private Byte roleId;
}

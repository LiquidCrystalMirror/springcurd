package org.example.springbootdemo.vo;

import lombok.Data;

@Data
public class UserVO {

    private Integer id;
    private String name;
    private Integer money;
    private Byte status;
    private Byte role_id;
}

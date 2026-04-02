package org.example.springbootdemo.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Integer id;
    private String name;
    private Integer money=0;
    private Byte status;
    private Byte role_id;
    private String password;
}

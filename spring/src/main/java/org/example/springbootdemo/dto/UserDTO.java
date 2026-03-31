package org.example.springbootdemo.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Integer id;
    private String name;
    private Integer money=0;
    private Integer status;
    private Integer role_id;
    private String password;
}

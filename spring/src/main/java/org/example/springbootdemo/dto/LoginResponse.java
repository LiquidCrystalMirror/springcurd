package org.example.springbootdemo.dto;

import lombok.Data;
import org.example.springbootdemo.vo.UserVO;

@Data
public class LoginResponse {
    private UserVO user;      // 用户信息
    private String token;     // JWT token

    public LoginResponse() {}

    public LoginResponse(UserVO user, String token) {
        this.user = user;
        this.token = token;
    }
}

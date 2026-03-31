package org.example.springbootdemo.controller;

import org.example.springbootdemo.dto.ApiResult;
import org.example.springbootdemo.dto.LoginResponse;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.service.UserService;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.example.springbootdemo.util.JwtUtil;
@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserService userService;
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@RequestBody UserDTO userDTO){

        if(userService.checkPassword(userDTO.getId(),userDTO.getPassword())){
            UserVO userVO = userService.getUserById(userDTO.getId());
            String token = JwtUtil.generateToken(userVO);
            LoginResponse loginResponse = new LoginResponse(userVO,token);
            return ApiResult.success(loginResponse);
        }else{
            return ApiResult.error(401,"密码错误");
        }
    }
    @PostMapping("/register")
    public ApiResult<UserVO> register(@RequestBody UserDTO userDTO){
        int result = userService.addUser(userDTO);
        if (result>0) {
            return ApiResult.success();
        }else {
            return ApiResult.error(500,"账号已存在");
        }
    }

}

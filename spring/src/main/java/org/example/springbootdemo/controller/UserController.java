package org.example.springbootdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springbootdemo.dto.ApiResult;
import org.example.springbootdemo.dto.LoginResponse;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.service.UserService;
import org.example.springbootdemo.util.PasswordUtil;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.web.bind.annotation.*;
import org.example.springbootdemo.util.JwtUtil;

@RestController
@RequestMapping("/users")
@Tag(name = "用户管理", description = "用户登录、注册等相关接口")
public class UserController {
    @Resource
    private UserService userService;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private PasswordUtil passwordUtil;
    @Operation(
            summary = "用户登录",
            description = "通过用户ID和密码进行登录认证，成功返回用户信息及JWT Token"
    )
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@RequestBody UserDTO userDTO){

        if(userService.checkPassword(userDTO.getId(),userDTO.getPassword())){
            UserVO userVO = userService.getUserById(userDTO.getId());
            String token = jwtUtil.generateToken(userVO);
            LoginResponse loginResponse = new LoginResponse(userVO,token);
            return ApiResult.success(loginResponse);
        }else{
            return ApiResult.error(401,"密码错误");
        }
    }
    @Operation(
            summary = "用户注册",
            description = "注册新用户账号，ID不能与已有用户重复"
    )
    @PostMapping("/register")
    public ApiResult<UserVO> register(@RequestBody UserDTO userDTO){
        userDTO.setPassword(passwordUtil.md5WithSalt(userDTO.getPassword()));
        int result = userService.addUser(userDTO);
        if (result>0) {
            return ApiResult.success();
        }else {
            return ApiResult.error(500,"账号已存在");
        }
    }

}

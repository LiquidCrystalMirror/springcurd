package org.example.springbootdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springbootdemo.dto.CustomerDTO;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.util.ApiResult;
import org.example.springbootdemo.entity.LoginResponse;
import org.example.springbootdemo.service.CustomerService;
import org.example.springbootdemo.service.StaffService;
import org.example.springbootdemo.util.JwtUtil;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器 —— 登录、注册
 * <p>登录：先查 staff（内部员工），未命中再查 customer（购买用户）</p>
 * <p>注册：仅支持购买用户自注册 → customer 表</p>
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "认证管理", description = "用户登录、注册接口")
public class AuthController {

    @Resource
    private StaffService staffService;
    @Resource
    private CustomerService customerService;
    @Resource
    private JwtUtil jwtUtil;

    @Operation(summary = "用户登录", description = "通过用户ID和密码登录，自动识别员工/购买用户")
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@RequestBody UserDTO userDTO) {
        int id = userDTO.getId();
        String password = userDTO.getPassword();

        // 先查 staff（内部员工），命中则用 staff 信息
        if (staffService.checkPassword(id, password)) {
            UserVO userVO = staffService.getById(id);
            String token = jwtUtil.generateToken(userVO);
            return ApiResult.success(new LoginResponse(userVO, token));
        }

        // 再查 customer（购买用户）
        if (customerService.checkPassword(id, password)) {
            UserVO userVO = customerService.getById(id);
            String token = jwtUtil.generateToken(userVO);
            return ApiResult.success(new LoginResponse(userVO, token));
        }

        return ApiResult.error(401, "账号或密码错误");
    }

    @Operation(summary = "用户注册", description = "购买用户自行注册，直接写入 customer 表")
    @PostMapping("/register")
    public ApiResult<Void> register(@RequestBody CustomerDTO dto) {
        int result = customerService.register(dto);
        return result > 0 ? ApiResult.success() : ApiResult.error(500, "注册失败，账号可能已存在");
    }
}

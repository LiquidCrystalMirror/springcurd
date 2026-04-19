package org.example.springbootdemo.controller;

import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.example.springbootdemo.dto.ApiResult;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.query.UserQuery;
import org.example.springbootdemo.service.UserService;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashBoardController {
    @Resource
    private UserService userService;
    @PostMapping("/getUsers")
    public ApiResult<PageInfo<UserVO>> getUsers(@RequestBody UserQuery userQuery){
        PageInfo<UserVO> pageInfo = userService.find(userQuery);
        return ApiResult.success(pageInfo);
    }
    @PostMapping("/updateUsers")
    public ApiResult<Void> updateUsers(@RequestBody UserDTO userDTO){
        int result = userService.updateUser(userDTO);
        if (result>0) {
            return ApiResult.success();
        }else {
            return ApiResult.error(500,"更新失败");
        }
    }
}

package org.example.springbootdemo.interceptor;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.exception.ForbiddenException;
import org.example.springbootdemo.exception.UnauthorizedException;
import org.example.springbootdemo.mapper.CustomerMapper;
import org.example.springbootdemo.mapper.StaffMapper;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户状态拦截器
 * <p>在 JWT 认证通过后、角色鉴权前执行，拦截所有未启用/未审核通过的用户请求</p>
 * <ul>
 *   <li>购买用户(customer)：status 必须 = 1(启用)，0=禁用则拒绝</li>
 *   <li>内部员工(staff)：status 必须 = 1(审核通过)，0/2/3 均拒绝</li>
 * </ul>
 */
@Component
public class StatusInterceptor implements HandlerInterceptor {

    @Resource
    private StaffMapper staffMapper;
    @Resource
    private CustomerMapper customerMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        UserDTO currentUser = (UserDTO) request.getAttribute("currentUser");
        if (currentUser == null) {
            return true; // JWT 未设置用户，由 JwtAuthInterceptor 或 RoleInterceptor 处理
        }

        Byte roleId = currentUser.getRoleId();
        Byte status;

        if (roleId != null && roleId == 1) {
            // 购买用户 → 查 customer 表
            UserVO vo = customerMapper.getById(currentUser.getId());
            status = (vo != null) ? vo.getStatus() : null;
        } else {
            // 内部员工 → 查 staff 表
            UserVO vo = staffMapper.getById(currentUser.getId());
            status = (vo != null) ? vo.getStatus() : null;
        }

        if (status == null || status != 1) {
            if (status != null && status == 2) {
                throw new ForbiddenException("账号尚未通过审核，请联系管理员");
            }
            throw new ForbiddenException("账号已被禁用或审核未通过");
        }

        return true;
    }
}

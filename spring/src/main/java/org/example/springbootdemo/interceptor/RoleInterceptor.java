package org.example.springbootdemo.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.springbootdemo.constant.enums.Role;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.exception.ForbiddenException;
import org.example.springbootdemo.exception.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 角色权限拦截器
 * <p>基于新 5 角色体系：</p>
 * <ul>
 *   <li>1=购买用户(BUYER)           → 仅访问购买端</li>
 *   <li>2=补货员(REPLENISHER)       → 创建补货单</li>
 *   <li>3=人事(HR)                 → 管理用户</li>
 *   <li>4=监管(SUPERVISOR)         → 审核补货单/用户</li>
 *   <li>5=系统管理员(SYSTEM_ADMIN)  → 全部权限</li>
 * </ul>
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();

        // 从请求属性中获取当前用户信息（由 JwtAuthInterceptor 设置）
        UserDTO currentUser = (UserDTO) request.getAttribute("currentUser");

        if (currentUser == null) {
            throw new UnauthorizedException("未登录或登录已过期");
        }

        Byte roleId = currentUser.getRoleId();
        Role role = Role.getByCode(roleId);

        // 系统管理员拥有全部权限，直接放行
        if (role == Role.SYSTEM_ADMIN) {
            return true;
        }

        // 员工管理操作（修改/删除）→ 人事
        if (uri.contains("/staff/update") || ("DELETE".equals(request.getMethod()) && uri.contains("/staff/"))) {
            if (!role.canManageUsers()) {
                throw new ForbiddenException("权限不足：仅人事和系统管理员可以管理员工");
            }
        }

        // 人事创建员工 → 人事
        if (uri.contains("/staff/create")) {
            if (!role.canManageUsers()) {
                throw new ForbiddenException("权限不足：仅人事和系统管理员可以创建员工");
            }
        }

        // 审核操作（员工审核 / 补货审核）→ 监管
        if (uri.contains("/approve")) {
            if (!role.canApprove()) {
                throw new ForbiddenException("权限不足：仅监管和系统管理员可以执行审核");
            }
        }

        // 补货提交 → 补货员
        if (uri.contains("/replenish/submit")) {
            if (!role.canCreateReplenish()) {
                throw new ForbiddenException("权限不足：仅补货员和系统管理员可以提交补货申请");
            }
        }

        return true;
    }
}

package org.example.springbootdemo.controller;

import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.springbootdemo.dto.*;
import org.example.springbootdemo.util.ApiResult;
import org.example.springbootdemo.query.UserQuery;
import org.example.springbootdemo.service.StaffService;
import org.example.springbootdemo.vo.UserDetailVO;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.web.bind.annotation.*;

/**
 * 员工管理控制器 —— 内部员工CRUD + 人事创建 + 监管审核
 * <p>仅管理 staff 表（role_id=2/3/4/5），购买用户不在此管理范围</p>
 * <p>路径映射变更：</p>
 * <ul>
 *   <li>/users/list       → /staff/list</li>
 *   <li>/users/{id}       → /staff/{id}</li>
 *   <li>/users/create     → /staff/create</li>
 *   <li>/users/{id}/approve → /staff/{id}/approve</li>
 *   <li>/users/update     → /staff/update</li>
 *   <li>/users/{id} DELETE → /staff/{id} DELETE</li>
 * </ul>
 */
@RestController
@RequestMapping("/staff")
@Tag(name = "员工管理", description = "内部员工CRUD、人事创建、监管审核")
public class StaffController {

    @Resource
    private StaffService staffService;

    @Operation(summary = "员工列表", description = "分页条件查询内部员工")
    @PostMapping("/list")
    public ApiResult<PageInfo<UserVO>> list(@RequestBody UserQuery query) {
        return ApiResult.success(staffService.find(query));
    }

    @Operation(summary = "员工详情", description = "获取员工完整信息（含创建人/审核人/时间戳）")
    @GetMapping("/{id}")
    public ApiResult<UserDetailVO> detail(@PathVariable int id) {
        UserDetailVO vo = staffService.getDetailById(id);
        return vo != null ? ApiResult.success(vo) : ApiResult.error(404, "员工不存在");
    }

    @Operation(summary = "人事创建员工", description = "人事创建内部员工账号，初始状态为待审核(2)，记录创建人")
    @PostMapping("/create")
    public ApiResult<Void> create(@Valid @RequestBody StaffDTO dto, HttpServletRequest request) {
        Integer creatorId = (Integer) request.getAttribute("userId");
        if (creatorId == null) return ApiResult.error(401, "未登录");
        int result = staffService.create(dto, creatorId);
        return result > 0 ? ApiResult.success() : ApiResult.error(500, "创建失败，账号可能已存在");
    }

    @Operation(summary = "监管审核员工", description = "监管/系统管理员审核员工：通过(status=1)或拒绝(status=3)")
    @PostMapping("/{id}/approve")
    public ApiResult<Void> approve(@PathVariable int id,
                                   @Valid @RequestBody UserApproveDTO dto,
                                   HttpServletRequest request) {
        Integer approverId = (Integer) request.getAttribute("userId");
        if (approverId == null) return ApiResult.error(401, "未登录");
        int result = staffService.approve(id, dto.getApproved(), approverId);
        return result > 0 ? ApiResult.success() : ApiResult.error(500, "审核失败，员工可能已被审核或不存在");
    }

    @Operation(summary = "更新员工", description = "更新员工基本信息")
    @PutMapping("/update")
    public ApiResult<Void> update(@RequestBody StaffDTO dto) {
        int result = staffService.update(dto);
        return result > 0 ? ApiResult.success() : ApiResult.error(500, "更新失败");
    }

    @Operation(summary = "删除员工", description = "删除指定员工")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable int id) {
        int result = staffService.delete(id);
        return result > 0 ? ApiResult.success() : ApiResult.error(500, "删除失败");
    }
}

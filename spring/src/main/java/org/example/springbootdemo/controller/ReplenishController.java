package org.example.springbootdemo.controller;

import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.springbootdemo.util.ApiResult;
import org.example.springbootdemo.dto.ReplenishApproveDTO;
import org.example.springbootdemo.dto.ReplenishDTO;
import org.example.springbootdemo.query.ReplenishQuery;
import org.example.springbootdemo.service.ReplenishService;
import org.example.springbootdemo.vo.ReplenishOrderVO;
import org.example.springbootdemo.vo.ReplenishVO;
import org.springframework.web.bind.annotation.*;

/**
 * 补货管理控制器
 * <p>原 StockManageController，路径 /admin/stock/replenish → /replenish/submit</p>
 */
@RestController
@RequestMapping("/replenish")
@Tag(name = "补货管理", description = "补货申请提交、列表查询、审核")
public class ReplenishController {

    @Resource
    private ReplenishService replenishService;

    @Operation(summary = "提交补货申请", description = "补货员提交补货申请单，批量增加商品库存")
    @PostMapping("/submit")
    public ApiResult<ReplenishVO> submit(@Valid @RequestBody ReplenishDTO dto) {
        return replenishService.replenish(dto);
    }

    @Operation(summary = "补货单列表", description = "分页查询补货单列表，联表显示商品种类数和总数量")
    @PostMapping("/list")
    public ApiResult<PageInfo<ReplenishOrderVO>> list(@RequestBody ReplenishQuery query) {
        return replenishService.findPage(query.getPage(), query.getPageSize(), query.getStatus());
    }

    @Operation(summary = "审核补货单", description = "监管/系统管理员审核补货单：通过或拒绝（拒绝需填写理由）")
    @PostMapping("/approve")
    public ApiResult<Void> approve(@Valid @RequestBody ReplenishApproveDTO dto,
                                    HttpServletRequest request) {
        Integer approverId = (Integer) request.getAttribute("userId");
        if (approverId == null) {
            return ApiResult.error(401, "未登录");
        }
        return replenishService.approve(dto, approverId);
    }
}

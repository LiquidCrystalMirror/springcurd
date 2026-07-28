package org.example.springbootdemo.service;

import com.github.pagehelper.PageInfo;
import org.example.springbootdemo.util.ApiResult;
import org.example.springbootdemo.dto.ReplenishApproveDTO;
import org.example.springbootdemo.dto.ReplenishDTO;
import org.example.springbootdemo.vo.ReplenishOrderVO;
import org.example.springbootdemo.vo.ReplenishVO;

public interface ReplenishService {

    /** 提交补货申请 */
    ApiResult<ReplenishVO> replenish(ReplenishDTO dto);

    /**
     * 分页查询补货单列表（联表 stock_replenish_log）
     * @param page     页码
     * @param pageSize 每页条数
     * @param status   状态筛选，传 null 表示全部
     */
    ApiResult<PageInfo<ReplenishOrderVO>> findPage(int page, int pageSize, Integer status);

    /**
     * 审核补货单（确认通过或拒绝）
     * @param dto        审核请求
     * @param approverId 审核人ID
     */
    ApiResult<Void> approve(ReplenishApproveDTO dto, Integer approverId);
}
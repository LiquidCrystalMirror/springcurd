package org.example.springbootdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.springbootdemo.entity.ReplenishOrder;
import org.example.springbootdemo.vo.ReplenishOrderVO;

import java.util.List;

/**
 * 补货单主表 Mapper
 */
@Mapper
public interface ReplenishOrderMapper extends BaseMapper<ReplenishOrder> {

    /**
     * 按状态查询补货单列表（用于审核列表）
     */
    List<ReplenishOrder> selectByStatus(@Param("status") Integer status);

    /**
     * 联表分页查询补货单列表（关联 stock_replenish_log 聚合明细 + staff 姓名）
     * @param status  状态筛选，传 null 表示全部
     * @param offset  分页偏移量
     * @param limit   每页条数
     */
    List<ReplenishOrderVO> selectPageWithDetail(@Param("status") Integer status,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    /**
     * 联表查询补货单总数（用于分页）
     */
    long countWithDetail(@Param("status") Integer status);

    /**
     * 根据ID联表查询补货单详情
     */
    ReplenishOrderVO selectByIdWithDetail(@Param("id") Long id);

    /**
     * 审核补货单（更新状态、审核人、审核时间、备注）
     */
    int approve(@Param("id") Long id,
                @Param("status") Integer status,
                @Param("approverId") Integer approverId,
                @Param("remark") String remark);

    /**
     * 更新补货单状态为已执行
     */
    int markExecuted(@Param("id") Long id);
}

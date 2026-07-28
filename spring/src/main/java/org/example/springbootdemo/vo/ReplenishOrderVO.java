package org.example.springbootdemo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 补货单联表查询结果 VO
 * 合并 stock_replenish_order（头表）与 stock_replenish_log（明细表）信息
 */
@Data
public class ReplenishOrderVO {

    // ========== 来自 stock_replenish_order ==========
    /** 补货单ID（雪花算法，序列化为字符串避免 JS 精度丢失） */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /** 创建人ID（补货员） */
    private Integer creatorId;

    /** 创建人姓名（联表 staff 查询） */
    private String creatorName;

    /** 审核人ID（监管/管理员） */
    private Integer approverId;

    /** 审核人姓名（联表 staff 查询） */
    private String approverName;

    /**
     * 状态：
     * 0 - 待审核
     * 1 - 审核通过（待执行）
     * 2 - 审核拒绝
     * 3 - 已执行
     */
    private Integer status;

    /** 审核备注/拒绝原因 */
    private String remark;

    /** 创建时间（提交申请时间） */
    private LocalDateTime createTime;

    /** 审核时间 */
    private LocalDateTime approveTime;

    // ========== 来自 stock_replenish_log（聚合） ==========
    /** 商品种类数 */
    private Integer itemCount;

    /** 补货总数量 */
    private Integer totalQuantity;
}

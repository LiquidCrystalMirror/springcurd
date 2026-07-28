package org.example.springbootdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 补货单主表 —— 存放审核流程头信息
 * 与 stock_replenish_log（明细表）通过 id 共享同一雪花批次ID
 */
@Data
@TableName("stock_replenish_order")
public class ReplenishOrder {

    /** 补货单ID（雪花算法，与 stock_replenish_log.id 共享同一值） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建人ID（补货员） */
    private Integer creatorId;

    /** 审核人ID（监管/系统管理员），审核时写入 */
    private Integer approverId;

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

    /** 创建时间（补货员提交申请时间） */
    private LocalDateTime createTime;

    /** 审核时间（监管确认/退回时间） */
    private LocalDateTime approveTime;

    /** 最后更新时间 */
    private LocalDateTime updateTime;
}

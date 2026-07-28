package org.example.springbootdemo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户详情 VO（列表双击查看时使用，含创建人/审核人等审计字段）
 */
@Data
public class UserDetailVO {

    private Integer id;
    private String name;
    private Byte status;
    private Byte roleId;

    /** 创建人ID（人事） */
    private Integer creatorId;

    /** 审核人ID（监管/管理员） */
    private Integer approverId;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 记录更新时间 */
    private LocalDateTime updatedAt;

    /** 审核时间 */
    private LocalDateTime approveTime;
}

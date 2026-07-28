package org.example.springbootdemo.constant.enums;

/**
 * 用户角色枚举
 * <p>角色 ID 从 1 开始，不使用 0（避免与 status=0 禁用混淆）</p>
 */
public enum Role {

    /** 购买用户 —— 仅可访问购买端，下单购买 */
    BUYER((byte) 1, "购买用户"),

    /** 补货员 —— 创建补货申请单，提交补货需求 */
    REPLENISHER((byte) 2, "补货员"),

    /** 人事 —— 创建/管理用户账号（创建的用户需监管审核） */
    HR((byte) 3, "人事"),

    /** 监管 —— 审核补货单、审核新用户注册 */
    SUPERVISOR((byte) 4, "监管"),

    /** 系统管理员 —— 集合所有权力的超级管理员 */
    SYSTEM_ADMIN((byte) 5, "系统管理员");

    private final byte code;
    private final String description;

    Role(byte code, String description) {
        this.code = code;
        this.description = description;
    }

    public byte getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取枚举
     */
    public static Role getByCode(byte code) {
        for (Role role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的角色ID：" + code);
    }

    /**
     * 判断是否为管理端角色（可访问 Dashboard）
     */
    public boolean isManagementRole() {
        return this != BUYER;
    }

    /**
     * 判断是否有用户管理权限（创建/修改/删除用户）
     */
    public boolean canManageUsers() {
        return this == HR || this == SYSTEM_ADMIN;
    }

    /**
     * 判断是否有审核权限（审核补货单、审核新用户）
     */
    public boolean canApprove() {
        return this == SUPERVISOR || this == SYSTEM_ADMIN;
    }

    /**
     * 判断是否有补货创建权限
     */
    public boolean canCreateReplenish() {
        return this == REPLENISHER || this == SYSTEM_ADMIN;
    }
}

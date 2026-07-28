/**
 * 用户状态枚举（v3：4 状态扩展）
 *
 * 0=禁用, 1=启用(审核通过), 2=待审核, 3=审核拒绝
 */
export const UserStatus = {
    DISABLED: 0,          // 禁用
    ENABLED: 1,           // 启用（审核通过）
    PENDING_APPROVAL: 2,  // 待审核（人事创建后的初始状态）
    APPROVAL_REJECTED: 3  // 审核拒绝
}

/**
 * 获取状态文本
 */
export function getStatusText(status) {
    switch (status) {
        case UserStatus.DISABLED:           return '禁用'
        case UserStatus.ENABLED:            return '启用'
        case UserStatus.PENDING_APPROVAL:   return '待审核'
        case UserStatus.APPROVAL_REJECTED:  return '审核拒绝'
        default:                            return '未知'
    }
}

/**
 * 获取状态标签类型（el-tag type）
 */
export function getStatusTagType(status) {
    switch (status) {
        case UserStatus.DISABLED:           return 'danger'
        case UserStatus.ENABLED:            return 'success'
        case UserStatus.PENDING_APPROVAL:   return 'warning'
        case UserStatus.APPROVAL_REJECTED:  return 'info'
        default:                            return ''
    }
}

/**
 * 获取状态选项列表
 */
export function getStatusOptions() {
    return [
        { label: '启用',   value: UserStatus.ENABLED },
        { label: '禁用',   value: UserStatus.DISABLED },
        { label: '待审核', value: UserStatus.PENDING_APPROVAL },
        { label: '审核拒绝', value: UserStatus.APPROVAL_REJECTED }
    ]
}

export default UserStatus

/**
 * 用户状态枚举
 */
export const UserStatus = {
    DISABLED: 0,  // 禁用
    ENABLED: 1   // 启用
}

/**
 * 获取状态文本
 * @param {number} status - 状态值
 * @returns {string} 状态文本
 */
export function getStatusText(status) {
    switch (status) {
        case UserStatus.DISABLED:
            return '禁用'
        case UserStatus.ENABLED:
            return '启用'
        default:
            return '未知'
    }
}

/**
 * 获取状态选项列表（用于下拉框）
 * @returns {Array<{label: string, value: number}>}
 */
export function getStatusOptions() {
    return [
        { label: '启用', value: UserStatus.ENABLED },
        { label: '禁用', value: UserStatus.DISABLED }
    ]
}

// 添加默认导出
export default UserStatus;

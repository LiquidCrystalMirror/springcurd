/**
 * 用户状态枚举
 */
export const Role = {
    User: 0,  // 禁用
    Administrator: 1   // 启用
}

/**
 * 获取状态文本
 * @param {number} status - 状态值
 * @returns {string} 状态文本
 */
export function getRoleText(status) {
    switch (status) {
        case Role.User:
            return '无权限'
        case Role.Administrator:
            return '管理员'
        default:
            return '未知'
    }
}

/**
 * 获取状态选项列表（用于下拉框）
 * @returns {Array<{label: string, value: number}>}
 */
export function getRoleOptions() {
    return [
        { label: '管理员', value: Role.Administrator },
        { label: '无权限', value: Role.User }
    ]
}

// 添加默认导出
export default Role;

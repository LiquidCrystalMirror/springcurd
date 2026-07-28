/**
 * 用户角色枚举（v3：5角色体系）
 *
 * 角色映射对照：
 *   旧 0=管理员   → 新 5=系统管理员
 *   旧 1=运营人员 → 新 2=补货员 / 4=监管（拆分）
 *   旧 2=购买用户 → 新 1=购买用户
 *   新增          → 新 3=人事（HR）
 */
export const Role = {
    BUYER: 1,           // 购买用户 —— 仅访问购买端，下单购买
    REPLENISHER: 2,     // 补货员   —— 创建补货申请单
    HR: 3,              // 人事     —— 创建/管理员工账号
    SUPERVISOR: 4,      // 监管     —— 审核补货单 + 审核员工
    SYSTEM_ADMIN: 5     // 系统管理员 —— 全部权限
}

/**
 * 管理端角色合集（可访问 Dashboard 的角色）
 */
export const MANAGEMENT_ROLES = [Role.REPLENISHER, Role.HR, Role.SUPERVISOR, Role.SYSTEM_ADMIN]

/**
 * 获取角色文本
 */
export function getRoleText(role) {
    switch (role) {
        case Role.BUYER:         return '购买用户'
        case Role.REPLENISHER:   return '补货员'
        case Role.HR:            return '人事'
        case Role.SUPERVISOR:    return '监管'
        case Role.SYSTEM_ADMIN:  return '系统管理员'
        default:                 return '未知'
    }
}

/**
 * 获取角色选项列表（用于下拉框）
 * @param {string} scope - 'all'=全部, 'staff'=仅员工角色(2~5), 'buyer'=仅购买用户
 */
export function getRoleOptions(scope = 'all') {
    const all = [
        { label: '购买用户',   value: Role.BUYER },
        { label: '补货员',     value: Role.REPLENISHER },
        { label: '人事',       value: Role.HR },
        { label: '监管',       value: Role.SUPERVISOR },
        { label: '系统管理员', value: Role.SYSTEM_ADMIN }
    ]
    if (scope === 'staff') return all.filter(r => r.value !== Role.BUYER)
    if (scope === 'buyer') return all.filter(r => r.value === Role.BUYER)
    return all
}

export default Role

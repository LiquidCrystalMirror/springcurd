import router from '@/router'
import { ElMessage } from 'element-plus'
import Role from '@/enum/user/Role.js'

/**
 * 解码 JWT Token（不验证签名，仅读取 payload）
 * @param {string} token - JWT token
 * @returns {object|null} 返回 decoded payload 或 null
 */
function decodeToken(token) {
  try {
    const base64Url = token.split('.')[1]
    if (!base64Url) return null

    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
        atob(base64).split('').map(c =>
            '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
        ).join('')
    )
    return JSON.parse(jsonPayload)
  } catch (e) {
    console.error('Token 解码失败:', e)
    return null
  }
}

/**
 * 获取 Token 的过期时间（从 token 的 exp 字段）
 * @returns {number|null} 返回过期时间戳（毫秒），如果 token 无效则返回 null
 */
export function getTokenExpireTime() {
  const token = getToken()
  if (!token) return null

  const decoded = decodeToken(token)
  if (!decoded) return null

  // JWT 标准中的 exp 是 Unix 时间戳（秒），转换为毫秒
  return decoded.exp ? decoded.exp * 1000 : null
}

/**
 * 检查 token 是否已过期
 * @param {number} [threshold=60000] - 提前判定过期的阈值（毫秒），默认 1 分钟
 * @returns {boolean} - 如果 token 有效（未过期）返回 true，否则返回 false
 */
export function isTokenValid(threshold = 60000) {
  const expireTime = getTokenExpireTime()
  if (!expireTime) {
    return false
  }
  const currentTime = Date.now()
  // 如果当前时间 + 阈值 >= 过期时间，则认为 token 已过期或即将过期
  return (currentTime + threshold) < expireTime
}

/**
 * 获取存储的用户信息
 * @returns {any|null}
 */
export function getUser() {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      return JSON.parse(userStr)
    } catch (e) {
      localStorage.removeItem('user')
      return null
    }
  }
  return null
}
/**
 * 获取 Token
 * @returns {string|null}
 */
export function getToken() {
  const token = localStorage.getItem('token')
  return token || null
}
/**
 * 检查用户是否已登录
 * @returns {boolean}
 */
export function isLoggedIn() {
  return getUser() !== null && getToken() !== null
}

/**
 * 退出登录
 */
export function logout() {
  localStorage.removeItem('user')
  localStorage.removeItem('token')
  sessionStorage.clear()
}

/**
 * 跳转到登录页面
 */
export function redirectToLogin() {
  logout()
  
  // 使用 nextTick 确保在下一个事件循环执行跳转
  setTimeout(() => {
    ElMessage.warning('请先登录')
    // 替换整个历史记录，防止返回
    router.replace('/login')
  }, 100)
}

/**
 * 检查登录状态，如果未登录则跳转到登录页
 * @returns {boolean} 返回是否已登录
 */
export function checkAuth() {
  if (!isLoggedIn()) {
    redirectToLogin()
    return false
  }
  return true
}

/**
 * 检查当前用户是否拥有指定角色之一
 * @param {number[]} roles - 允许的角色数组
 * @returns {boolean}
 */
export function hasRole(roles) {
  const user = getUser()
  if (!user || user.roleId === undefined) return false
  return roles.includes(user.roleId)
}

/**
 * 检查当前用户是否为管理端角色（可访问 Dashboard）
 * @returns {boolean}
 */
export function isManagement() {
  return hasRole([Role.REPLENISHER, Role.HR, Role.SUPERVISOR, Role.SYSTEM_ADMIN])
}

/**
 * 检查当前用户是否为系统管理员
 * @returns {boolean}
 */
export function isAdmin() {
  return hasRole([Role.SYSTEM_ADMIN])
}

/**
 * 检查当前用户是否为人事
 */
export function isHR() {
  return hasRole([Role.HR, Role.SYSTEM_ADMIN])
}

/**
 * 检查当前用户是否为监管
 */
export function isSupervisor() {
  return hasRole([Role.SUPERVISOR, Role.SYSTEM_ADMIN])
}

/**
 * 检查当前用户是否为购买用户
 * @returns {boolean}
 */
export function isBuyer() {
  return hasRole([Role.BUYER])
}

/**
 * 获取登录后应跳转的首页路径
 * @returns {string}
 */
export function getHomePath() {
  if (isBuyer()) return '/purchase'
  return '/dashboard'
}

export default {
  getUser,
  getToken,
  isLoggedIn,
  isTokenValid,
  getTokenExpireTime,
  hasRole,
  isManagement,
  isAdmin,
  isHR,
  isSupervisor,
  isBuyer,
  getHomePath,
  logout,
  redirectToLogin,
  checkAuth
}

import router from '@/router'
import { ElMessage } from 'element-plus'

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

export default {
  getUser,
  getToken,
  isLoggedIn,
  logout,
  redirectToLogin,
  checkAuth
}

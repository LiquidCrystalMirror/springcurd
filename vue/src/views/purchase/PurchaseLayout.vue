<template>
  <el-container class="purchase-layout">
    <el-header height="48px" class="purchase-header">
      <div class="header-left">
        <span class="app-title">商品购买</span>
        <el-menu
            mode="horizontal"
            :default-active="activeMenu"
            :router="true"
            class="header-nav"
            background-color="transparent"
            text-color="#bfcbd9"
            active-text-color="#ffffff"
        >
          <el-menu-item index="/purchase/order">下单</el-menu-item>
          <el-menu-item index="/purchase/history">订单历史</el-menu-item>
        </el-menu>
      </div>

      <div class="header-right">
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-avatar :size="28" icon="UserFilled" style="background-color: #ffd04b;" />
            <span class="user-name">欢迎，{{ userName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="isAdmin" command="goDashboard">
                <el-icon><Setting /></el-icon>
                管理后台
              </el-dropdown-item>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-main class="purchase-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import router from '@/router/index.js'
import authService from '@/service/AuthService.js'
import {
  ArrowDown,
  SwitchButton,
  Setting,
} from '@element-plus/icons-vue'

const route = useRoute()

const activeMenu = computed(() => route.path)

const user = computed(() => authService.getUser())
const userName = computed(() => {
  const userData = user.value
  return userData ? (userData.username || userData.name || '用户') : '用户'
})

const isAdmin = computed(() => authService.isAdmin())

const handleCommand = (command) => {
  if (command === 'logout') {
    localStorage.removeItem('user')
    localStorage.removeItem('token')
    sessionStorage.clear()
    ElMessage.success('退出登录成功')
    router.replace('/login')
  } else if (command === 'goDashboard') {
    router.push('/dashboard')
  }
}
</script>

<style scoped>
.purchase-layout {
  height: 100vh;
  background: #f0f2f5;
}

.purchase-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.app-title {
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 1px;
  white-space: nowrap;
}

.header-nav {
  border-bottom: none !important;
  background: transparent !important;
}

.header-nav .el-menu-item {
  height: 48px;
  line-height: 48px;
  border-bottom: 2px solid transparent;
}

.header-nav .el-menu-item.is-active {
  border-bottom-color: #ffd04b;
  color: #ffffff !important;
}

.header-nav .el-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.1) !important;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.9);
  padding: 4px 12px;
  border-radius: 6px;
  transition: background-color 0.3s;
}

.user-info:hover {
  background-color: rgba(255, 255, 255, 0.15);
}

.user-name {
  font-size: 14px;
}

.purchase-main {
  padding: 20px;
  overflow-y: auto;
}
</style>

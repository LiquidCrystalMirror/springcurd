import { createWebHashHistory, createRouter } from 'vue-router'
import authService from "@/service/AuthService.js";
import { Role, MANAGEMENT_ROLES } from '@/enum/user/Role.js'

let routes=[
  {
    path:'/',
    redirect:'/login'
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
  },
  {
    path:'/register',
    name:'register',
    component:()=>import('@/views/Register.vue')
  },
  // ==================== 管理端布局 ====================
  {
    path:'/dashboard',
    name:'dashboard',
    component:()=>import('@/views/Dashboard.vue'),
    meta: { requiresAuth: true, roles: MANAGEMENT_ROLES },
    children:[
      {
        path:'staff',
        name:'staff',
        component:()=>import('@/views/user/ListView.vue'),
        meta: { requiresAuth: true, roles: [Role.HR, Role.SUPERVISOR, Role.SYSTEM_ADMIN] }
      },
      {
        path:'dept',
        name:'dept',
        component:()=>import('@/views/dept/ListView.vue'),
        meta: { requiresAuth: true, roles: [Role.SYSTEM_ADMIN] }
      },
      {
        path:'stock',
        name:'stock',
        component:()=>import('@/views/stock/ListView.vue'),
        meta: { requiresAuth: true, roles: MANAGEMENT_ROLES }
      },
      {
        path:'replenish',
        name:'replenish',
        component:()=>import('@/views/stock/ReplenishList.vue'),
        meta: { requiresAuth: true, roles: [Role.REPLENISHER, Role.SUPERVISOR, Role.SYSTEM_ADMIN] }
      },
      {
        path:'orders',
        name:'orders',
        component:()=>import('@/views/orders/ListView.vue'),
        meta: { requiresAuth: true, roles: MANAGEMENT_ROLES }
      },
      {
        path:'api-tester',
        name:'apiTester',
        component:()=>import('@/views/ApiTester.vue'),
        meta: { requiresAuth: true, roles: [Role.SYSTEM_ADMIN] }
      }
    ]
  },
  // ==================== 购买端布局 ====================
  {
    path:'/purchase',
    name:'purchase',
    component:()=>import('@/views/purchase/PurchaseLayout.vue'),
    meta: { requiresAuth: true, roles: [Role.BUYER, Role.SYSTEM_ADMIN] },
    redirect:'/purchase/order',
    children:[
      {
        path:'order',
        name:'purchaseOrder',
        component:()=>import('@/views/purchase/OrderCreate.vue'),
        meta: { requiresAuth: true, roles: [Role.BUYER, Role.SYSTEM_ADMIN] }
      },
      {
        path:'history',
        name:'purchaseHistory',
        component:()=>import('@/views/purchase/OrderHistory.vue'),
        meta: { requiresAuth: true, roles: [Role.BUYER, Role.SYSTEM_ADMIN] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

// 全局前置路由守卫
router.beforeEach((to, from, next) => {
  console.log('路由守卫触发:', {
    from: from.path,
    to: to.path,
    isLoggedIn: authService.isLoggedIn()
  })

  // 白名单路径（不需要登录验证）
  const whiteList = ['/login', '/register']

  // 检查目标路径是否在白名单中
  if (whiteList.includes(to.path)) {
    // 如果已登录，访问登录页则重定向到首页
    if (to.path === '/login' && authService.isLoggedIn()) {
      next(authService.getHomePath())
    } else {
      next()
    }
    return
  }

  // 其他路径需要验证登录状态
  if (!authService.isLoggedIn()) {
    authService.redirectToLogin()
    return
  }

  // 角色权限检查
  if (to.meta.roles) {
    if (!authService.hasRole(to.meta.roles)) {
      console.warn(`无权访问 ${to.path}，当前角色无权限`)
      const homePath = authService.getHomePath()
      // 防止死循环：如果 getHomePath 返回的就是被拒的路径，回登录页
      if (homePath === to.path || homePath === from.path) {
        console.warn('getHomePath 与被拒路径相同，防止死循环，跳转登录页')
        authService.logout()
        next('/login')
        return
      }
      next(homePath)
      return
    }
  }

  next()
})

export default router

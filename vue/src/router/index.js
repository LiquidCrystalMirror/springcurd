import { createWebHashHistory, createRouter } from 'vue-router'
import authService from "@/service/AuthService.js";

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
  {
    path:'/dashboard',
    name:'dashboard',
    component:()=>import('@/views/Dashboard.vue'),
    meta: { requiresAuth: true },
    children:[
      {
        path:'/dashboard/user',
        name:'user',
        component:()=>import('@/views/user/ListView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path:'/dashboard/dept',
        name:'dept',
        component:()=>import('@/views/dept/ListView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path:'/dashboard/stock',
        name:'stock',
        component:()=>import('@/views/stock/ListView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path:'/dashboard/orders',
        name:'orders',
        component:()=>import('@/views/orders/ListView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path:'/dashboard/api-tester',
        name:'apiTester',
        component:()=>import('@/views/ApiTester.vue'),
        meta: { requiresAuth: true }
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
      next('/dashboard')
    } else {
      next()
    }
    return
  }

  // 其他路径需要验证登录状态
  if (authService.isLoggedIn()) {
    next()
  } else {
    authService.redirectToLogin()
  }
})

export default router

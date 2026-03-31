<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <h2>欢迎回来</h2>
          <p>请登录您的账号</p>
        </div>
      </template>

      <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          @submit.prevent="handleLogin"
      >
        <el-form-item prop="id">
          <el-input
              v-model="loginForm.id"
              type="number"
              placeholder="用户ID"
              prefix-icon="User"
              size="large"
              clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              prefix-icon="Lock"
              size="large"
              show-password
              clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="login-btn"
              @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="footer-buttons">
        <el-divider>
          <span class="divider-text">还没有账号？</span>
        </el-divider>
        <el-button
            size="large"
            plain
            class="register-btn"
            @click="handleRegister"
        >
          立即注册
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginApi} from "@/api/LoginApi.js";

const router = useRouter()
const loginFormRef = ref(null)
const loading = ref(false)

// 登录表单数据 - 使用 id
const loginForm = reactive({
  id: null,
  password: ''
})

// 表单验证规则
const loginRules = {
  id: [
    { required: true, message: '请输入用户ID', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}


// 登录方法
const handleLogin = async () => {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true

  try {
    const response = await loginApi({ id: loginForm.id, password: loginForm.password })




    if (response.code === 200) {
      ElMessage.success('登录成功')
      // 存储用户信息
      localStorage.setItem('user', JSON.stringify(response.data.user))
      localStorage.setItem('token', JSON.stringify(response.data.token))
      router.push('/dashboard')
    } else {
      ElMessage.error(response.message || '登录失败')
    }
  } catch (error) {
    console.error('登录错误:', error)
    ElMessage.error('登录失败，请检查网络')
  } finally {
    loading.value = false
  }
}

// 跳转到注册页
const handleRegister = () => {
  router.push('/register')
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 100%;
  max-width: 450px;
  border-radius: 20px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  background: white;
}

.card-header {
  text-align: center;
}

.card-header h2 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 600;
  color: #1f2937;
}

.card-header p {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

.login-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

.footer-buttons {
  margin-top: 24px;
}

.divider-text {
  color: #9ca3af;
  font-size: 14px;
}

.register-btn {
  width: 100%;
  border-radius: 10px;
  height: 44px;
}

</style>
<template>
  <div class="register-container">
    <el-card class="register-card">
      <template #header>
        <div class="card-header">
          <h2>用户注册</h2>
        </div>
      </template>

      <!-- 注册表单 -->
      <el-form
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          label-width="80px"
      >
        <el-form-item label="用户ID" prop="id">
          <el-input
              v-model="registerForm.id"
              type="number"
              placeholder="请输入用户ID"
              prefix-icon="User"
              clearable
          />
        </el-form-item>

        <el-form-item label="用户名" prop="name">
          <el-input
              v-model="registerForm.name"
              placeholder="请输入用户名"
              prefix-icon="User"
              clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="Lock"
              show-password
              clearable
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              prefix-icon="Lock"
              show-password
              clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button
              type="primary"
              :loading="loading"
              class="register-btn"
              @click="handleRegister"
          >
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 底部按钮区域 -->
      <div class="footer-buttons">
        <el-divider>
          <span class="divider-text">已有账号？</span>
        </el-divider>
        <el-button
            type="info"
            plain
            class="login-btn"
            @click="handleLogin"
        >
          返回登录
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { registerApi} from "@/api/RegisterApi.js";

const router = useRouter()
const registerFormRef = ref(null)
const loading = ref(false)

// 注册表单数据
const registerForm = reactive({
  id: null,
  name: '',
  password: '',
  confirmPassword: '',
})

// 修改：使用函数形式的验证规则
const checkConfirmPassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

// 表单验证规则
const registerRules = {
  id: [
    { required: true, message: '请输入用户ID', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { validator: checkConfirmPassword, trigger: 'blur' }
  ],
}

// 注册方法
const handleRegister = async () => {
  // 表单验证
  try {
    await registerFormRef.value.validate()
  } catch (error) {
    console.log('表单验证失败:', error)
    return
  }

  loading.value = true

  try {
    // 准备提交数据
    const submitData = {
      id: Number(registerForm.id),  // 确保是数字类型
      name: registerForm.name,
      password: registerForm.password,
    }

    console.log('发送注册数据:', submitData)

    const response = await registerApi({
      id: submitData.id,
      name: submitData.name,
      password: submitData.password,
    })

    console.log('注册响应:', response.data)

    if (response.code === 200) {
      ElMessage.success('注册成功，请登录')
      // 延迟跳转，让用户看到成功消息
      setTimeout(() => {
        router.push('/login')
      }, 1000)
    } else {
      ElMessage.error(response.message || '注册失败')
    }
  } catch (error) {
    console.error('注册错误:', error)

    if (error.response) {
      // 服务器返回错误
      console.log('错误状态码:', error.response.status)
      console.log('错误数据:', error.response.data)
      ElMessage.error(error.response?.message || `请求失败: ${error.response.status}`)
    } else if (error.request) {
      // 请求已发送但无响应
      console.log('请求对象:', error.request)
      ElMessage.error('服务器无响应，请检查后端服务')
    } else {
      // 其他错误
      ElMessage.error('注册失败: ' + error.message)
    }
  } finally {
    loading.value = false
  }
}

// 返回登录
const handleLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.register-card {
  width: 100%;
  max-width: 500px;
  border-radius: 12px;
  box-shadow: 0 20px 35px rgba(0, 0, 0, 0.1);
}

.card-header {
  text-align: center;
}

.card-header h2 {
  margin: 0;
  color: #333;
  font-size: 24px;
}

.register-btn {
  width: 100%;
  margin-top: 10px;
}

.footer-buttons {
  margin-top: 20px;
  text-align: center;
}

.divider-text {
  color: #999;
  font-size: 14px;
}

.login-btn {
  width: 100%;
  margin-top: 10px;
}

/* 响应式适配 */
@media (max-width: 768px) {
  .register-card {
    width: 90%;
    max-width: 400px;
  }
}
</style>
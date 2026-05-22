<template>
  <div class="api-tester">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>API 测试工具</span>
          <el-tag type="info">在线调试</el-tag>
        </div>
      </template>

      <!-- API 选择 -->
      <el-form :model="testForm" label-width="100px">
        <el-form-item label="选择接口">
          <el-select v-model="testForm.apiPath" placeholder="请选择要测试的接口" @change="handleApiChange" style="width: 100%">
            <el-option-group label="订单管理">
              <el-option label="添加订单" value="/orders/add" />
              <el-option label="取消订单" value="/orders/cancel" />
            </el-option-group>
            <el-option-group label="库存管理">
              <el-option label="商品补货" value="/admin/stock/replenish" />
            </el-option-group>
            <el-option-group label="Dashboard查询">
              <el-option label="获取用户列表" value="/dashboard/getUsers" />
              <el-option label="获取订单列表" value="/dashboard/getOrders" />
              <el-option label="获取库存列表" value="/dashboard/getStocks" />
            </el-option-group>
          </el-select>
        </el-form-item>

        <!-- 请求模式 -->
        <el-form-item label="请求模式">
          <el-radio-group v-model="testForm.mode">
            <el-radio label="test">测试模式（不影响数据库）</el-radio>
            <el-radio label="mock">纯模拟响应</el-radio>
          </el-radio-group>
          <el-alert 
            v-if="testForm.mode === 'test'" 
            title="测试模式：请求会发送到后端，但后端不会执行数据库操作，仅返回模拟结果" 
            type="warning" 
            :closable="false"
            style="margin-top: 10px"
          />
          <el-alert 
            v-if="testForm.mode === 'mock'" 
            title="模拟模式：不会发送真实请求，仅用于测试前端展示" 
            type="info" 
            :closable="false"
            style="margin-top: 10px"
          />
        </el-form-item>

        <!-- 请求方法（固定为 POST） -->
        <el-form-item label="请求方法">
          <el-tag type="success">POST</el-tag>
        </el-form-item>

        <!-- 请求参数 -->
        <el-form-item label="请求参数">
          <el-input
            v-model="testForm.requestBody"
            type="textarea"
            :rows="8"
            placeholder="请输入 JSON 格式的请求参数"
            @blur="formatJson"
          />
        </el-form-item>

        <!-- 操作按钮 -->
        <el-form-item>
          <el-button type="primary" @click="sendRequest" :loading="loading">
            <el-icon><Promotion /></el-icon>
            发送请求
          </el-button>
          <el-button @click="resetForm">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
          <el-button @click="loadExample">
            <el-icon><Document /></el-icon>
            加载示例
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 响应结果 -->
    <el-card class="box-card response-card" v-if="response">
      <template #header>
        <div class="card-header">
          <span>响应结果</span>
          <el-tag :type="responseStatus === 200 ? 'success' : 'danger'">
            HTTP {{ responseStatus }}
          </el-tag>
        </div>
      </template>

      <!-- 响应时间 -->
      <div class="response-meta">
        <el-tag size="small" type="info">耗时: {{ responseTime }}ms</el-tag>
        <el-tag size="small" type="info">大小: {{ responseSize }}</el-tag>
      </div>

      <!-- 响应内容 -->
      <el-tabs v-model="activeTab">
        <el-tab-pane label="JSON" name="json">
          <pre class="json-viewer">{{ formattedResponse }}</pre>
        </el-tab-pane>
        <el-tab-pane label="原始数据" name="raw">
          <pre class="json-viewer">{{ rawResponse }}</pre>
        </el-tab-pane>
      </el-tabs>

      <!-- 复制按钮 -->
      <div class="response-actions">
        <el-button size="small" @click="copyResponse">
          <el-icon><CopyDocument /></el-icon>
          复制响应
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Refresh, Document, CopyDocument } from '@element-plus/icons-vue'
import axios from 'axios'

// 测试表单
const testForm = ref({
  apiPath: '',
  requestBody: '{}',
  mode: 'test'  // 默认使用测试模式
})

// 响应数据
const response = ref(null)
const responseStatus = ref(0)
const responseTime = ref(0)
const responseSize = ref('')
const loading = ref(false)
const activeTab = ref('json')

// 格式化后的响应
const formattedResponse = computed(() => {
  if (!response.value) return ''
  try {
    return JSON.stringify(response.value, null, 2)
  } catch (e) {
    return String(response.value)
  }
})

// 原始响应
const rawResponse = computed(() => {
  return JSON.stringify(response.value)
})

// API 切换时加载默认参数
const handleApiChange = (path) => {
  const examples = {
    '/orders/add': {
      orderNo: `ORD${Date.now()}`,
      platformId: 'P001',
      items: [
        {
          productId: 1,
          quantity: 5
        },
        {
          productId: 2,
          quantity: 3
        }
      ]
    },
    '/orders/cancel': {
      orderNo: 'ORD1234567890',
      platformId: 'P001',
      items: [
        {
          productId: 1,
          quantity: 5
        }
      ]
    },
    '/admin/stock/replenish': {
      items: [
        {
          productId: 1,
          quantity: 100
        },
        {
          productId: 2,
          quantity: 50
        }
      ]
    },
    '/dashboard/getUsers': {
      page: 1,
      pageSize: 10
    },
    '/dashboard/getOrders': {
      orderNo: '',
      platformId: '',
      productId: null,
      status: null,
      page: 1,
      pageSize: 10
    },
    '/dashboard/getStocks': {
      productId: null,
      stock: null,
      page: 1,
      pageSize: 10
    }
  }
  
  testForm.value.requestBody = JSON.stringify(examples[path] || {}, null, 2)
}

// 发送请求
const sendRequest = async () => {
  if (!testForm.value.apiPath) {
    ElMessage.warning('请先选择接口')
    return
  }

  loading.value = true
  const startTime = Date.now()

  try {
    let res
    
    if (testForm.value.mode === 'mock') {
      // 模拟模式：不发送真实请求
      await new Promise(resolve => setTimeout(resolve, 500)) // 模拟网络延迟
      
      // 根据选择的接口返回模拟数据
      res = {
        status: 200,
        data: generateMockResponse(testForm.value.apiPath, testForm.value.requestBody)
      }
    } else {
      // 测试模式：发送真实请求到后端（但不执行数据库操作）
      // 验证 JSON 格式
      let params
      try {
        params = JSON.parse(testForm.value.requestBody)
      } catch (e) {
        ElMessage.error('请求参数不是有效的 JSON 格式')
        loading.value = false
        return
      }
      
      const headers = {
        'Content-Type': 'application/json',
        'X-Test-Mode': 'true'  // 测试模式标记
      }
      
      res = await axios.post(testForm.value.apiPath, params, {
        headers: headers
      })
    }

    response.value = res.data
    responseStatus.value = res.status
    responseTime.value = Date.now() - startTime
    responseSize.value = formatBytes(JSON.stringify(res.data).length)
    
    ElMessage.success(
      testForm.value.mode === 'mock' ? '模拟响应成功' : 
      '测试模式响应成功（未影响数据库）'
    )
  } catch (error) {
    response.value = error.response?.data || { error: error.message }
    responseStatus.value = error.response?.status || 0
    responseTime.value = Date.now() - startTime
    responseSize.value = formatBytes(JSON.stringify(response.value).length)
    
    ElMessage.error(`请求失败: ${error.message}`)
  } finally {
    loading.value = false
  }
}

// 生成模拟响应
const generateMockResponse = (apiPath, requestBody) => {
  let params = {}
  try {
    params = JSON.parse(requestBody)
  } catch (e) {
    params = {}
  }

  const page = params.page || 1
  const pageSize = params.pageSize || 10
  
  // 根据不同的 API 路径生成不同的模拟数据
  switch (apiPath) {
    case '/orders/add':
      return {
        code: 200,
        msg: 'success',
        data: {
          orderNo: params.orderNo,
          platformId: params.platformId,
          operations: params.items.reduce((acc, item) => {
            acc[item.productId] = (acc[item.productId] || 0) + item.quantity
            return acc
          }, {})
        }
      }
    
    case '/orders/cancel':
      const cancelResults = params.items.map(item => 
        `商品[${item.productId}]取消成功（测试模式）`
      ).join('\n')
      return {
        code: 200,
        msg: 'success',
        data: cancelResults
      }
    
    case '/admin/stock/replenish':
      const replenishOps = params.items.reduce((acc, item) => {
        acc[item.productId] = (acc[item.productId] || 0) + item.quantity
        return acc
      }, {})
      return {
        code: 200,
        msg: 'success',
        data: {
          id: Date.now(),
          operations: replenishOps
        }
      }
    
    case '/dashboard/getUsers':
      return {
        code: 200,
        msg: 'success',
        data: {
          total: 100,
          list: Array.from({ length: pageSize }, (_, i) => ({
            id: (page - 1) * pageSize + i + 1,
            name: `用户${(page - 1) * pageSize + i + 1}`,
            status: Math.floor(Math.random() * 3),
            roleId: Math.floor(Math.random() * 3) + 1,
            createTime: new Date().toISOString(),
            updateTime: new Date().toISOString()
          }))
        }
      }
    
    case '/dashboard/getOrders':
      return {
        code: 200,
        msg: 'success',
        data: {
          total: 200,
          list: Array.from({ length: pageSize }, (_, i) => ({
            id: (page - 1) * pageSize + i + 1,
            orderNo: `ORD${Date.now()}${i}`,
            platformId: `P${String(Math.floor(Math.random() * 10)).padStart(3, '0')}`,
            productId: Math.floor(Math.random() * 100) + 1,
            quantity: Math.floor(Math.random() * 10) + 1,
            status: Math.floor(Math.random() * 3),
            createTime: new Date().toISOString(),
            updateTime: new Date().toISOString()
          }))
        }
      }
    
    case '/dashboard/getStocks':
      return {
        code: 200,
        msg: 'success',
        data: {
          total: 150,
          list: Array.from({ length: pageSize }, (_, i) => ({
            productId: (page - 1) * pageSize + i + 1,
            stock: Math.floor(Math.random() * 1000),
            version: Math.floor(Math.random() * 100),
            updateTime: new Date().toISOString()
          }))
        }
      }
    
    default:
      return {
        code: 200,
        msg: 'success',
        data: {}
      }
  }
}

// 格式化 JSON
const formatJson = () => {
  try {
    const obj = JSON.parse(testForm.value.requestBody)
    testForm.value.requestBody = JSON.stringify(obj, null, 2)
  } catch (e) {
    // 不处理，允许用户继续编辑
  }
}

// 重置表单
const resetForm = () => {
  testForm.value = {
    apiPath: '',
    requestBody: '{}'
  }
  response.value = null
  responseStatus.value = 0
  responseTime.value = 0
  responseSize.value = ''
}

// 加载示例
const loadExample = () => {
  if (testForm.value.apiPath) {
    handleApiChange(testForm.value.apiPath)
  } else {
    ElMessage.warning('请先选择接口')
  }
}

// 复制响应
const copyResponse = () => {
  navigator.clipboard.writeText(formattedResponse.value)
  ElMessage.success('已复制到剪贴板')
}

// 格式化字节大小
const formatBytes = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<style scoped>
.api-tester {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.box-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.response-card {
  margin-top: 20px;
}

.response-meta {
  margin-bottom: 15px;
  display: flex;
  gap: 10px;
}

.json-viewer {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  max-height: 500px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.response-actions {
  margin-top: 15px;
  text-align: right;
}


</style>

<template>
  <div class="page-container">
    <!-- 页面标题栏 -->
    <div class="page-header">
      <div class="page-header-left">
        <el-icon class="page-header-icon"><Checked /></el-icon>
        <div class="page-header-text">
          <h2 class="page-title">补货单审核</h2>
          <span class="page-breadcrumb">
            <router-link to="/dashboard/stock" class="breadcrumb-link">库存管理</router-link>
            <span class="breadcrumb-sep">/</span>
            <span class="breadcrumb-current">补货单审核</span>
          </span>
        </div>
      </div>
      <div class="page-header-right">
        <el-button size="small" @click="router.push('/dashboard/stock')">
          <el-icon><Plus /></el-icon>
          新建补货
        </el-button>
      </div>
    </div>

    <!-- 状态统计卡片 -->
    <div class="stats-row">
      <div class="stat-card" :class="{ active: searchForm.status === 0 }" @click="filterByStatus(0)">
        <span class="stat-num" style="color: #e6a23c">{{ stats.pending }}</span>
        <span class="stat-label">待审核</span>
      </div>
      <div class="stat-card" :class="{ active: searchForm.status === 1 }" @click="filterByStatus(1)">
        <span class="stat-num" style="color: #67c23a">{{ stats.approved }}</span>
        <span class="stat-label">审核通过</span>
      </div>
      <div class="stat-card" :class="{ active: searchForm.status === 2 }" @click="filterByStatus(2)">
        <span class="stat-num" style="color: #f56c6c">{{ stats.rejected }}</span>
        <span class="stat-label">审核拒绝</span>
      </div>
      <div class="stat-card" :class="{ active: searchForm.status === 3 }" @click="filterByStatus(3)">
        <span class="stat-num" style="color: #409eff">{{ stats.executed }}</span>
        <span class="stat-label">已执行</span>
      </div>
    </div>

    <!-- 搜索栏（固定顶部） -->
    <div class="search-bar">
      <el-form :model="searchForm" inline>
        <el-form-item label="审核状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable @change="handleSearch">
            <el-option label="待审核" :value="0" />
            <el-option label="审核通过" :value="1" />
            <el-option label="审核拒绝" :value="2" />
            <el-option label="已执行" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 列表 -->
    <div class="table-card">
      <el-table :data="tableData" stripe border style="width: 100%" v-loading="loading" empty-text="暂无补货单数据">
      <el-table-column prop="id" label="补货单号" min-width="13%" show-overflow-tooltip />
      <el-table-column prop="creatorName" label="创建人" min-width="8%" />
      <el-table-column label="状态" min-width="7%">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)" size="small">
            {{ statusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="itemCount" label="商品种类" min-width="7%" />
      <el-table-column prop="totalQuantity" label="补货总量" min-width="7%" />
      <el-table-column prop="createTime" label="创建时间" min-width="13%" />
      <el-table-column prop="approverName" label="审核人" min-width="8%" />
      <el-table-column prop="approveTime" label="审核时间" min-width="13%" />
      <el-table-column prop="remark" label="审核备注" min-width="11%" show-overflow-tooltip />

      <el-table-column label="操作" min-width="13%">
        <template #default="scope">
          <el-button
            v-if="canApprove && scope.row.status === 0"
            size="small"
            type="success"
            @click="handleApprove(scope.row, true)"
          >
            通过
          </el-button>
          <el-button
            v-if="canApprove && scope.row.status === 0"
            size="small"
            type="danger"
            @click="handleApprove(scope.row, false)"
          >
            拒绝
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="searchForm.page"
        v-model:page-size="searchForm.pageSize"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <!-- 审核对话框 -->
    <el-dialog
      :title="approveForm.approved ? '确认通过' : '确认拒绝'"
      v-model="dialogVisible"
      width="30%"
      min-width="360px"
    >
      <el-form label-width="80px">
        <el-form-item label="补货单号">
          <span>{{ approveForm.id }}</span>
        </el-form-item>
        <el-form-item label="审核结果">
          <el-tag :type="approveForm.approved ? 'success' : 'danger'">
            {{ approveForm.approved ? '通过' : '拒绝' }}
          </el-tag>
        </el-form-item>
        <el-form-item
          :label="approveForm.approved ? '审核备注' : '拒绝理由'"
          :required="!approveForm.approved"
        >
          <el-input
            v-model="approveForm.remark"
            type="textarea"
            :rows="3"
            :placeholder="approveForm.approved ? '可选填备注' : '请填写拒绝理由（必填）'"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          :type="approveForm.approved ? 'success' : 'danger'"
          :loading="approving"
          @click="doApprove"
        >
          确认{{ approveForm.approved ? '通过' : '拒绝' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getReplenishList, approveReplenish } from '@/api/ReplenishApi.js'
import { Search, Refresh, Checked, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { hasRole } from '@/service/AuthService.js'
import { Role } from '@/enum/user/Role.js'

const router = useRouter()

// 审核权限：监管、系统管理员
const canApprove = computed(() => hasRole([Role.SUPERVISOR, Role.SYSTEM_ADMIN]))

// 状态映射
const statusMap = { 0: '待审核', 1: '审核通过', 2: '审核拒绝', 3: '已执行' }
const statusTagMap = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }

const statusText = (status) => statusMap[status] || '未知'
const statusTagType = (status) => statusTagMap[status] || 'info'

// 搜索表单
const searchForm = ref({
  status: null,
  page: 1,
  pageSize: 10,
})

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

// 审核对话框
const dialogVisible = ref(false)
const approving = ref(false)
const approveForm = ref({
  id: null,
  approved: true,
  remark: '',
})

const loadData = () => {
  loading.value = true
  getReplenishList(searchForm.value)
    .then((resp) => {
      if (resp.code === 200 && resp.data) {
        tableData.value = resp.data.list || []
        total.value = resp.data.total || 0
      } else {
        tableData.value = []
        total.value = 0
      }
    })
    .catch((err) => {
      console.error('查询补货单失败:', err)
      tableData.value = []
      total.value = 0
    })
    .finally(() => {
      loading.value = false
      updateStats()
    })
}

const handleSearch = () => {
  searchForm.value.page = 1
  loadData()
}

const handleReset = () => {
  searchForm.value = { status: null, page: 1, pageSize: 10 }
  loadData()
}

// 打开审核对话框
const handleApprove = (row, approved) => {
  approveForm.value = {
    id: row.id,
    approved: approved,
    remark: '',
  }
  dialogVisible.value = true
}

// 执行审核
const doApprove = () => {
  if (!approveForm.value.approved && !approveForm.value.remark.trim()) {
    ElMessage.warning('拒绝时必须填写理由')
    return
  }

  approving.value = true
  approveReplenish({
    id: approveForm.value.id,
    approved: approveForm.value.approved,
    remark: approveForm.value.remark.trim() || undefined,
  })
    .then((resp) => {
      if (resp.code === 200) {
        ElMessage.success(approveForm.value.approved ? '审核通过' : '已拒绝')
        dialogVisible.value = false
        loadData()
      } else {
        ElMessage.error(resp.message || resp.msg || '审核失败')
      }
    })
    .catch((err) => {
      console.error('审核失败:', err)
      ElMessage.error('审核失败，请重试')
    })
    .finally(() => {
      approving.value = false
    })
}

// ==================== 统计卡片 ====================
const stats = ref({ pending: 0, approved: 0, rejected: 0, executed: 0 })

const updateStats = () => {
  if (!tableData.value.length && searchForm.value.status !== null) {
    return
  }
  const counts = { 0: 0, 1: 0, 2: 0, 3: 0 }
  tableData.value.forEach(row => {
    if (counts[row.status] !== undefined) counts[row.status]++
  })
  stats.value = { pending: counts[0], approved: counts[1], rejected: counts[2], executed: counts[3] }
}

const filterByStatus = (status) => {
  if (searchForm.value.status === status) {
    searchForm.value.status = null
  } else {
    searchForm.value.status = status
  }
  handleSearch()
}

// 初始化
loadData()
</script>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: linear-gradient(180deg, #f8f9fc 0%, #eef1f6 100%);
}

/* ==================== 页面标题栏 ==================== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 1.2% 1.5%;
  margin: 0 1.5%;
  margin-top: 1%;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  flex-shrink: 0;
}

.page-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-header-icon {
  font-size: 26px;
  color: #409eff;
  background: linear-gradient(135deg, #ecf5ff, #d9ecff);
  padding: 8px;
  border-radius: 10px;
}

.page-header-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.page-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #303133;
  letter-spacing: 0.5px;
}

.page-breadcrumb {
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}

.breadcrumb-link {
  color: #409eff;
  text-decoration: none;
  transition: color 0.2s;
}

.breadcrumb-link:hover {
  color: #66b1ff;
  text-decoration: underline;
}

.breadcrumb-sep {
  color: #c0c4cc;
}

.breadcrumb-current {
  color: #606266;
}

.page-header-right {
  flex-shrink: 0;
}

/* ==================== 状态统计卡片 ==================== */
.stats-row {
  display: flex;
  gap: 1.5%;
  margin: 1% 1.5% 0;
  flex-shrink: 0;
}

.stat-card {
  flex: 1;
  background: #fff;
  border-radius: 10px;
  padding: 1.2% 1.5%;
  text-align: center;
  cursor: pointer;
  box-shadow: 0 1px 5px rgba(0,0,0,0.03);
  transition: all 0.25s ease;
  border: 2px solid transparent;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
}

.stat-card.active {
  border-color: #409eff;
  background: linear-gradient(135deg, #ecf5ff, #f0f7ff);
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 1px;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

/* ==================== 搜索栏 ==================== */
.search-bar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  padding: 8px 1.5% 0;
  margin: 1% 1.5% 0;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  flex-shrink: 0;
}

/* ==================== 表格 ==================== */
.table-card {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  margin: 1% 1.5% 0;
  overflow: hidden;
  flex: 1;
}

.table-card :deep(.el-table) {
  --el-table-border-color: transparent;
}

.table-card :deep(.el-table th) {
  background: linear-gradient(180deg, #f5f7fa, #ebeef5);
  font-weight: 600;
  color: #303133;
  border-bottom: 2px solid #dcdfe6;
}

.table-card :deep(.el-table tr:hover > td) {
  background: #f5f7fa;
}

/* ==================== 分页 ==================== */
.pagination-wrap {
  position: sticky;
  bottom: 0;
  background: #fff;
  padding: 8px 1.5%;
  border-top: 1px solid #f0f0f0;
  z-index: 50;
  flex-shrink: 0;
}
</style>

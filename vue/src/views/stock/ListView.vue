<template>
  <div class="page-container">
    <!-- 页面标题栏 -->
    <div class="page-header">
      <div class="page-header-left">
        <el-icon class="page-header-icon"><Box /></el-icon>
        <div class="page-header-text">
          <h2 class="page-title">库存管理</h2>
          <span class="page-breadcrumb">
            <span class="breadcrumb-current">库存管理</span>
            <span class="breadcrumb-sep">/</span>
            <router-link v-if="canReplenish" to="/dashboard/replenish" class="breadcrumb-link">补货单审核</router-link>
          </span>
        </div>
      </div>
      <div class="page-header-right">
        <span class="header-hint" v-if="canReplenish">
          从左侧表格选择商品 → 加入右侧补货清单 → 提交审核
        </span>
      </div>
    </div>
    <!-- 搜索栏（固定顶部） -->
    <div class="search-bar">
      <el-form :model="searchForm" inline>
        <el-form-item label="商品ID">
          <el-input v-model="searchForm.productId" placeholder="请输入商品ID" clearable />
        </el-form-item>
        <el-form-item label="库存数量">
          <el-input v-model="searchForm.stock" placeholder="请输入库存数量" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="findStocks">
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

    <!-- 主体：左库存表格 + 右补货清单 -->
    <div class="main-layout">
      <!-- 左侧：库存表格 -->
      <div class="table-panel">
        <div class="table-card">
          <el-table :data="tableData" stripe border style="width: 100%">
          <el-table-column prop="productId" label="商品ID" min-width="10%" />
          <el-table-column prop="productName" label="商品名称" min-width="20%" />
          <el-table-column prop="stock" label="库存数量" min-width="12%" />
          <el-table-column prop="version" label="版本号" min-width="10%" />
          <el-table-column prop="updateTime" label="更新时间" min-width="18%" />
          <el-table-column label="操作" min-width="22%">
            <template #default="scope">
              <template v-if="canReplenish">
                <el-input-number
                  v-model="rowQuantities[scope.row.productId]"
                  :min="1"
                  :max="99999"
                  size="small"
                  controls-position="right"
                  style="width: 55%"
                  placeholder="数量"
                />
                <el-button size="small" type="primary" style="margin-left: 4px" @click="addToCart(scope.row)">
                  加入补货
                </el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
        </div>

        <div class="pagination-wrap">
          <el-pagination
              v-model:current-page="searchForm.page"
              v-model:page-size="searchForm.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="total"
              @size-change="findStocks"
              @current-change="findStocks"
          />
        </div>
      </div>

      <!-- 右侧：补货清单 -->
      <div v-if="canReplenish" class="cart-panel">
        <el-card shadow="hover" class="cart-card">
          <template #header>
            <div class="cart-header">
              <span>
                <el-icon><ShoppingCart /></el-icon>
                <strong>补货清单</strong>
                <el-tag v-if="cartItems.length > 0" type="warning" size="small" style="margin-left: 6px">
                  {{ cartItems.length }} 种
                </el-tag>
              </span>
              <el-button type="success" size="small" @click="openAddDialog">
                <el-icon><Plus /></el-icon>
                手动添加
              </el-button>
            </div>
          </template>

          <el-empty v-if="cartItems.length === 0" description="点击表格「加入补货」添加商品" :image-size="60" />

          <template v-else>
            <TransitionGroup name="cart-list" tag="div" class="cart-items">
              <div v-for="(item, index) in cartItems" :key="item.productId" class="cart-item">
                <div class="cart-item-accent"></div>
                <div class="cart-item-body">
                  <div class="cart-item-info">
                    <span class="cart-item-id">#{{ item.productId }}</span>
                    <span class="cart-item-name">{{ item.productName }}</span>
                  </div>
                  <div class="cart-item-actions">
                    <el-input-number
                      v-model="item.quantity"
                      :min="1"
                      :max="99999"
                      size="small"
                      controls-position="right"
                      class="cart-qty-input"
                    />
                    <el-button size="small" type="danger" :icon="Delete" circle @click="removeFromCart(index)" />
                  </div>
                </div>
              </div>
            </TransitionGroup>

            <div class="cart-footer">
              <el-button size="small" @click="clearCart">清空</el-button>
              <el-button type="primary" size="small" :loading="submitting" @click="submitReplenishOrder">
                提交（{{ cartTotalQuantity }} 件）
              </el-button>
            </div>
          </template>
        </el-card>
      </div>
    </div>
  </div>

  <!-- 手动添加商品对话框 -->
  <el-dialog title="手动添加补货商品" v-model="addDialogVisible" width="480px">
    <el-form label-width="100px" :model="addForm" :rules="addFormRules" ref="addFormRef">
      <el-form-item label="商品ID" prop="productId">
        <el-input v-model="addForm.productId" placeholder="请输入商品ID" type="text" />
      </el-form-item>
      <el-form-item label="商品名称" prop="productName">
        <el-input v-model="addForm.productName" placeholder="请输入商品名称（必填，用于校验）" />
      </el-form-item>
      <el-form-item label="补货数量" prop="quantity">
        <el-input-number v-model="addForm.quantity" :min="1" :max="99999" style="width: 100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="addDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmAddItem">加入补货车</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from "vue";
import { useRouter } from 'vue-router';
import { getStocks } from "@/api/StockApi.js";
import { submitReplenish } from "@/api/ReplenishApi.js";
import { Search, Refresh, Plus, ShoppingCart, Delete, Check, Box } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { hasRole } from '@/service/AuthService.js';
import { Role } from '@/enum/user/Role.js';

const router = useRouter()

// 判断是否有补货权限（补货员 or 系统管理员）
const canReplenish = computed(() => hasRole([Role.REPLENISHER, Role.SYSTEM_ADMIN]));

// ==================== 搜索与表格 ====================
const searchForm = ref({
  productId: null,
  stock: null,
  page: 1,
  pageSize: 10,
});

const total = ref(0);
const tableData = ref([]);
// 每行的数量输入（与购物车独立）
const rowQuantities = ref({});

const handleReset = () => {
  searchForm.value = {
    productId: null,
    stock: null,
    page: 1,
    pageSize: 10,
  };
  findStocks();
};

const findStocks = () => {
  getStocks(searchForm.value).then((resp) => {
    if (resp.data && resp.data.list) {
      tableData.value = resp.data.list;
      total.value = resp.data.total || 0;
    } else {
      tableData.value = [];
      total.value = 0;
    }
  }).catch((error) => {
    console.error('查询库存失败:', error);
    tableData.value = [];
    total.value = 0;
  });
};

// ==================== 购物车逻辑 ====================
const cartItems = ref([]);
const submitting = ref(false);

const cartTotalQuantity = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + item.quantity, 0);
});

// 将表格行商品加入购物车
const addToCart = (row) => {
  const qty = rowQuantities.value[row.productId];
  if (!qty || qty < 1) {
    ElMessage.warning('请输入有效的补货数量（至少为1）');
    return;
  }

  const existingIndex = cartItems.value.findIndex(item => item.productId === row.productId);
  if (existingIndex >= 0) {
    cartItems.value[existingIndex].quantity += qty;
    ElMessage.success(`已追加 ${qty} 件，${row.productName || row.productId} 现共 ${cartItems.value[existingIndex].quantity} 件`);
  } else {
    cartItems.value.push({
      productId: row.productId,
      productName: row.productName || '',
      quantity: qty,
    });
    ElMessage.success(`已加入补货车：${row.productName || row.productId} × ${qty}`);
  }
  rowQuantities.value[row.productId] = null;
};

// 从购物车移除
const removeFromCart = (index) => {
  const removed = cartItems.value[index];
  cartItems.value.splice(index, 1);
  ElMessage.info(`已移除：${removed.productName || removed.productId}`);
};

// 清空购物车
const clearCart = () => {
  if (cartItems.value.length === 0) return;
  ElMessageBox.confirm('确定要清空补货车吗？', '确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    cartItems.value = [];
    ElMessage.success('补货车已清空');
  }).catch(() => {});
};

// 提交补货
const submitReplenishOrder = () => {
  if (cartItems.value.length === 0) {
    ElMessage.warning('补货车为空，请先添加商品');
    return;
  }

  submitting.value = true;
  const replenishData = {
    items: cartItems.value.map(item => ({
      productId: item.productId,
      productName: item.productName || undefined,
      quantity: item.quantity,
    })),
  };

  submitReplenish(replenishData).then((resp) => {
    if (resp.code === 200) {
      const batchId = resp.data?.id || '-'
      ElMessageBox.confirm(
        `补货申请已提交，批次号：${batchId}。是否前往补货单审核页查看？`,
        '提交成功',
        { confirmButtonText: '前往查看', cancelButtonText: '继续补货', type: 'success' }
      ).then(() => {
        router.push('/dashboard/replenish')
      }).catch(() => {})
      cartItems.value = [];
      findStocks();
    } else {
      ElMessage.error(resp.msg || resp.message || '补货失败');
    }
  }).catch((error) => {
    console.error('补货失败:', error);
    ElMessage.error('补货失败：' + (error.message || '未知错误'));
  }).finally(() => {
    submitting.value = false;
  });
};

// ==================== 手动添加对话框 ====================
const addDialogVisible = ref(false);
const addFormRef = ref(null);
const addForm = ref({
  productId: null,
  productName: '',
  quantity: 1,
});

const addFormRules = {
  productId: [
    { required: true, message: '请输入商品ID', trigger: 'blur' },
    { pattern: /^\d+$/, message: '商品ID必须为数字', trigger: 'blur' },
  ],
  productName: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
  ],
  quantity: [
    { required: true, message: '请输入补货数量', trigger: 'blur' },
    { type: 'number', min: 1, message: '补货数量必须大于0', trigger: 'blur' },
  ],
};

const openAddDialog = () => {
  addForm.value = { productId: null, productName: '', quantity: 1 };
  addDialogVisible.value = true;
};

const confirmAddItem = () => {
  addFormRef.value.validate().then(() => {
    const existingIndex = cartItems.value.findIndex(
      item => String(item.productId) === String(addForm.value.productId)
    );
    if (existingIndex >= 0) {
      cartItems.value[existingIndex].quantity += addForm.value.quantity;
      ElMessage.success(`已追加，现共 ${cartItems.value[existingIndex].quantity} 件`);
    } else {
      cartItems.value.push({
        productId: addForm.value.productId,
        productName: addForm.value.productName,
        quantity: addForm.value.quantity,
      });
      ElMessage.success('已加入补货车');
    }
    addDialogVisible.value = false;
  }).catch(() => {
    ElMessage.warning('请检查表单输入');
  });
};

// 初始化
findStocks();
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
  color: #e6a23c;
  background: linear-gradient(135deg, #fef0e6, #fde2cc);
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

.header-hint {
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  padding: 5px 12px;
  border-radius: 20px;
  white-space: nowrap;
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

/* ==================== 左右分栏 ==================== */
.main-layout {
  display: flex;
  gap: 1.5%;
  flex: 1;
  overflow: hidden;
  padding: 1% 1.5% 0;
}

/* 左侧：库存表格 */
.table-panel {
  width: 62%;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.table-card {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  padding: 0;
  overflow: hidden;
  flex: 1;
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

.pagination-wrap {
  background: #fff;
  padding: 8px 1.5%;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
}

/* 右侧：补货清单 */
.cart-panel {
  width: 36%;
  min-width: 0;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.cart-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 10px;
  border: none;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  overflow: hidden;
}

.cart-card :deep(.el-card__header) {
  background: linear-gradient(135deg, #f0f5ff 0%, #e8f0fe 100%);
  border-bottom: 1px solid #dce8f5;
  padding: 10px 14px;
  flex-shrink: 0;
}

.cart-card :deep(.el-card__body) {
  padding: 10px 14px;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.cart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cart-header span {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #303133;
}

/* ==================== 补货清单项 ==================== */
.cart-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cart-item {
  display: flex;
  gap: 0;
  border-radius: 6px;
  background: #fff;
  border: 1px solid #e8ecf1;
  overflow: hidden;
  transition: all 0.25s ease;
}

.cart-item:hover {
  border-color: #c0cde0;
  box-shadow: 0 2px 8px rgba(64,128,255,0.08);
  transform: translateY(-1px);
}

/* 左侧色条 */
.cart-item-accent {
  width: 4px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #409eff 0%, #66b1ff 100%);
}

.cart-item:nth-child(2n) .cart-item-accent {
  background: linear-gradient(180deg, #67c23a 0%, #85ce61 100%);
}
.cart-item:nth-child(3n) .cart-item-accent {
  background: linear-gradient(180deg, #e6a23c 0%, #ebb563 100%);
}
.cart-item:nth-child(4n) .cart-item-accent {
  background: linear-gradient(180deg, #f56c6c 0%, #f78989 100%);
}

.cart-item-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
}

.cart-item-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.cart-item-id {
  font-size: 11px;
  color: #a8abb2;
  letter-spacing: 0.5px;
}

.cart-item-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  line-height: 1.3;
}

.cart-item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cart-qty-input {
  width: 55%;
}

/* ==================== 底部操作栏 ==================== */
.cart-footer {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* ==================== TransitionGroup 动画 ==================== */
.cart-list-enter-active,
.cart-list-leave-active {
  transition: all 0.3s ease;
}
.cart-list-enter-from {
  opacity: 0;
  transform: translateX(20px);
}
.cart-list-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
.cart-list-leave-active {
  position: absolute;
}
</style>

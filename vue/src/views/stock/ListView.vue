<template>
  <el-form :model="searchForm" class="search-form">
    <el-row :gutter="10">
      <el-col :span="6">
        <el-form-item label="商品ID">
          <el-input v-model="searchForm.productId" placeholder="请输入商品ID" clearable />
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item label="库存数量">
          <el-input v-model="searchForm.stock" placeholder="请输入库存数量" clearable />
        </el-form-item>
      </el-col>
      <el-col :span="6">
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
      </el-col>
    </el-row>
  </el-form>

  <el-button v-if="isAdmin" type="primary" @click="handleAdd" class="mgb-4">
    <el-icon><Plus /></el-icon>
    补货
  </el-button>

  <el-table :data="tableData" stripe border style="width: 100%">
    <el-table-column prop="productId" label="商品ID" width="120" />
    <el-table-column prop="productName" label="商品名称" width="200" />
    <el-table-column prop="stock" label="库存数量" width="150" />
    <el-table-column prop="version" label="版本号" width="120" />
    <el-table-column prop="updateTime" label="更新时间" width="200" />
    
    <el-table-column label="操作">
      <template #default="scope">
        <el-button v-if="isAdmin" size="small" type="primary" @click="handleEdit(scope.row)">
          补货
        </el-button>
      </template>
    </el-table-column>
  </el-table>

  <el-pagination
      class="mgt-4"
      v-model:current-page="searchForm.page"
      v-model:page-size="searchForm.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      @size-change="findStocks"
      @current-change="findStocks"
  />

  <!-- 对话框 -->
  <el-dialog :title="stockForm.existingId ? '商品补货' : '新商品补货'" v-model="dialogVisible">
    <el-form label-width="100px" :model="stockForm" :rules="stockFormRules" ref="stockFormRef">
      <el-form-item label="商品ID" prop="productId">
        <el-input 
          v-model.number="stockForm.productId" 
          placeholder="请输入商品ID" 
          :readonly="!!stockForm.existingId"
          type="number"
        />
      </el-form-item>
      
      <el-form-item label="商品名称" prop="productName">
        <el-input 
          v-model="stockForm.productName" 
          placeholder="请输入商品名称（用于校验）" 
        />
      </el-form-item>
      
      <el-form-item label="补货数量" prop="quantity">
        <el-input 
          v-model.number="stockForm.quantity" 
          placeholder="请输入要增加的库存数量" 
          type="number"
          min="1"
        />
      </el-form-item>
    </el-form>

    <!-- 保存按钮、关闭按钮 -->
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">确认补货</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { getStocks, replenishStock } from "@/api/StockApi.js";
import { Search, Refresh, Plus } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

// 从 localStorage 获取用户角色信息
const getUserInfo = () => {
  try {
    const user = localStorage.getItem('user');
    if (user) {
      return JSON.parse(user);
    }
  } catch (e) {
    console.error('解析用户信息失败:', e);
  }
  return null;
};

// 判断是否为管理员（roleId === 1 表示管理员）
const isAdmin = computed(() => {
  const user = getUserInfo();
  return user && user.roleId === 1;
});

// 搜索表单
const searchForm = ref({
  productId: null,
  stock: null,
  page: 1,
  pageSize: 10,
});

let total = ref(0);
const tableData = ref([]);

// 对话框相关
let dialogVisible = ref(false);
let stockForm = ref({
  productId: null,
  productName: '',
  quantity: 1,  // 补货数量（要增加的数量）
  existingId: null  // 用于标记是否为编辑模式
});
let stockFormRef = ref(null);

// 表单校验规则（动态）
const stockFormRules = computed(() => {
  const rules = {
    productId: [
      { required: true, message: '请输入商品ID', trigger: 'blur' },
      { type: 'number', message: '商品ID必须为数字', trigger: 'blur' }
    ],
    quantity: [
      { required: true, message: '请输入补货数量', trigger: 'blur' },
      { type: 'number', message: '补货数量必须为数字', trigger: 'blur' },
      { validator: (rule, value, callback) => {
          if (value < 1) {
            callback(new Error('补货数量必须大于0'));
          } else {
            callback();
          }
        }, trigger: 'blur' }
    ]
  };
  
  // 新商品补货时，商品名称为必填
  if (!stockForm.value.existingId) {
    rules.productName = [
      { required: true, message: '请输入商品名称', trigger: 'blur' }
    ];
  }
  
  return rules;
});

// 重置搜索条件
const handleReset = () => {
  searchForm.value = {
    productId: null,
    stock: null,
    page: 1,
    pageSize: 10,
  };
  findStocks();
};

// 查询库存列表
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

// 新增补货
const handleAdd = () => {
  dialogVisible.value = true;
  stockForm.value = {
    productId: null,
    productName: '',
    quantity: 1,
    existingId: null
  };
};

// 补货（编辑）
const handleEdit = (row) => {
  dialogVisible.value = true;
  // 克隆对象，并标记为编辑模式
  stockForm.value = {
    productId: row.productId,
    productName: row.productName || '',
    quantity: 1,  // 默认补货数量为1
    existingId: row.productId  // 标记为编辑模式，商品ID不可修改
  };
};

// 保存（执行补货）
const handleSave = () => {
  // 判断校验是否通过
  stockFormRef.value.validate().then(() => {
    // 构建补货请求参数
    const replenishData = {
      items: [
        {
          productId: stockForm.value.productId,
          productName: stockForm.value.productName,
          quantity: stockForm.value.quantity
        }
      ]
    };
    
    // 发送补货请求
    replenishStock(replenishData).then((resp) => {
      if (resp.code === 200) {
        ElMessage.success(resp.msg || '补货成功');
        findStocks();
        dialogVisible.value = false;
      } else {
        ElMessage.error(resp.msg || '补货失败');
      }
    }).catch((error) => {
      console.error('补货失败:', error);
      ElMessage.error('补货失败：' + (error.message || '未知错误'));
    });
  }).catch(() => {
    // 校验失败
    ElMessage.warning('请检查表单输入');
  });
};

// 初始化加载数据
findStocks();
</script>

<style scoped>
.mgt-4 {
  margin-top: 16px;
}

.mgb-4 {
  margin-bottom: 16px;
}
</style>

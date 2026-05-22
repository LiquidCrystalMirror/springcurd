<template>
  <el-form :model="searchForm" class="search-form">
    <el-row :gutter="10">
      <el-col :span="6">
        <el-form-item label="订单号">
          <el-input v-model="searchForm.orderNo" placeholder="请输入订单号" clearable />
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item label="平台ID">
          <el-input v-model="searchForm.platformId" placeholder="请输入平台ID" clearable />
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item label="商品ID">
          <el-input v-model="searchForm.productId" placeholder="请输入商品ID" clearable />
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="已取消" :value="0" />
            <el-option label="已完成" :value="1" />
            <el-option label="已回滚" :value="2" />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>
    <el-row>
      <el-col :span="6">
        <el-form-item>
          <el-button type="primary" @click="findOrders">
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

  <el-table :data="tableData" stripe border style="width: 100%">
    <el-table-column prop="id" label="ID" width="180" />
    <el-table-column prop="orderNo" label="订单号" width="200" />
    <el-table-column prop="platformId" label="平台ID" width="150" />
    <el-table-column prop="productId" label="商品ID" width="120" />
    <el-table-column prop="quantity" label="数量" width="100" />
    <el-table-column prop="status" label="状态" width="120">
      <template #default="scope">
        {{ getStatusText(scope.row.status) }}
      </template>
    </el-table-column>
    <el-table-column prop="createTime" label="创建时间" width="180" />
    <el-table-column prop="updateTime" label="更新时间" width="180" />
  </el-table>

  <el-pagination
      class="mgt-4"
      v-model:current-page="searchForm.page"
      v-model:page-size="searchForm.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      @size-change="findOrders"
      @current-change="findOrders"
  />
</template>

<script setup>
import { ref } from "vue";
import { getOrders } from "@/api/OrdersApi.js";
import { Search, Refresh } from '@element-plus/icons-vue';

// 搜索表单
const searchForm = ref({
  orderNo: '',
  platformId: '',
  productId: null,
  status: null,
  page: 1,
  pageSize: 10,
});

let total = ref(0);
const tableData = ref([]);

// 获取状态文本
const getStatusText = (status) => {
  const statusMap = {
    0: '已取消',
    1: '已完成',
    2: '已回滚'
  };
  return statusMap[status] || '未知';
};

// 重置搜索条件
const handleReset = () => {
  searchForm.value = {
    orderNo: '',
    platformId: '',
    productId: null,
    status: null,
    page: 1,
    pageSize: 10,
  };
  findOrders();
};

// 查询订单列表
const findOrders = () => {
  getOrders(searchForm.value).then((resp) => {
    if (resp.data && resp.data.list) {
      tableData.value = resp.data.list;
      total.value = resp.data.total || 0;
    } else {
      tableData.value = [];
      total.value = 0;
    }
  }).catch((error) => {
    console.error('查询订单失败:', error);
    tableData.value = [];
    total.value = 0;
  });
};

// 初始化加载数据
findOrders();
</script>

<style scoped>
.mgt-4 {
  margin-top: 16px;
}
</style>

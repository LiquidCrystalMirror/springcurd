<template>
  <div class="page-container">
    <!-- 搜索栏（固定顶部） -->
    <div class="search-bar">
    <el-form :model="searchForm" inline>
      <el-form-item label="姓名">
        <el-input v-model="searchForm.name" placeholder="请输入姓名" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="请选择状态" clearable multiple>
          <template v-for="item in statusOptions" :key="item.value">
            <el-option :label="item.label" :value="item.value" />
          </template>
        </el-select>
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="searchForm.roleId" placeholder="请选择角色" clearable multiple>
          <template v-for="item in staffRoleOptions" :key="item.value">
            <el-option :label="item.label" :value="item.value" />
          </template>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="findStaff">
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

  <div class="table-card">
    <el-button v-if="canManage" type="primary" @click="handleAdd" class="mgb-4">
    <el-icon><Plus /></el-icon>
    新增员工
  </el-button>

  <el-table :data="tableData" stripe border style="width: 100%">
    <el-table-column prop="name" label="姓名" min-width="9%" />
    <el-table-column prop="roleId" label="角色" min-width="9%">
      <template #default="scope">
        {{ getRoleText(scope.row.roleId) }}
      </template>
    </el-table-column>
    <el-table-column prop="status" label="状态" min-width="8%">
      <template #default="scope">
        <el-tag :type="getStatusTagType(scope.row.status)" size="small">
          {{ getStatusText(scope.row.status) }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="creatorId" label="创建人ID" min-width="9%" />
    <el-table-column prop="approverId" label="审核人ID" min-width="9%" />
    <el-table-column prop="createdAt" label="创建时间" min-width="14%" />
    <el-table-column prop="approveTime" label="审核时间" min-width="14%" />

    <el-table-column label="操作" min-width="21%" v-if="canManage || canApprove">
      <template #default="scope">
        <!-- 编辑/删除：人事+管理员 -->
        <template v-if="canManage">
          <el-button size="small" @click="handleEdit(scope.row)">
            编辑
          </el-button>
          <el-popconfirm title="确定要删除吗？" @confirm="handleDelete(scope.row.id)">
            <template #reference>
              <el-button size="small" type="danger">
                删除
              </el-button>
            </template>
          </el-popconfirm>
        </template>

        <!-- 审核：监管+管理员，仅待审核状态显示 -->
        <template v-if="canApprove && scope.row.status === UserStatus.PENDING_APPROVAL">
          <el-button size="small" type="success" @click="handleApprove(scope.row, true)">
            通过
          </el-button>
          <el-button size="small" type="danger" @click="handleApprove(scope.row, false)">
            拒绝
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
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="findStaff"
        @current-change="findStaff"
    />
  </div>

  <!-- 新增/编辑对话框 -->
  <el-dialog :title="dialogTitle" v-model="dialogVisible">
    <el-form label-width="100px" :model="userForm" :rules="userFormRules" ref="userFormRef">
      <!-- 新增时：可填ID -->
      <el-form-item label="员工ID" prop="id" v-if="!userForm.originalId">
        <el-input v-model.number="userForm.id" type="number" placeholder="请输入员工ID" />
      </el-form-item>

      <el-form-item label="姓名" prop="name">
        <el-input placeholder="请输入姓名" v-model="userForm.name" :readonly="!!userForm.originalId" />
      </el-form-item>

      <!-- 新增时：可填密码 -->
      <el-form-item label="密码" prop="password" v-if="!userForm.originalId">
        <el-input placeholder="请输入密码" v-model="userForm.password" type="password" show-password />
      </el-form-item>

      <!-- 编辑时：可改状态 -->
      <el-form-item label="状态" prop="status" v-if="userForm.originalId">
        <el-select v-model="userForm.status" placeholder="请选择状态">
          <template v-for="item in statusOptions" :key="item.value">
            <el-option :label="item.label" :value="item.value" />
          </template>
        </el-select>
      </el-form-item>

      <!-- 角色：新增必选，编辑可选 -->
      <el-form-item label="角色" prop="roleId">
        <el-select v-model="userForm.roleId" placeholder="请选择角色">
          <template v-for="item in staffRoleOptions" :key="item.value">
            <el-option :label="item.label" :value="item.value" />
          </template>
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
  </div>
</template>


<script setup>

import { ref, computed } from "vue";
import { findStaff as apiFindStaff, createStaff, approveStaff, updateStaff, deleteStaff } from "@/api/UserApi.js";
import { ElMessage } from "element-plus";
import { Search, Refresh, Plus } from '@element-plus/icons-vue';
import UserStatus, { getStatusText, getStatusOptions, getStatusTagType } from "@/enum/user/UserStatus.js";
import Role, { getRoleOptions, getRoleText } from "@/enum/user/Role.js";
import authService from "@/service/AuthService.js";

// ==================== 搜索表单 ====================
const searchForm = ref({
  name: '',
  status: [],
  roleId: [],
  page: 1,
  pageSize: 10,
});

let total = ref(0);
const tableData = ref([]);

// ==================== 对话框 ====================
let dialogVisible = ref(false);
let userForm = ref({
  id: null,
  originalId: null,    // 编辑时存原始ID
  name: '',
  password: '',
  status: UserStatus.PENDING_APPROVAL,
  roleId: Role.REPLENISHER
});
let userFormRef = ref(null);

// 对话框标题
const dialogTitle = computed(() => userForm.value.originalId ? '编辑员工' : '新增员工')

// 表单校验规则
const userFormRules = computed(() => {
  const rules = {
    name: [
      { required: true, message: '请输入姓名', trigger: 'blur' },
      { min: 2, max: 20, message: '姓名长度在2到20个字符', trigger: 'blur' }
    ],
    roleId: [
      { required: true, message: '请选择角色', trigger: 'change' }
    ]
  };
  // 新增时额外校验
  if (!userForm.value.originalId) {
    rules.id = [
      { required: true, message: '请输入员工ID', trigger: 'blur' },
      { type: 'number', message: 'ID必须为数字', trigger: 'blur' }
    ];
    rules.password = [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, max: 20, message: '密码长度在6到20个字符', trigger: 'blur' }
    ];
  }
  return rules;
});

// ==================== 权限判断 ====================
const canManage = computed(() => authService.isHR())
const canApprove = computed(() => authService.isSupervisor())

// ==================== 下拉选项 ====================
const statusOptions = computed(() => getStatusOptions());
const staffRoleOptions = computed(() => getRoleOptions('staff'));

// ==================== 方法 ====================

// 重置
const handleReset = () => {
  searchForm.value = {
    name: '',
    status: [],
    roleId: [],
    page: 1,
    pageSize: 10,
  };
  findStaff();
};

// 查询员工列表
const findStaff = () => {
  apiFindStaff(searchForm.value).then((resp) => {
    if (resp.data && Array.isArray(resp.data.list)) {
      tableData.value = resp.data.list;
      total.value = resp.data.total || 0;
    } else if (Array.isArray(resp.data)) {
      tableData.value = resp.data;
      total.value = resp.data.length || 0;
    } else {
      console.error('Unexpected response format:', resp);
      tableData.value = [];
      total.value = 0;
    }
  }).catch((err) => {
    console.error('查询员工失败:', err);
    tableData.value = [];
    total.value = 0;
  });
};
// 新增员工
const handleAdd = () => {
  dialogVisible.value = true;
  userForm.value = {
    id: null,
    originalId: null,
    name: '',
    password: '',
    status: UserStatus.PENDING_APPROVAL,
    roleId: Role.REPLENISHER
  };
};

// 编辑员工
const handleEdit = (row) => {
  dialogVisible.value = true;
  userForm.value = {
    id: row.id,
    originalId: row.id,
    name: row.name || '',
    password: '',
    status: row.status,
    roleId: row.roleId
  };
};

// 保存
const handleSave = () => {
  userFormRef.value.validate().then(() => {
    if (userForm.value.originalId) {
      // 编辑
      updateStaff(userForm.value.originalId, {
        name: userForm.value.name,
        status: userForm.value.status,
        roleId: userForm.value.roleId
      }).then(() => {
        ElMessage.success('更新成功');
        dialogVisible.value = false;
        findStaff();
      }).catch((err) => {
        ElMessage.error('更新失败：' + (err.msg || err.message || '未知错误'));
      });
    } else {
      // 新增
      createStaff({
        id: userForm.value.id,
        name: userForm.value.name,
        password: userForm.value.password,
        roleId: userForm.value.roleId
      }).then(() => {
        ElMessage.success('创建成功，员工状态为待审核');
        dialogVisible.value = false;
        findStaff();
      }).catch((err) => {
        ElMessage.error('创建失败：' + (err.msg || err.message || '未知错误'));
      });
    }
  }).catch(() => {
    ElMessage.warning('请检查表单输入');
  });
};

// 删除
const handleDelete = (id) => {
  deleteStaff(id).then((resp) => {
    ElMessage.success(resp.msg || '删除成功');
    findStaff();
  }).catch((err) => {
    ElMessage.error('删除失败：' + (err.msg || err.message || '未知错误'));
  });
};

// 审核（通过/拒绝）
const handleApprove = (row, approved) => {
  const actionText = approved ? '通过' : '拒绝';
  approveStaff(row.id, { approved }).then(() => {
    ElMessage.success('审核' + actionText + '成功');
    findStaff();
  }).catch((err) => {
    ElMessage.error('审核失败：' + (err.msg || err.message || '未知错误'));
  });
};

// 初始化加载
findStaff();
</script>


<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: linear-gradient(180deg, #f8f9fc 0%, #eef1f6 100%);
}

.search-bar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  padding: 12px 20px 0;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.table-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 5px rgba(0,0,0,0.04);
  margin: 1% 1.5% 0;
  padding: 12px 16px 0;
  overflow: hidden;
  flex: 1;
}

.pagination-wrap {
  position: sticky;
  bottom: 0;
  background: #fff;
  padding: 8px 20px;
  border-top: 1px solid #f0f0f0;
  z-index: 50;
}

.mgb-4 {
  margin-bottom: 12px;
}
</style>

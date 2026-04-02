<template>

  <el-form  :model="searchForm" class="search-form">

    <el-row :gutter="10">
      <el-col :span="6">
        <el-form-item label="姓名">
          <el-input v-model="searchForm.name" placeholder="请输入姓名" clearable />
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable multiple>
            <template v-for="item in statusOptions" :key="item.value">
              <el-option
                  :label="item.label"
                  :value="item.value"
              />
            </template>
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item>
          <el-button type="primary" @click="findUser">
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

  <el-button type="primary" @click="handleAdd" class="mgb-4">
    <el-icon><Plus /></el-icon>
    新增用户
  </el-button>

  <el-table :data="tableData" stripe   border style="width: 100%">
    <el-table-column prop="name" label="姓名" width="180" />
    <el-table-column prop="money" label="余额" width="180" />
    <el-table-column prop="status" label="状态">
      <template #default="scope">
        {{ getStatusText(scope.row.status) }}
      </template>
    </el-table-column>


    <el-table-column label="操作">
      <template #default="scope">
        <el-button size="small" @click="handleEdit(scope.row)" :disabled="scope.row.status===DISABLED">
          编辑
        </el-button>

        <el-popconfirm title="确定要删除吗？"   @confirm="handleDelete(scope.row.id)">
          <template #reference>
            <el-button size="small" type="danger" :disabled="scope.row.status===DISABLED">
              删除
            </el-button>
          </template>
        </el-popconfirm>
      </template>
    </el-table-column>
  </el-table>


  <el-pagination
      class="mgt-4"
      v-model:current-page="searchForm.page"
      v-model:page-size="searchForm.pageSize"
      :page-sizes="[1, 2, 10, 200]"
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      @size-change="findUser"
      @current-change="findUser"
  />

  <!--对话框-->
  <el-dialog :title="userForm.id?'编辑用户':'新增用户'" v-model="dialogVisible">
    <el-form label-width="80px" :model="userForm" :rules="userFormRules" ref="userFormRef">
      <el-form-item label="姓名" prop="name">
        <el-input placeholder="请输入姓名" v-model="userForm.name" :readonly="!!userForm.id"/>
      </el-form-item>
      <el-form-item label="余额" prop="money">
        <el-input-number placeholder="请输入余额" v-model="userForm.money" :precision="0" :min="0"/>
      </el-form-item>

      <!--修改的时候可以编辑状态-->
      <el-form-item label="状态" prop="status" v-if="userForm.id">
        <el-select v-model="userForm.status" placeholder="请选择状态">
          <template v-for="item in statusOptions" :key="item.value">
            <el-option
                :label="item.label"
                :value="item.value"
            />
          </template>
        </el-select>
      </el-form-item>
    </el-form>

    <!--保存按钮、关闭按钮-->
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>


<script setup>


import {ref, computed} from "vue";

import * as userAPI from "@/api/UserApi.js";
import {ElMessage} from "element-plus";
import { Search, Refresh, Plus } from '@element-plus/icons-vue';
import UserStatus, { getStatusText, getStatusOptions } from "@/enum/user/UserStatus.js";



const searchForm = ref({
  name: '',
  status: [],


  page: 1,
  pageSize: 2,
});

let total = ref(0);
const tableData = ref([]);

let dialogVisible = ref(false);
let userForm = ref({
  name: '',
  money: 0
});
let userFormRef = ref(null);
let userFormRules = ref({

})

// 状态选项
const statusOptions = computed(() => getStatusOptions());

// 获取状态文本（直接使用导入的函数）

// 禁用状态常量
const DISABLED = UserStatus.DISABLED;

//**************************************方法开始****************************************

const handleReset=()=>{
  searchForm.value = {
    name: '',
    status: []
  };

  findUser();
};




const findUser=()=>{
  userAPI.findUser(searchForm.value).then((resp)=>{
    if (resp.data && Array.isArray(resp.data.list)) {
      // 如果是分页响应（包含 list 数组）
      tableData.value = resp.data.list;
      total.value = resp.data.total || 0;
    } else if (Array.isArray(resp.data)) {
      // 如果是直接返回数组
      tableData.value = resp.data;
      total.value = resp.length || 0;
    } else {
      // 异常情况
      console.error('Unexpected response format:', resp);
      tableData.value = [];
      total.value = 0;
    }
  })
}

const handleAdd=()=>{
  dialogVisible.value = true;


  userForm.value = {
    name: '',
    money: 0,
    status: UserStatus.ENABLED,
  };
}

const handleEdit=(row)=>{
  dialogVisible.value = true;
  //克隆对象
  userForm.value = JSON.parse(JSON.stringify(row));
}

const handleSave=()=>{
  //判断校验是否通过
  userFormRef.value.validate().then(()=>{
    //判断添加、还是编辑
    if(userForm.value.id){
      userAPI.updateUser(userForm.value.id,userForm.value).then((resp)=>{
        findUser();
      })
    }else{
      //发送请求
      userAPI.addUser(userForm.value).then((resp)=>{
        userForm.value = {
          name: '',
          money: 0
        };
        findUser();
      })
    }
    dialogVisible.value = false;
  })
}

const handleDelete=(id)=>{
  userAPI.deleteUser(id).then((resp)=>{//{"code":500}
    ElMessage.success(resp.msg);
    //重新加载数据
    findUser();
  })
}



findUser();
//**************************************方法结束****************************************
</script>


<style scoped>

</style>
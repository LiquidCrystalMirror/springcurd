# API 接口测试用例

## 基础信息

- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json`
- **认证方式**: 已加入白名单，无需 Token

---

## 一、订单管理接口 (OrderController)

### 1.1 创建订单 - 成功场景

**接口信息**
- **URL**: `POST /orders/add`
- **描述**: 提交订单并原子性扣减 Redis 库存

**请求体**
```json
{
  "orderNo": "ORD20260419001",
  "platformId": "PLATFORM_A",
  "items": [
    {
      "productId": 1,
      "quantity": 5
    },
    {
      "productId": 2,
      "quantity": 3
    }
  ]
}
```

**预期响应（成功）**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderNo": "ORD20260419001",
    "platformId": "PLATFORM_A",
    "operations": {
      "1": 5,
      "2": 3
    }
  }
}
```

**验证步骤**
1. 在 Redis Insight 中执行：`GET product:stock:1`，应该减少 5
2. 执行：`GET product:stock:2`，应该减少 3
3. 执行：`GET biz:idempotent:deduct:ORD20260419001`，应该返回 "success"
4. 等待 2-4 秒后检查数据库 `order_detail` 表是否有记录

---

### 1.2 创建订单 - 库存不足

**请求体**
```json
{
  "orderNo": "ORD20260419002",
  "platformId": "PLATFORM_A",
  "items": [
    {
      "productId": 1,
      "quantity": 9999
    }
  ]
}
```

**预期响应（失败）**
```json
{
  "code": 400,
  "message": "库存不足",
  "data": null
}
```

---

### 1.3 创建订单 - 参数校验失败（订单号为空）

**请求体**
```json
{
  "orderNo": "",
  "platformId": "PLATFORM_A",
  "items": [
    {
      "productId": 1,
      "quantity": 5
    }
  ]
}
```

**预期响应**
```json
{
  "code": 400,
  "message": "订单号不能为空",
  "data": null
}
```

---

### 1.4 取消订单 - 成功场景

**前提条件**
- 需要先调用"1.1 创建订单"接口
- 等待 2-4 秒让 MQ 消费者将订单明细持久化到数据库

**请求体**
```json
{
  "orderNo": "ORD20260419001",
  "platformId": "PLATFORM_A",
  "items": [
    {
      "productId": 1,
      "quantity": 5
    }
  ]
}
```

**预期响应（成功）**
```json
{
  "code": 200,
  "message": "success",
  "data": "商品[1]取消成功\n"
}
```

**验证步骤**
1. 在 Redis Insight 中执行：`GET product:stock:1`，库存应该恢复
2. 执行：`GET cancel:idempotent:ORD20260419001`，应该返回 "success"
3. 检查数据库 `order_detail` 表中该订单状态是否变为 0

---

### 1.5 取消订单 - 订单不存在

**请求体**
```json
{
  "orderNo": "ORD_NOT_EXIST",
  "platformId": "PLATFORM_A",
  "items": [
    {
      "productId": 1,
      "quantity": 5
    }
  ]
}
```

**预期响应**
```json
{
  "code": 200,
  "message": "success",
  "data": "商品[1]订单未找到\n"
}
```

---

### 1.6 取消订单 - 部分成功部分失败

**前提条件**
- 订单中某些商品存在，某些不存在

**请求体**
```json
{
  "orderNo": "ORD20260419001",
  "platformId": "PLATFORM_A",
  "items": [
    {
      "productId": 1,
      "quantity": 5
    },
    {
      "productId": 999,
      "quantity": 10
    }
  ]
}
```

**预期响应**
```json
{
  "code": 200,
  "message": "success",
  "data": "商品[1]取消成功\n商品[999]订单未找到\n"
}
```

---

## 二、库存管理接口 (StockManageController)

### 2.1 补货 - 成功场景（新商品自动初始化）

**接口信息**
- **URL**: `POST /admin/stock/replenish`
- **描述**: 批量增加库存，不存在的商品会自动初始化

**请求体**
```json
{
  "replenishNo": "REP20260419001",
  "items": [
    {
      "productId": 1,
      "quantity": 100
    },
    {
      "productId": 2,
      "quantity": 50
    },
    {
      "productId": 999,
      "quantity": 200
    }
  ]
}
```

**预期响应（成功）**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "replenishNo": "REP20260419001",
    "operations": {
      "1": 100,
      "2": 50,
      "999": 200
    }
  }
}
```

**验证步骤**
1. 在 Redis Insight 中执行：
   - `GET product:stock:1` → 应该返回 100
   - `GET product:stock:2` → 应该返回 50
   - `GET product:stock:999` → 应该返回 200（自动初始化）
2. 执行：`GET replenish:idempotent:REP20260419001` → 应该返回 "success"
3. 检查数据库 `stock_replenish_log` 表是否有记录

---

### 2.2 补货 - 幂等性测试（重复提交）

**前提条件**
- 先执行一次"2.1 补货"接口

**请求体**（相同的 replenishNo）
```json
{
  "replenishNo": "REP20260419001",
  "items": [
    {
      "productId": 1,
      "quantity": 100
    }
  ]
}
```

**预期响应**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "replenishNo": "REP20260419001",
    "operations": {
      "1": 100
    }
  }
}
```

**验证步骤**
1. 在 Redis Insight 中执行：`GET product:stock:1`
2. **库存值应该不变**（仍然是 100，不会变成 200）
3. 说明幂等性保护生效，不会重复增加库存

---

### 2.3 补货 - 参数校验失败（补货单号为空）

**请求体**
```json
{
  "replenishNo": "",
  "items": [
    {
      "productId": 1,
      "quantity": 100
    }
  ]
}
```

**预期响应**
```json
{
  "code": 400,
  "message": "补货单号不能为空",
  "data": null
}
```

---

### 2.4 补货 - 参数校验失败（数量为负数）

**请求体**
```json
{
  "replenishNo": "REP20260419002",
  "items": [
    {
      "productId": 1,
      "quantity": -10
    }
  ]
}
```

**预期响应**
```json
{
  "code": 400,
  "message": "补货数量必须大于0",
  "data": null
}
```

---

### 2.5 补货 - 参数校验失败（空商品列表）

**请求体**
```json
{
  "replenishNo": "REP20260419003",
  "items": []
}
```

**预期响应**
```json
{
  "code": 400,
  "message": "补货商品列表不能为空",
  "data": null
}
```

---

## 三、完整测试流程建议

### 推荐测试顺序

#### 第一阶段：基础功能测试
1. ✅ 执行 **2.1 补货** - 为商品初始化库存
2. ✅ 执行 **1.1 创建订单** - 扣减库存
3. ✅ 等待 2-4 秒（MQ 消费者处理）
4. ✅ 执行 **1.4 取消订单** - 恢复库存

#### 第二阶段：异常场景测试
5. ✅ 执行 **1.2 创建订单（库存不足）**
6. ✅ 执行 **1.3 创建订单（参数校验）**
7. ✅ 执行 **1.5 取消订单（订单不存在）**
8. ✅ 执行 **1.6 取消订单（部分成功）**

#### 第三阶段：边界测试
9. ✅ 执行 **2.2 补货（幂等性）**
10. ✅ 执行 **2.3-2.5 补货（参数校验）**

---

## 四、Redis Insight 常用验证命令

### 库存查询
```bash
# 查看商品库存
GET product:stock:1
GET product:stock:2
GET product:stock:999

# 查看所有库存 key
KEYS product:stock:*
```

### 幂等性标记查询
```bash
# 订单创建幂等性
GET biz:idempotent:deduct:ORD20260419001

# 订单取消幂等性
GET cancel:idempotent:ORD20260419001

# 补货幂等性
GET replenish:idempotent:REP20260419001
```

### 队列监控
```bash
# 查看扣减队列长度
LLEN async:queue:deduct

# 查看队列中的消息
LRANGE async:queue:deduct 0 -1

# 查看增加队列长度
LLEN async:queue:add
```

### 快照查询
```bash
# 查看所有快照
KEYS biz:snapshot:*

# 查看具体快照内容
GET biz:snapshot:deduct:ORD20260419001
```

### 数据清理（仅开发环境）
```bash
# 清空当前数据库
FLUSHDB

# 删除特定 key
DEL product:stock:1
DEL biz:idempotent:deduct:ORD20260419001
```

---

## 五、常见问题排查

### 问题 1：创建订单后数据库没有记录
**原因**: MQ 消费者有延迟（fixedDelay = 2000ms）  
**解决**: 等待 2-4 秒后再查询数据库

### 问题 2：取消订单提示"订单未找到"
**原因**: 订单明细还未持久化到数据库  
**解决**: 确保先创建了订单，并等待足够时间

### 问题 3：补货时 Redis 连接失败
**原因**: Redis 服务未启动  
**解决**: 
```powershell
# 检查 Redis 是否运行
redis-cli ping
# 应该返回 PONG
```

### 问题 4：Lua 脚本执行失败
**原因**: Lua 脚本文件缺失或语法错误  
**解决**: 检查 `src/main/resources/lua/` 目录下的 5 个脚本文件是否存在

---

## 六、日志查看位置

应用启动后，日志文件位于项目根目录的 `logs/` 文件夹：

```
logs/
├── order/
│   ├── redis/
│   │   ├── create/order_redis_create.log
│   │   └── cancel/order_redis_cancel.log
│   └── mysql/
│       ├── create/order_mysql_create.log
│       └── cancel/order_mysql_cancel.log
└── replenish/
    ├── redis/replenish_redis.log
    └── mysql/replenish_mysql.log
```

每个日志文件按天滚动，单个文件最大 100MB，保留 30 天。

---

## 七、注意事项

1. **白名单配置**: 这两个控制器已加入白名单，无需携带 Token
2. **幂等性保护**: 相同的 `orderNo` 或 `replenishNo` 多次提交只会生效一次
3. **异步处理**: 订单明细通过 MQ 异步写入数据库，有 2-4 秒延迟
4. **数据一致性**: Redis 操作是原子的，数据库操作有重试机制
5. **测试数据清理**: 每次重新测试前，建议在 Redis Insight 中执行 `FLUSHDB`

---

**文档版本**: v1.0  
**更新时间**: 2026-04-19  
**适用环境**: 开发环境

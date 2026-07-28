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

### 1.7 创建订单 - 幂等性测试（重复订单号）

**说明**: 验证订单幂等性保护，重复订单号应该被拦截并返回明确提示

**操作步骤**
1. 第一次创建订单
   ```bash
   curl -X POST http://localhost:8080/orders/add \
     -H "Content-Type: application/json" \
     -d '{"orderNo":"ORD_IDEMPOTENT_TEST","platformId":"PLATFORM_A","items":[{"productId":1,"quantity":5}]}'
   ```
   
2. 立即第二次提交相同订单号
   ```bash
   curl -X POST http://localhost:8080/orders/add \
     -H "Content-Type: application/json" \
     -d '{"orderNo":"ORD_IDEMPOTENT_TEST","platformId":"PLATFORM_A","items":[{"productId":1,"quantity":5}]}'
   ```

**预期响应**

第一次请求：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderNo": "ORD_IDEMPOTENT_TEST",
    "platformId": "PLATFORM_A",
    "operations": {
      "1": 5
    }
  }
}
```

第二次请求（幂等拦截）：
```json
{
  "code": 409,
  "message": "订单已存在，请勿重复提交",
  "data": null
}
```

**验证步骤**
1. 检查Redis库存只扣减了一次：
   ```bash
   redis-cli GET product:stock:1
   # 假设原来是100，现在应该是95，而不是90
   ```

2. 检查数据库幂等记录：
   ```sql
   SELECT * FROM biz_idempotent WHERE biz_no = 'ORD_IDEMPOTENT_TEST';
   -- 应该只有一条记录，status=1
   ```

3. 检查订单明细表：
   ```sql
   SELECT * FROM order_detail WHERE order_no = 'ORD_IDEMPOTENT_TEST';
   -- 应该只有一条记录
   ```

4. 查看日志：
   ```bash
   tail -f logs/order/redis/create/order_redis_create.log
   # 应该看到："订单已存在，幂等性拦截，请勿重复提交"
   ```

---

### 1.8 创建订单 - 数据库幂等表验证

**说明**: 验证即使Redis幂等key过期，数据库层面仍能拦截重复请求

**操作步骤**
1. 创建一个订单
2. 等待Redis幂等key过期（或手动删除）：
   ```bash
   redis-cli DEL biz:idempotent:deduct:ORD_DB_IDEMPOTENT
   ```
3. 再次提交相同订单号

**预期结果**
- 第二次请求会被数据库幂等表拦截
- 返回 `409 订单已存在，请勿重复提交`
- 库存不会重复扣减

**验证SQL**
```sql
-- 查询幂等记录
SELECT * FROM biz_idempotent WHERE biz_no = 'ORD_DB_IDEMPOTENT';

-- 检查是否有孤儿数据（有库存扣减但无订单记录）
SELECT ps.* FROM product_stock ps
LEFT JOIN order_detail od ON ps.product_id = od.product_id 
    AND od.order_no = 'ORD_DB_IDEMPOTENT'
WHERE od.id IS NULL;
-- 应该返回空结果集
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

### 2.6 补货 - 数据库幂等性测试

**说明**: 验证补货操作的数据库幂等性保护

**操作步骤**
1. 先执行一次补货
2. 删除Redis幂等key：
   ```bash
   redis-cli DEL replenish:idempotent:REP20260419001
   ```
3. 再次提交相同补货单号

**预期结果**
- 数据库幂等表会拦截重复请求
- 库存不会重复增加

**验证SQL**
```sql
SELECT * FROM biz_idempotent 
WHERE biz_no = 'REP20260419001' AND op_type = 'add';
```

---

## 三、回滚功能测试

### 3.1 回滚队列消费验证

**前提条件**
- 需要先创建一个订单并取消
- 观察回滚队列的消费情况

**操作步骤**
1. 创建订单
   ```json
   POST /orders/add
   {
     "orderNo": "ROLLBACK_TEST_001",
     "platformId": "PLATFORM_A",
     "items": [{"productId": 1, "quantity": 10}]
   }
   ```

2. 等待2-4秒让订单明细持久化

3. 取消订单
   ```json
   POST /orders/cancel
   {
     "orderNo": "ROLLBACK_TEST_001",
     "platformId": "PLATFORM_A",
     "items": [{"productId": 1, "quantity": 10}]
   }
   ```

4. 等待2-4秒让回滚队列消费

**验证步骤**

1. **检查日志**：
   ```
   # 应该看到以下日志序列：
   [order.mysql] 开始执行回滚操作，恢复库存并更新订单状态
   [order.mysql] 恢复商品库存，productId=1, 原始库存=XXX
   [order.mysql] 回滚操作更新订单状态完成，影响行数: 1
   [order.mysql] 回滚操作完成，订单状态已更新为已回滚
   ```

2. **检查Redis库存**：
   ```bash
   redis-cli GET product:stock:1
   # 应该恢复到订单创建前的值
   ```

3. **检查MySQL库存备份表**：
   ```sql
   SELECT product_id, stock, version 
   FROM product_stock 
   WHERE product_id = 1;
   -- stock 应该与Redis一致
   ```

4. **检查订单状态**：
   ```sql
   SELECT order_no, product_id, quantity, status 
   FROM order_detail 
   WHERE order_no = 'ROLLBACK_TEST_001';
   -- status 应该为 2（已回滚）
   ```

5. **检查数据一致性**：
   ```sql
   -- Redis和MySQL库存应该一致
   -- 订单状态应该是已回滚
   -- 不应该有孤儿数据
   ```

---

### 3.2 回滚消息格式验证

**说明**: 验证回滚队列消息是否包含完整的商品信息

**操作步骤**
1. 在Redis中查看回滚队列消息：
   ```bash
   redis-cli LRANGE async:queue:rollback 0 -1
   ```

**预期消息格式**
```json
{
  "bizNo": "ROLLBACK_TEST_001",
  "platformId": "PLATFORM_A",
  "items": [
    {
      "key": "product:stock:1",
      "quantity": 590  // 这是回滚前的原始库存值
    }
  ]
}
```

**关键点**
- ✅ 消息中包含 `items` 数组
- ✅ 每个item包含 `key` 和 `quantity`
- ✅ `quantity` 存储的是回滚前的原始库存值（不是扣减数量）

---

### 3.3 从快照恢复商品信息测试

**说明**: 测试当回滚消息中items为空时，能否从Redis快照恢复

**操作步骤**
1. 手动构造一个不包含items的回滚消息（仅用于测试）
2. 推送到回滚队列
3. 观察消费者是否能从快照加载

**预期日志**
```
[order.mysql] 回滚消息中没有商品信息，尝试从Redis快照读取
[order.mysql] 从快照加载商品信息成功，共X个商品
[order.mysql] 恢复商品库存，productId=X, 原始库存=XXX
```

**注意**: 此测试需要手动构造消息，一般场景下不需要测试

---

## 四、完整测试流程建议

#### 第一阶段：基础功能测试
1. ✅ 执行 **2.1 补货** - 为商品初始化库存
2. ✅ 执行 **1.1 创建订单** - 扣减库存
3. ✅ 等待 2-4 秒（MQ 消费者处理）
4. ✅ 执行 **1.4 取消订单** - 恢复库存
5. ✅ 等待 2-4 秒（回滚队列消费）
6. ✅ 执行 **3.1 回滚验证** - 确认数据一致性

#### 第二阶段：幂等性测试
7. ✅ 执行 **1.7 创建订单（重复订单号）** - 验证幂等性
8. ✅ 执行 **1.8 数据库幂等表验证** - 验证数据库层面保护
9. ✅ 执行 **2.2 补货（幂等性）**
10. ✅ 执行 **2.6 补货数据库幂等性**

#### 第三阶段：异常场景测试
11. ✅ 执行 **1.2 创建订单（库存不足）**
12. ✅ 执行 **1.3 创建订单（参数校验）**
13. ✅ 执行 **1.5 取消订单（订单不存在）**
14. ✅ 执行 **1.6 取消订单（部分成功）**

#### 第四阶段：边界测试
15. ✅ 执行 **2.3-2.5 补货（参数校验）**
16. ✅ 执行 **3.2 回滚消息格式验证**

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
# 订单创建幂等性（Redis）
GET biz:idempotent:deduct:ORD20260419001

# 订单取消幂等性
GET cancel:idempotent:ORD20260419001

# 补货幂等性（Redis）
GET replenish:idempotent:REP20260419001

# 回滚队列
LRANGE async:queue:rollback 0 -1
```

### 数据库幂等表查询
```sql
-- 查询某个订单的幂等记录
SELECT * FROM biz_idempotent 
WHERE biz_no = 'ORD20260419001';

-- 查询所有成功的订单创建记录
SELECT * FROM biz_idempotent 
WHERE op_type = 'deduct' AND status = 1
ORDER BY create_time DESC LIMIT 10;

-- 查询失败的记录（需要人工介入）
SELECT * FROM biz_idempotent 
WHERE status = 2;

-- 统计各状态的记录数
SELECT status, COUNT(*) as count 
FROM biz_idempotent 
GROUP BY status;
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

### 数据一致性检查
```sql
-- 检查是否有孤儿数据（有库存变化但无订单记录）
SELECT ps.* 
FROM product_stock ps
LEFT JOIN order_detail od ON ps.product_id = od.product_id 
    AND od.order_no = 'ORD20260419001'
WHERE od.id IS NULL;

-- 检查库存不一致（Redis vs MySQL）
-- 需要手动对比Redis值和MySQL值

-- 清理90天前的幂等记录
DELETE FROM biz_idempotent 
WHERE create_time < DATE_SUB(NOW(), INTERVAL 90 DAY);
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

### 问题 5：重复订单号导致库存被扣减但无订单记录
**原因**: Redis TTL过期，且没有数据库幂等表保护  
**解决**: 
1. 确保已执行 `biz_idempotent.sql` 创建幂等表
2. 检查日志中是否有"数据库幂等检查命中"的记录
3. 如果仍有问题，检查 `biz_idempotent` 表的唯一索引是否正常

### 问题 6：回滚后Redis库存恢复但MySQL未恢复
**原因**: 回滚消息格式不正确或消费者逻辑有误  
**解决**:
1. 检查回滚队列消息格式：`LRANGE async:queue:rollback 0 -1`
2. 确认消息中包含 `items` 数组
3. 检查日志中是否有"恢复商品库存"的记录
4. 如果items为空，检查是否能从快照加载

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

## 七、新增特性说明

### 7.1 数据库幂等表
- **表名**: `biz_idempotent`
- **作用**: 永久保存业务操作的幂等性状态，解决Redis TTL过期问题
- **状态码**: 0=处理中, 1=成功, 2=失败
- **优势**: 
  - 不会过期，永久有效
  - 支持查询历史操作记录
  - 利用联合唯一索引保证并发安全

### 7.2 增强的回滚逻辑
- **改进点**: 
  - 回滚时会同时恢复Redis和MySQL库存
  - 回滚消息包含完整的商品信息（key和原始库存值）
  - 支持从Redis快照恢复商品信息（备用方案）
- **验证方法**: 取消订单后检查MySQL的`product_stock`表是否恢复

### 7.3 优化的业务处理顺序
- **改进前**: 先扣库存 → 再插订单（可能导致孤儿数据）
- **改进后**: 先插订单 → 再扣库存（利用唯一索引做二次保护）
- **效果**: 即使重复请求，也不会产生"有库存扣减但无订单"的数据不一致

---

## 七、新增特性说明

### 7.1 数据库幂等表
- **表名**: `biz_idempotent`
- **作用**: 永久保存业务操作的幂等性状态，解决Redis TTL过期问题
- **状态码**: 0=处理中, 1=成功, 2=失败
- **优势**: 
  - 不会过期，永久有效
  - 支持查询历史操作记录
  - 利用联合唯一索引保证并发安全

### 7.2 增强的回滚逻辑
- **改进点**: 
  - 回滚时会同时恢复Redis和MySQL库存
  - 回滚消息包含完整的商品信息（key和原始库存值）
  - 支持从Redis快照恢复商品信息（备用方案）
- **验证方法**: 取消订单后检查MySQL的`product_stock`表是否恢复

### 7.3 优化的业务处理顺序
- **改进前**: 先扣库存 → 再插订单（可能导致孤儿数据）
- **改进后**: 先插订单 → 再扣库存（利用唯一索引做二次保护）
- **效果**: 即使重复请求，也不会产生"有库存扣减但无订单"的数据不一致

---

## 八、注意事项

1. **白名单配置**: 这两个控制器已加入白名单，无需携带 Token
2. **双重幂等保护**: Redis（快速）+ MySQL（持久），确保不会重复处理
3. **异步处理**: 订单明细通过 MQ 异步写入数据库，有 2-4 秒延迟
4. **数据一致性**: Redis 操作是原子的，数据库操作有重试机制和幂等保护
5. **回滚完整性**: 取消订单会同时恢复Redis和MySQL库存
6. **测试数据清理**: 每次重新测试前，建议在 Redis Insight 中执行 `FLUSHDB`
7. **幂等表维护**: 建议定期清理90天前的幂等记录，避免表过大

---

**文档版本**: v2.0  
**更新时间**: 2026-04-20  
**适用环境**: 开发环境  
**主要更新**: 
- 新增数据库幂等表支持
- 增强回滚逻辑（恢复MySQL库存）
- 优化业务处理顺序（先插订单再扣库存）
- 新增幂等性和回滚测试用例

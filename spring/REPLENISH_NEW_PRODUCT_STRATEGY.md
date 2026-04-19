# 补货系统 - 商品不存在处理策略

## 📋 问题背景

在补货系统中，如果遇到Redis或MySQL中不存在该商品的情况，应该如何处理？

---

## ✅ 已实施的解决方案：**自动初始化策略**

### 核心设计原则
> **补货系统的目的是增加库存，不应该因为商品不存在而失败**

---

## 🔧 具体实现

### 1. Redis层处理（Lua脚本）

**文件**: `lua/replenish.lua`

```lua
for i = 1, N do
    local before = redis.call('GET', KEYS[i])
    local qty = tonumber(ARGV[i])
    
    -- 如果key不存在，自动初始化为0
    if not before then
        redis.call('SET', KEYS[i], 0)  -- 创建key并设为0
        before = 0
    end
    
    local after = redis.call('INCRBY', KEYS[i], qty)  -- 增加库存
    result[KEYS[i]] = {before = tonumber(before), after = after}
end
```

**行为说明：**
- ✅ Redis中key不存在 → 自动创建并初始化为0
- ✅ 然后执行INCRBY增加库存
- ✅ 返回的before=0, after=补货数量

---

### 2. MySQL层处理（异步消费者）

**文件**: `StockPersistenceConsumer.java`

```java
private void updateProductStock(Long productId, int newStock) {
    ProductStock stock = productStockMapper.queryById(productId);
    
    if (stock == null) {
        // MySQL中记录不存在，自动创建
        StructuredLogger.info(REPLENISH_MYSQL, "SYSTEM", 
                "商品库存记录不存在，自动创建新记录，productId={}, stock={}", 
                productId, newStock);
        
        stock = new ProductStock();
        stock.setProductId(productId);
        stock.setStock(newStock);
        stock.setVersion(0);
        productStockMapper.insert(stock);
        
        StructuredLogger.info(REPLENISH_MYSQL, "SYSTEM", 
                "商品库存记录创建成功，productId={}", productId);
        return;
    }
    
    // 记录存在，执行更新...
}
```

**行为说明：**
- ✅ MySQL中记录不存在 → 自动INSERT新记录
- ✅ 设置初始库存为Redis中的当前值
- ✅ version设为0，启用乐观锁

---

## 📊 完整流程示例

### 场景：为新商品补货（Redis和MySQL都不存在）

#### 第1步：调用补货接口
```json
POST /admin/stock/replenish
{
  "replenishNo": "REP20240417001",
  "items": [
    {"productId": 9999, "quantity": 100}
  ]
}
```

#### 第2步：Redis处理（同步）
```
[replenish.redis] 开始执行补货操作，商品种类数=1
[replenish.redis] 调用Lua脚本执行批量库存增加
  → Lua检测到 product:stock:9999 不存在
  → 自动 SET product:stock:9999 0
  → 执行 INCRBY product:stock:9999 100
  → 返回 before=0, after=100
[replenish.redis] 补货Redis操作成功，商品种类数=1（不存在的商品已自动初始化）
```

**Redis状态：**
```
product:stock:9999 = 100
```

#### 第3步：MySQL处理（异步，2秒后）
```
[replenish.mysql] 开始消费增加队列
[replenish.mysql] 商品库存记录不存在，自动创建新记录，productId=9999, stock=100
[replenish.mysql] 商品库存记录创建成功，productId=9999
[replenish.mysql] 开始写入补货审计日志，记录数=1
[replenish.mysql] 补货审计日志写入成功
[replenish.mysql] 补货流程结束
```

**MySQL状态：**
```sql
SELECT * FROM product_stock WHERE product_id = 9999;
+------------+-------+---------+---------------------+
| product_id | stock | version | update_time         |
+------------+-------+---------+---------------------+
|       9999 |   100 |       0 | 2024-04-17 14:30:25 |
+------------+-------+---------+---------------------+

SELECT * FROM stock_replenish_log WHERE replenish_no = 'REP20240417001';
+----+-----------------+------------+----------+--------------+-------------+--------+---------------------+
| id | replenish_no    | product_id | quantity | stock_before | stock_after | status | create_time         |
+----+-----------------+------------+----------+--------------+-------------+--------+---------------------+
|  1 | REP20240417001  |       9999 |      100 |            0 |         100 |      1 | 2024-04-17 14:30:25 |
+----+-----------------+------------+----------+--------------+-------------+--------+---------------------+
```

---

## 🎯 优势分析

### 1. 业务友好
- ✅ 新商品可以直接补货，无需预先创建
- ✅ 简化了商品上架流程
- ✅ 符合实际业务场景（供应商发货时可能新增商品）

### 2. 数据一致性
- ✅ Redis和MySQL最终一致
- ✅ 审计日志完整记录初始化过程
- ✅ 通过补货单号可追溯所有操作

### 3. 幂等性保护
- ✅ Lua脚本有幂等性检查
- ✅ MySQL插入有唯一约束保护
- ✅ 重复补货不会创建多条记录

### 4. 可观测性
- ✅ 自动初始化操作有明确日志
- ✅ 可通过日志区分"新建"和"更新"
- ✅ 便于问题追踪和审计

---

## ⚠️ 注意事项

### 1. 商品ID合法性
**风险：** 如果补货单中商品ID错误（如输入了不存在的ID），会自动创建脏数据

**缓解措施：**
- 前端应提供商品选择器，避免手动输入
- 后台可增加商品白名单校验（可选）
- 定期审计补货日志，发现异常及时清理

### 2. 初始库存为0的合理性
**场景：** 首次补货前，Redis中库存为0是合理的

**但如果需要非0初始值：**
- 可以先调用一次补货接口设置初始库存
- 或者扩展系统支持"商品初始化"接口

### 3. 并发安全性
**保证：**
- Lua脚本原子性保证Redis操作安全
- 乐观锁保证MySQL更新安全
- 异步消费者串行处理同一队列消息

---

## 🔍 Debug技巧

### 查看新商品自动初始化日志
```bash
# 查看所有自动创建的记录
grep "自动创建新记录" logs/replenish/mysql/*.log

# 查看某个商品的完整补货链路
grep "productId=9999" logs/replenish/redis/*.log
grep "productId=9999" logs/replenish/mysql/*.log
```

### 验证数据一致性
```sql
-- 检查Redis和MySQL库存是否一致
SELECT product_id, stock FROM product_stock WHERE product_id = 9999;

-- 在Redis CLI中验证
GET product:stock:9999
```

### 查找异常的自动初始化
```bash
# 统计每天自动创建的商品数量
grep "自动创建新记录" logs/replenish/mysql/*.log | \
  grep "$(date +%Y-%m-%d)" | wc -l

# 如果数量异常多，可能存在误操作
```

---

## 🔄 替代方案对比

### 方案A：严格校验（未采用）
```lua
if not before then
    return {0, "key_not_found", KEYS[i]}  -- 拒绝补货
end
```

**优点：**
- 数据更干净，不会有误操作产生的脏数据

**缺点：**
- 新商品需要先通过其他接口初始化
- 业务流程复杂，用户体验差

### 方案B：自动初始化（✅ 已采用）
```lua
if not before then
    redis.call('SET', KEYS[i], 0)  -- 自动创建
end
```

**优点：**
- 业务流程简单，新商品可直接补货
- 符合实际业务场景

**缺点：**
- 需要防范商品ID输入错误

**结论：方案B更适合您的系统！**

---

## 📝 总结

### 当前行为
| 场景 | Redis | MySQL | 处理方式 |
|------|-------|-------|---------|
| 商品已存在 | key存在 | 记录存在 | 正常增加库存 |
| 商品不存在 | key不存在 | 记录不存在 | **自动初始化** |
| 仅Redis不存在 | key不存在 | 记录存在 | Redis自动创建，MySQL正常更新 |
| 仅MySQL不存在 | key存在 | 记录不存在 | Redis正常增加，MySQL自动创建 |

### 关键特性
- ✅ **零配置** - 无需额外设置
- ✅ **自动化** - 系统自动处理不存在的情况
- ✅ **可追溯** - 完整日志记录初始化过程
- ✅ **安全** - 幂等性保护和乐观锁保证数据一致性

**现在您的补货系统可以优雅地处理新商品了！** 🎉

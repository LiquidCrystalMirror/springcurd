# 结构化日志使用指南

## 📁 日志文件目录结构

```
logs/
├── order/                    # 订单相关日志
│   ├── redis/               
│   │   ├── create/         # 订单创建-Redis操作
│   │   │   └── order_redis_create.log
│   │   └── cancel/         # 订单取消-Redis操作
│   │       └── order_redis_cancel.log
│   └── mysql/              
│       ├── create/         # 订单创建-MySQL操作(异步消费者)
│       │   └── order_mysql_create.log
│       └── cancel/         # 订单取消-MySQL操作
│           └── order_mysql_cancel.log
└── replenish/              # 补货相关日志
    ├── redis/              
    │   └── replenish_redis.log
    └── mysql/              
        └── replenish_mysql.log
```

## 🔧 使用方法

### 1. 导入工具类
```java
import org.example.springbootdemo.util.StructuredLogger;
import static org.example.springbootdemo.util.StructuredLogger.LogCategory.*;
```

### 2. 记录日志示例

#### 订单创建 - Redis操作
```java
// INFO级别
StructuredLogger.info(ORDER_REDIS_CREATE, bizNo, 
    "开始执行批量库存扣减，商品数量={}", operations.size());

// ERROR级别（带异常）
StructuredLogger.error(ORDER_REDIS_CREATE, bizNo, 
    "Redis库存扣减失败，可能原因：库存不足或网络异常", e);

// WARN级别
StructuredLogger.warn(ORDER_REDIS_CREATE, bizNo, 
    "库存扣减返回失败，错误码={}, 错误信息={}", result.getCode(), result.getMessage());
```

#### 订单取消 - Redis操作
```java
StructuredLogger.info(ORDER_REDIS_CANCEL, bizNo, 
    "开始回滚商品库存，productId={}, quantity={}", productId, quantity);

StructuredLogger.error(ORDER_REDIS_CANCEL, bizNo, 
    "Redis库存回滚失败，可能原因：幂等性检查失败或key不存在", e);
```

#### 订单取消 - MySQL操作
```java
StructuredLogger.info(ORDER_MYSQL_CANCEL, bizNo, 
    "开始更新订单状态为已取消，productId={}", productId);

StructuredLogger.error(ORDER_MYSQL_CANCEL, bizNo, 
    "数据库更新失败（第{}/{}次重试），可能原因：乐观锁冲突或连接超时", 
    retry + 1, maxRetries, e);

// 严重错误 - 需人工介入
StructuredLogger.error(ORDER_MYSQL_CANCEL, bizNo, 
    "【严重错误】Redis已回滚但数据库更新失败（已重试{}次），数据不一致！需人工修复", 
    maxRetries);
```

#### 补货 - Redis操作
```java
StructuredLogger.info(REPLENISH_REDIS, replenishNo, 
    "开始执行补货操作，商品数量={}", operations.size());

StructuredLogger.error(REPLENISH_REDIS, replenishNo, 
    "补货Redis操作失败，可能原因：商品key不存在或网络异常", e);
```

#### 补货 - MySQL操作
```java
StructuredLogger.info(REPLENISH_MYSQL, replenishNo, 
    "开始写入补货审计日志，记录数={}", logs.size());

StructuredLogger.error(REPLENISH_MYSQL, replenishNo, 
    "补货日志写入失败（第{}/{}次重试），可能原因：唯一约束冲突或连接异常", 
    retry + 1, maxRetries, e);
```

## 📊 日志格式说明

每条日志包含以下信息：
```
2024-04-17 14:30:25.123 [http-nio-8080-exec-1] INFO  o.e.s.s.i.OrderProcessingServiceImpl - [Category:order.redis.create] [BizNo:ORDER20240417001] 2024-04-17 14:30:25.123 开始执行批量库存扣减，商品数量=3
```

字段说明：
- **时间戳**: `2024-04-17 14:30:25.123`
- **线程名**: `[http-nio-8080-exec-1]`
- **日志级别**: `INFO`
- **类名**: `o.e.s.s.i.OrderProcessingServiceImpl`
- **分类**: `[Category:order.redis.create]`
- **业务单号**: `[BizNo:ORDER20240417001]`
- **消息内容**: 包含详细时间和参数

## 🎯 关键错误场景及日志建议

### 1. 订单创建失败
| 场景 | 日志分类 | 错误原因 |
|------|---------|---------|
| Redis扣减失败 | ORDER_REDIS_CREATE | 库存不足、key不存在、网络超时 |
| Lua脚本异常 | ORDER_REDIS_CREATE | 脚本语法错误、Redis连接断开 |
| 参数校验失败 | - | 订单号为空、商品列表为空 |

### 2. 订单取消失败
| 场景 | 日志分类 | 错误原因 |
|------|---------|---------|
| 订单明细不存在 | ORDER_MYSQL_CANCEL | 订单未创建、数据被删除 |
| Redis回滚失败 | ORDER_REDIS_CANCEL | 幂等性冲突、key不存在 |
| 数据库更新失败 | ORDER_MYSQL_CANCEL | 乐观锁冲突、连接超时、状态不符 |
| 数据不一致 | ORDER_MYSQL_CANCEL | Redis成功但DB失败（最严重） |

### 3. 补货失败
| 场景 | 日志分类 | 错误原因 |
|------|---------|---------|
| Redis增加失败 | REPLENISH_REDIS | key不存在、网络异常 |
| 审计日志写入失败 | REPLENISH_MYSQL | 唯一约束冲突、连接超时 |

## 🔍 Debug技巧

### 快速定位问题
```bash
# 查看某个订单的所有Redis操作
grep "ORDER20240417001" logs/order/redis/create/*.log

# 查看某个订单的MySQL操作
grep "ORDER20240417001" logs/order/mysql/cancel/*.log

# 查看所有严重错误
grep "严重错误" logs/order/mysql/cancel/*.log

# 查看今天的补货错误
grep "$(date +%Y-%m-%d)" logs/replenish/redis/*.log | grep ERROR
```

### 分析错误模式
```bash
# 统计各类错误数量
grep -c "ERROR" logs/order/redis/create/*.log
grep -c "ERROR" logs/order/mysql/cancel/*.log

# 查找重试次数最多的订单
grep "重试" logs/order/mysql/cancel/*.log | sort | uniq -c | sort -rn
```

## ⚙️ 配置调整

### 修改日志保留天数
编辑 `logback-spring.xml`，修改 `<maxHistory>30</maxHistory>`

### 修改单个文件大小限制
编辑 `<maxFileSize>100MB</maxFileSize>`

### 调整日志级别
将 `<logger name="order.redis.create" level="INFO"` 改为 `DEBUG` 可看到更详细信息

## 📝 注意事项

1. **所有可能出错的地方都必须记录日志**
2. **ERROR级别日志必须包含异常堆栈**
3. **关键业务节点记录INFO日志**
4. **重试操作记录WARN日志**
5. **数据不一致记录ERROR并标注"需人工介入"**
6. **日志消息要清晰描述可能原因**

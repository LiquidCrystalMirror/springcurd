# 电商库存同步防超卖系统

> 基于 Redis + Lua 原子操作的高并发库存扣减系统，解决电商大促场景下的超卖风险与数据库瓶颈问题。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![JDK](https://img.shields.io/badge/JDK-17-orange)](https://openjdk.org/)

---

## 📋 项目概述

在电商秒杀/大促场景下，传统基于数据库行锁的库存扣减方案存在严重的性能瓶颈。本项目设计了一套 **Redis 主存储 + MySQL 异步持久化** 的库存管理方案，通过 Lua 脚本原子化操作保证并发安全，结合本地消息表 + RabbitMQ 实现可靠异步刷盘，兼顾高并发性能与数据最终一致性。

### 核心指标（单机压测）

| 指标 | 数值 |
|------|------|
| 下单接口 QPS | ~700 |
| 超卖次数 | 0（Lua 原子操作保证） |
| 消息投递可靠性 | 本地消息表 + 状态追踪 |

---

## 🏗️ 技术架构

```
                  ┌──────────────────────────────────┐
                  │          Vue 3 前端               │
                  │    (Vite + Vant UI + Axios)       │
                  └──────────────┬───────────────────┘
                                 │ HTTP
                  ┌──────────────▼───────────────────┐
                  │      Spring Boot 3.3.5            │
                  │                                   │
                  │  ┌─────────────────────────────┐  │
                  │  │  拦截器链                     │  │
                  │  │  TraceId → JWT认证 → 角色鉴权 │  │
                  │  │  → 状态校验 → 测试模式        │  │
                  │  └─────────────────────────────┘  │
                  │                                   │
                  │  ┌─────────────────────────────┐  │
                  │  │  业务层                       │  │
                  │  │  订单处理 / 库存管理 / 补货   │  │
                  │  └──────────┬──────────────────┘  │
                  │             │                      │
                  │  ┌──────────▼──────────────────┐  │
                  │  │  Resilience4j 保护层         │  │
                  │  │  熔断 / 限流 / 重试           │  │
                  │  └──────────────────────────────┘  │
                  └──────┬────────────┬────────────────┘
                         │            │
              ┌──────────▼──┐  ┌──────▼──────────┐
              │    Redis     │  │    RabbitMQ      │
              │  Lua原子操作  │  │  可靠消息投递     │
              │  库存主存储   │  │  异步订单持久化   │
              └──────┬───────┘  └──────┬──────────┘
                     │                 │
              ┌──────▼─────────────────▼──────────┐
              │             MySQL                   │
              │  订单表 / 库存表 / 幂等表 / 消息表   │
              └────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3.5, MyBatis-Plus 3.5.10 |
| 缓存 | Redis + Redisson 3.37（Lua 脚本原子操作） |
| 消息队列 | RabbitMQ（本地消息表模式，可靠投递） |
| 数据库 | MySQL 8.0（乐观锁版本号并发控制） |
| 熔断限流 | Resilience4j 2.2（CircuitBreaker + RateLimiter + Retry） |
| 认证 | JWT（jjwt 0.12）+ BCrypt 密码加密 |
| 监控 | Spring Boot Actuator + Prometheus + TraceId 链路追踪 |
| 前端 | Vue 3 + Vite + Vant UI + Axios |
| 日志 | Logback（结构化日志 + 按业务类别分流） |

---

## 🚀 快速启动

### 前置依赖

- JDK 17+
- Maven 3.8+
- Docker（或手动安装 MySQL 8.0 + Redis 7.x + RabbitMQ 3.x）

### 1. 启动依赖服务

```bash
docker-compose up -d
```

### 2. 初始化数据库

```bash
# 建表
mysql -u root -p < sql/sqltest.sql

# 种子数据（角色、管理员账号）
mysql -u root -p < sql/seed_data.sql
```

> ⚠️ `seed_data.sql` 中的 BCrypt 密码密文需要先生成：
> 在项目任意 `@Test` 方法中运行：
> `System.out.println(new BCryptPasswordEncoder(10).encode("admin123"));`
> 将输出替换 SQL 中的 `$2a$10$PLACEHOLDER_REPLACE_ME`
>
> 详见 `personal/GenerateBcryptHash.java`

### 3. 配置环境变量

编辑 `.env` 文件（默认值适用于 docker-compose）：

```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=springbootdemo
DB_USERNAME=root
DB_PASSWORD=root
REDIS_HOST=localhost
REDIS_PORT=6379
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
```

### 4. 启动后端

```bash
cd spring
mvn spring-boot:run
```

服务启动后访问：
- Swagger 文档：http://localhost:8081/swagger-ui.html
- 健康检查：http://localhost:8081/actuator/health

### 5. 启动前端

```bash
cd vue
npm install
npm run dev
```

---

## 🎬 5分钟完整演示流程

> 本项目定位：面向仓库管理者的库存后台。库存从零创建（补货），订单由外部系统通过 API 推送。

### 演示故事线

```
管理员登录 → 补货入库 → 外部订单扣减 → 取消订单回滚 → 验证数据一致性
```

### Step 1: 管理员登录

```bash
# 管理员账号来自 seed_data.sql（id=1, 密码=admin123）
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"id":1,"password":"admin123"}'

# 响应中获取 token
# {"code":200,"data":{"token":"eyJhbG..."}}
```

将 token 存为环境变量：

```bash
# PowerShell
$TOKEN="eyJhbG..."   # 替换为实际 token

# Bash
export TOKEN="eyJhbG..."
```

### Step 2: 补货入库（从零创建库存）

```bash
# 无需预先创建商品，补货时自动初始化
# 为商品 1、2、3 各补 100 件
curl -X POST http://localhost:8081/replenish/submit \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "items":[
      {"productId":1,"productName":"iPhone 15","quantity":100},
      {"productId":2,"productName":"MacBook Pro","quantity":100},
      {"productId":3,"productName":"AirPods Pro","quantity":100}
    ]
  }'

# 验证 Redis 库存
redis-cli GET product:stock:1   # 应返回 100
redis-cli GET product:stock:2   # 应返回 100
redis-cli GET product:stock:3   # 应返回 100
```

### Step 3: 创建订单（扣减库存）

```bash
# 下单：商品1买5件，商品2买3件
curl -X POST http://localhost:8081/orders/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderNo":"ORD-DEMO-001",
    "platformId":"PLATFORM_A",
    "items":[
      {"productId":1,"quantity":5},
      {"productId":2,"quantity":3}
    ]
  }'

# 验证扣减结果
redis-cli GET product:stock:1   # 100-5=95
redis-cli GET product:stock:2   # 100-3=97
```

### Step 4: 幂等性验证（防重提交）

```bash
# 重复提交相同订单号 → 被拦截
curl -X POST http://localhost:8081/orders/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderNo":"ORD-DEMO-001",
    "platformId":"PLATFORM_A",
    "items":[{"productId":1,"quantity":5}]
  }'

# 响应: {"code":409,"message":"订单已存在，请勿重复提交"}
# 库存不变（仍然是95，不是90）
```

### Step 5: 取消订单（库存回滚）

```bash
# 等待2-4秒让 MQ 消费者完成订单持久化
sleep 4

curl -X POST http://localhost:8081/orders/cancel \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderNo":"ORD-DEMO-001",
    "platformId":"PLATFORM_A",
    "items":[
      {"productId":1,"quantity":5},
      {"productId":2,"quantity":3}
    ]
  }'

# 验证库存已恢复
redis-cli GET product:stock:1   # 回到 100
redis-cli GET product:stock:2   # 回到 100

# 验证数据库一致性
mysql -u root -p -e "SELECT status FROM springbootdemo.order_detail WHERE order_no='ORD-DEMO-001'"
# status=0 表示已取消
```

### Step 6: 验证数据最终一致性

```bash
# Redis库存
redis-cli GET product:stock:1    # 100
# MySQL备份库存
mysql -u root -p -e "SELECT stock,version FROM springbootdemo.product_stock WHERE product_id=1"
# 应该与 Redis 一致
```

### 完整流程总结

```
初始状态: Redis 空，库存=0
  → 补货 3 个商品各 100 件 → Redis 库存 = [100, 100, 100]
  → 下单扣 5+3                 → Redis 库存 = [95, 97, 100]
  → 重复下单                   → 幂等拦截，库存不变
  → 取消订单                   → Redis 库存 = [100, 100, 100]
  → 零超卖，最终一致 ✅
```

**所有 API 的完整测试用例**见 [`API测试用例.md`](API测试用例.md)。

---

## 🎯 核心设计

### 1. Redis + Lua 原子库存扣减

**问题**：多线程并发扣减库存时，先读后写的操作模式存在竞态条件，导致超卖。

**方案**：将「检查库存 → 扣减数量 → 写回」封装为单个 Lua 脚本，利用 Redis 单线程模型保证原子性。

```
EVAL 脚本 → 一次性完成:
  ① 检查库存是否充足
  ② 扣减库存值
  ③ 写入幂等标记
  ④ 保存操作快照（用于回滚）
```

详见：[Redis+Lua 原子操作设计文档](doc/Redis+Lua原子操作设计.md)

### 2. 双重幂等保护

防止网络重试导致的重复扣减，采用 **两层幂等** 设计：

| 层级 | 实现 | 特点 |
|------|------|------|
| Redis 层 | `biz:idempotent:{opType}:{bizNo}` | 快速拦截，有 TTL 过期 |
| MySQL 层 | `biz_idempotent` 表 | 永久保存，联合唯一索引 |

即使 Redis 幂等 Key 过期，数据库层的唯一索引也能拦截重复请求。

详见：[双重幂等保护设计文档](doc/双重幂等保护设计.md)

### 3. 本地消息表 + RabbitMQ 可靠投递

**问题**：库存扣减成功后，异步发送 MQ 消息可能丢失（网络闪断、Broker 宕机）。

**方案**：采用本地消息表模式（Transactional Outbox 的简化版）：

```
业务操作成功
  → 写入本地消息表（状态=0 待发送，与业务在同一事务中）
  → 发送 RabbitMQ 消息
  → 成功：更新状态=1 / 失败：更新状态=2
```

消息表 `rabbit_message_log` 记录每次投递的完整消息体，状态可追溯。

详见：[本地消息表与可靠性投递设计文档](doc/本地消息表与可靠性投递设计.md)

### 4. 乐观锁 + 指数退避重试

`product_stock` 表使用 `version` 字段实现乐观锁并发控制：

```sql
UPDATE product_stock 
SET stock = ?, version = version + 1 
WHERE product_id = ? AND version = ?
```

冲突时指数退避重试（10ms → 20ms → 30ms），最多 3 次。

### 5. Resilience4j 熔断降级

| 组件 | 实例 | 触发条件 | 降级行为 |
|------|------|----------|----------|
| CircuitBreaker | redisService | 10次窗口失败率≥50% | 熔断15s |
| CircuitBreaker | rabbitmqService | 同上 | 静默跳过，不阻塞主流程 |
| RateLimiter | orderCreate | >20 req/s | 返回429 |
| RateLimiter | loginRateLimit | >5 req/s | 防止暴力破解 |
| Retry | redisRetry | 操作异常 | 最多3次，100→200→400ms |
| TimeLimiter | redisTimeLimiter | >3s | 超时中断 |

详见：[Resilience4j 熔断限流设计文档](doc/Resilience4j熔断限流设计.md)

### 6. TraceId 全链路追踪

每个请求进入时，`TraceIdFilter` 生成 16 位 UUID，写入：
- `MDC("traceId")` → 日志自动带上 `[TraceId:xxx]`
- 响应头 `X-Trace-Id` → 前端可获取用于问题反馈

详见：[TraceId 链路追踪设计文档](personal/TraceId链路追踪设计.md)

---

## 📁 项目结构

```
springBootDemo/
├── spring/                          # 后端 Spring Boot 工程
│   ├── src/main/java/org/example/springbootdemo/
│   │   ├── config/                  # 配置类（Redisson/RabbitMQ/TraceId/TestMode）
│   │   ├── constant/enums/          # 枚举常量
│   │   ├── controller/              # REST 控制器
│   │   ├── dto/                     # 数据传输对象
│   │   ├── entity/                  # 数据库实体
│   │   ├── exception/               # 业务异常体系（400/401/403/404/409/503）
│   │   ├── interceptor/             # 拦截器（JWT/角色/状态/测试模式/响应时间）
│   │   ├── mapper/                  # MyBatis Mapper
│   │   ├── service/                 # 服务层
│   │   │   └── imp/                 # 服务实现
│   │   ├── util/                    # 工具类
│   │   │   ├── LuaScriptManager.java    # Lua脚本管理器（SHA缓存+自动回退）
│   │   │   ├── StructuredLogger.java    # 结构化日志（按业务分类路由）
│   │   │   ├── ApiResult.java           # 统一响应体（含traceId/errorCode）
│   │   │   ├── JwtUtil.java             # JWT工具（双Token机制）
│   │   │   └── PasswordUtil.java        # 密码工具（BCrypt+MD5向后兼容）
│   │   └── vo/                     # 视图对象
│   └── src/main/resources/
│       ├── lua/                     # Lua脚本（5个）
│       ├── mapper/                  # MyBatis XML
│       ├── application-*.yml        # 分层配置文件
│       └── logback-spring.xml       # 日志配置
├── vue/                            # 前端 Vue 3 工程
├── sql/                            # 数据库脚本
│   ├── sqltest.sql                 # 建表（完整schema）
│   ├── migration_v3.sql            # 用户表拆分迁移
│   ├── migration_v4.sql            # 补货单索引优化
│   └── seed_data.sql               # 种子数据（角色+管理员）
├── doc/                            # 设计文档（公开）
├── personal/                       # 设计笔记（私有，不提交）
├── docker-compose.yml              # 依赖服务编排
└── API测试用例.md                   # 完整接口测试用例
```

---

## 🧪 接口测试

完整的测试用例见 [`API测试用例.md`](API测试用例.md)，覆盖：
- 订单创建/幂等性/库存不足/参数校验
- 订单取消/部分失败/订单不存在
- 补货/幂等性/参数校验
- 回滚验证/消息格式验证
- 数据库幂等表验证

---

## 📊 压测验证

使用 JMeter 进行本地单机压测（100线程 × 100次循环）：

```
最终库存数 = 初始库存 - 扣减总量
→ 验证零超卖
```

---

## 🛡️ 异常处理体系

```
RuntimeException
  └── BusinessException（含 httpStatus + errorCode）
        ├── BadRequestException       (400) 参数校验
        ├── UnauthorizedException     (401) 未登录
        ├── ForbiddenException        (403) 权限不足
        ├── NotFoundException         (404) 资源不存在
        ├── ConflictException         (409) 幂等性冲突
        └── ServiceUnavailableException (503) 熔断限流
```

通过 `@RestControllerAdvice` 全局捕获，统一响应格式。

---

## 📝 设计文档索引

公开设计文档位于 [`doc/`](doc/) 目录：

| 文档 | 说明 |
|------|------|
| [整体架构设计总览](doc/整体架构设计总览.md) | 项目架构全景图与技术选型依据 |
| [Redis+Lua原子操作设计](doc/Redis+Lua原子操作设计.md) | Lua脚本设计思路、Redis单线程模型原理 |
| [双重幂等保护设计](doc/双重幂等保护设计.md) | Redis+MySQL双层幂等的设计权衡 |
| [本地消息表与可靠性投递](doc/本地消息表与可靠性投递设计.md) | 消息投递可靠性保障方案 |
| [乐观锁与指数退避重试](doc/乐观锁与指数退避重试设计.md) | 并发控制策略与重试机制 |
| [Resilience4j熔断限流](doc/Resilience4j熔断限流设计.md) | 三层保护（熔断/限流/重试）设计 |
| [架构升级说明](doc/架构升级说明_2026-07-14.md) | 异常体系/熔断/监控升级记录 |

> 更多内部设计笔记（TraceId、BCrypt、结构化日志、TestMode、RabbitMQ数据链路等）位于 `personal/` 目录（不提交）。

---

## 📄 License

MIT

好的，我为你整理了一份完整的项目分析报告，涵盖架构、技术栈、核心流程、模块划分和实现细节。你可以直接将这份文档用于毕业设计论文、项目日志或新的技术讨论。

---

# 基于 SpringBoot + Redisson 的电商大促库存同步防超卖系统 —— 项目全面分析

## 一、项目概述

### 1.1 项目背景与目标

在电商大促（如双11、618）场景中，商品库存面临极高并发的读写压力。传统基于关系型数据库的行锁方案存在性能瓶颈，容易出现超卖现象。本项目旨在构建一套**高性能、高可用、最终一致性**的库存管理系统，核心目标：

- **防止超卖**：利用 Redis 原子操作保证扣减的准确性。
- **高并发支撑**：Redis 作为库存主存储，承载每秒数千级别的扣减请求。
- **数据可靠性**：通过异步持久化 + 定时对账，保证 Redis 与 MySQL 数据的最终一致。
- **业务完整性**：支持订单取消时库存的原子恢复，并保证幂等性。

### 1.2 项目适用范围

- 毕业设计 / 课程设计
- 中小型电商秒杀、抢购活动的库存模块原型
- 分布式系统学习案例

---

## 二、系统架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      客户端（Vue3）                          │
└─────────────────────────┬───────────────────────────────────┘
                          │ HTTP / JSON
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   SpringBoot 后端服务                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ Controller  │→ │   Service   │→ │  LuaScriptManager   │ │
│  │   (API)     │  │  (业务逻辑)  │  │  (Redis脚本执行器)  │ │
│  └─────────────┘  └─────────────┘  └──────────┬──────────┘ │
│                                                │            │
│                              ┌─────────────────┴────────────┐│
│                              │        Redisson Client       ││
│                              └─────────────────┬────────────┘│
│                                                │            │
│         ┌──────────────────────────────────────┼──────────┐ │
│         │            Redis (主存储)            │          │ │
│         │  - 商品库存 (String)                 │          │ │
│         │  - 幂等标记 (String)                 │          │ │
│         │  - 异步队列 (List)  ◄────────────────┘          │ │
│         └──────────────────────────────────────┬──────────┘ │
│                                                │            │
│         ┌──────────────────────────────────────┼──────────┐ │
│         │     StockPersistenceConsumer         │          │ │
│         │   (定时拉取队列，持久化到MySQL)        │          │ │
│         └──────────────────────────────────────┼──────────┘ │
│                                                │            │
│         ┌──────────────────────────────────────┼──────────┐ │
│         │            MySQL (备份存储)           │          │ │
│         │  - order_detail (订单明细)            │          │ │
│         │  - product_stock (库存备份)           │          │ │
│         │  - user, dept (辅助业务)              │          │ │
│         └──────────────────────────────────────┴──────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 架构核心思想

| 组件           | 职责                                           | 选型理由                                         |
| -------------- | ---------------------------------------------- | ------------------------------------------------ |
| **Redis**      | 库存主存储，提供原子扣减/增加操作              | 单线程模型，`DECRBY/INCRBY` 天然原子性，QPS 10w+ |
| **Redisson**   | Redis 客户端 + 分布式锁 + Lua 脚本执行         | 封装完善，提供 RScript 高效执行 Lua              |
| **Lua 脚本**   | 实现复杂原子操作（批量扣减、回滚、取消、查询） | 减少网络往返，保证操作的原子性和幂等性           |
| **Redis List** | 轻量级消息队列，缓冲异步持久化任务             | 简单可靠，无额外中间件依赖，内存占用可控         |
| **MySQL**      | 数据备份 + 复杂查询 + 对账基准                 | 持久化保证，便于报表统计和人工干预               |
| **SpringBoot** | 整体框架，提供 Web 服务、事务管理、定时任务    | 生态成熟，开发效率高                             |
| **PageHelper** | 分页查询                                       | 与 MyBatis 无缝集成                              |
| **JWT**        | 无状态认证                                     | 适用于前后端分离架构                             |

---

## 三、技术栈明细

| 层级        | 技术                   | 版本   | 用途                    |
| ----------- | ---------------------- | ------ | ----------------------- |
| 后端框架    | Spring Boot            | 2.7.x  | 基础框架                |
| ORM         | MyBatis / MyBatis-Plus | 3.5.x  | 数据库操作              |
| 分页插件    | PageHelper             | 5.3.x  | 物理分页                |
| Redis客户端 | Redisson               | 3.23.5 | 分布式锁、Lua脚本、队列 |
| JSON处理    | Jackson / Fastjson     | -      | 序列化与反序列化        |
| 数据库      | MySQL                  | 8.0+   | 关系型数据存储          |
| 缓存/队列   | Redis                  | 7.0+   | 库存主存、队列、标记    |
| 认证授权    | JJWT                   | 0.11.x | JWT生成与解析           |
| 接口文档    | SpringDoc OpenAPI      | 1.6.x  | Swagger UI              |
| 工具库      | Lombok                 | -      | 简化代码                |

### 3.1 Redis 关键配置

```properties
maxmemory 100mb
maxmemory-policy volatile-lru
appendonly yes
aof-use-rdb-preamble yes
```

- **maxmemory 100mb**：限制内存上限，防止 OOM。
- **volatile-lru**：内存满时优先淘汰有过期时间的 key（如幂等标记、快照）。
- **appendonly yes + 混合持久化**：保证数据重启可恢复，兼顾性能与安全。

---

## 四、核心模块与代码结构

### 4.1 项目包结构

```
org.example.springbootdemo
├── config                      # 配置类
│   ├── WebConfig.java
│   ├── QueueConsumerProperties.java
│   └── StockScriptProperties.java
├── constant                    # 枚举常量
│   └── enums
│       ├── ScriptConstant.java
│       └── UserStatus.java
├── controller                  # 接口层
│   ├── DashBoardController.java
│   └── UserController.java
├── dto                         # 数据传输对象
│   ├── ApiResult.java
│   ├── DeptDTO.java
│   ├── LoginResponse.java
│   └── UserDTO.java
├── entity                      # 实体类（对应数据库表）
│   ├── OrderDetail.java
│   └── ProductStock.java
├── exception                   # 全局异常处理
│   └── GlobalExceptionHandler.java
├── interceptor                 # 拦截器（认证、权限）
│   ├── JwtAuthInterceptor.java
│   └── RoleInterceptor.java
├── mapper                      # MyBatis Mapper 接口
│   ├── DeptMapper.java
│   ├── OrderDetailMapper.java
│   ├── ProductStockMapper.java
│   └── UserMapper.java
├── model                       # 业务模型（非持久化）
│   └── OrderItem.java
├── query                       # 查询条件对象
│   ├── base/BaseQuery.java
│   ├── DeptQuery.java
│   ├── OrderQuery.java
│   ├── ProductStockQuery.java
│   └── UserQuery.java
├── service                     # 服务层接口与实现
│   ├── DeptService.java
│   ├── OrderDetailService.java
│   ├── ProductStockService.java
│   ├── StockPersistenceConsumer.java   # 队列消费者
│   ├── UserService.java
│   └── imp/
│       ├── DeptServiceImpl.java
│       ├── OrderDetailServiceImpl.java
│       ├── ProductStockServiceImpl.java
│       └── UserServiceImpl.java
├── util                        # 工具类
│   ├── JwtUtil.java
│   ├── LuaScriptManager.java   # Lua脚本统一管理器 ★
│   └── PasswordUtil.java
├── vo                          # 视图对象（返回前端）
│   └── UserVO.java
└── SpringBootDemoApplication.java
```

### 4.2 关键类职责说明

| 类名                       | 核心职责                                                     |
| -------------------------- | ------------------------------------------------------------ |
| `LuaScriptManager`         | 加载、缓存、执行 Redis Lua 脚本；封装批量操作、回滚、查询、取消等接口；返回统一 `BatchResult` |
| `StockPersistenceConsumer` | 定时从 Redis List 拉取消息，将库存变更持久化到 MySQL（订单明细 + 库存备份表） |
| `GlobalExceptionHandler`   | 统一处理各类异常，返回标准 `ApiResult` 格式                  |
| `JwtAuthInterceptor`       | 校验请求头 `Authorization` 中的 Bearer Token，提取用户信息存入 `request` 属性 |
| `RoleInterceptor`          | 基于用户角色进行接口级权限控制（如管理员才可更新用户）       |

---

## 五、数据库设计

### 5.1 订单明细表 `order_detail`

| 字段        | 类型     | 描述                             |
| ----------- | -------- | -------------------------------- |
| id          | BIGINT   | 主键（雪花ID）                   |
| order_no    | VARCHAR  | 订单号                           |
| platform_id | VARCHAR  | 平台标识                         |
| product_id  | BIGINT   | 商品ID                           |
| quantity    | INT      | 购买数量                         |
| status      | TINYINT  | 状态：0-已取消，1-正常，2-已回滚 |
| create_time | DATETIME | 创建时间                         |
| update_time | DATETIME | 更新时间                         |

### 5.2 库存备份表 `product_stock`

| 字段        | 类型     | 描述                          |
| ----------- | -------- | ----------------------------- |
| product_id  | BIGINT   | 商品ID（主键）                |
| stock       | INT      | 库存数量（定期从 Redis 同步） |
| version     | INT      | 乐观锁版本号                  |
| update_time | DATETIME | 最后更新时间                  |

### 5.3 用户表 `user`

| 字段     | 类型    | 描述                            |
| -------- | ------- | ------------------------------- |
| id       | INT     | 用户ID                          |
| name     | VARCHAR | 用户名                          |
| password | VARCHAR | MD5加盐密码                     |
| status   | TINYINT | 状态（0禁用，1启用）            |
| role_id  | TINYINT | 角色ID（1管理员，其他普通用户） |

### 5.4 部门表 `dept`

| 字段 | 类型    | 描述     |
| ---- | ------- | -------- |
| id   | BIGINT  | 部门ID   |
| name | VARCHAR | 部门名称 |

---

## 六、Redis 数据结构与 Key 设计

| Key 模式                          | 数据类型      | 用途                                  | 示例                               |
| --------------------------------- | ------------- | ------------------------------------- | ---------------------------------- |
| `product:stock:{productId}`       | String        | 商品实时库存                          | `product:stock:1001` → `500`       |
| `biz:idempotent:{opType}:{bizNo}` | String        | 幂等标记（processing/success/failed） | `biz:idempotent:deduct:ORD2024001` |
| `biz:snapshot:{opType}:{bizNo}`   | String (JSON) | 操作前库存快照，用于回滚              | `biz:snapshot:deduct:ORD2024001`   |
| `cancel:idempotent:{bizNo}`       | String        | 取消操作的幂等标记                    | `cancel:idempotent:ORD2024001`     |
| `async:queue:deduct`              | List          | 扣减异步队列                          | 元素为 JSON 格式的队列消息         |
| `async:queue:add`                 | List          | 增加（如退货）异步队列                |                                    |
| `async:queue:rollback`            | List          | 回滚异步队列                          |                                    |

---

## 七、Lua 脚本详解

### 7.1 `batch_deduct.lua` —— 批量扣减/增加

**功能**：
- 支持批量扣减（`deduct`）或增加（`add`）
- 预检查库存是否充足（扣减时）
- 记录操作前快照到 `biz:snapshot:{opType}:{bizNo}`
- 原子执行 `DECRBY` / `INCRBY`
- 幂等控制，避免重复执行
- 执行成功后推送消息到 `async:queue:{opType}`

**返回值**：`[状态码, 消息, bizNo, snapshotKey]`

### 7.2 `rollback.lua` —— 通用回滚

**功能**：
- 根据快照恢复数据到操作前状态
- 支持部分 key 恢复或全部恢复
- 清理幂等标记和快照（可选）
- 推送回滚消息到 `async:queue:rollback`

### 7.3 `query.lua` —— 批量查询库存

**功能**：
- 批量获取多个商品库存的当前值
- 返回 JSON 格式结果 `{"product:stock:1": "100", ...}`

### 7.4 `cancel.lua` —— 取消订单库存恢复 ★

**功能**：
- 原子增加指定商品库存
- 独立幂等标记 `cancel:idempotent:{bizNo}`
- **不保存快照，不发送队列**（依赖定时对账最终一致）

**参数**：
- KEYS：商品库存 key 列表
- ARGV[1..N]：对应增加数量
- ARGV[N+1]：订单号 bizNo
- ARGV[N+2]：平台 ID
- ARGV[N+3]：超时时间

**返回值**：`[状态码, 消息, bizNo]`

---

## 八、核心业务流程

### 8.1 下单扣减库存流程

```
1. 前端携带 Token 请求下单接口
2. JwtAuthInterceptor 校验 Token，解析用户信息
3. Controller 接收订单项列表（productId, quantity）
4. Service 构造 Redis key 和数量映射 Map<String, Integer>
5. 调用 LuaScriptManager.executeBatchDeduct(bizNo, platformId, map)
6. Lua 脚本执行：
   - 幂等检查 → 预检查库存 → 保存快照 → 原子扣减 → 推送队列
7. 返回扣减结果（成功则订单创建成功）
8. 异步消费者 StockPersistenceConsumer 定时拉取队列消息
9. 消费者将订单明细插入 MySQL order_detail，并更新 product_stock 备份表
10. 定时对账任务（未在代码中展示，可扩展）定期对比 Redis 与 MySQL 库存，修正差异
```

### 8.2 取消订单恢复库存流程

```
1. 前端请求取消订单（订单号 orderNo, 平台ID）
2. Service 查询 MySQL 确认订单存在且状态可取消
3. 更新 order_detail 状态为“已取消”（status=0）
4. 构造恢复映射 Map<String, Integer>（商品 key → 数量）
5. 调用 LuaScriptManager.executeCancel(orderNo, platformId, map)
6. Lua 脚本执行：
   - 幂等检查 → 原子增加库存 → 标记成功
7. 返回成功，流程结束（无需发队列，对账任务后续同步备份表）
```

### 8.3 定时对账任务（设计预留）

- 每 5 分钟执行一次
- 扫描所有 `product:stock:*` 的 Redis 库存值
- 与 MySQL `product_stock` 表对比
- 若差异超过阈值（如 5%），以 MySQL 为准修正 Redis（或反之根据业务决定）
- 记录对账日志，异常告警

---

## 九、异步持久化消费者机制

### 9.1 消费者实现

`StockPersistenceConsumer` 通过 `@Scheduled(fixedDelay = 2000)` 每 2 秒执行一次，分别处理三个队列：

- `async:queue:deduct`
- `async:queue:add`
- `async:queue:rollback`

### 9.2 消费流程

1. 批量从 Redis List 中 `poll` 最多 `batchSize` 条消息（配置为 50）
2. 解析 JSON 消息，得到 `bizNo`、`platformId` 和 `items` 列表
3. 遍历 items，对每个商品：
   - 从 Redis 获取当前库存值
   - 更新/插入 MySQL `product_stock` 表（带乐观锁重试）
   - 若为扣减操作，插入订单明细 `order_detail`
4. 若为回滚消息，更新订单明细状态为“已回滚”
5. 删除对应的 Redis 快照 key
6. 若处理失败，消息重新放回队列尾部（保证至少一次处理）

### 9.3 队列消息格式

```json
{
  "bizNo": "ORD2024001",
  "platformId": "TAOBAO",
  "items": [
    {"key": "product:stock:1001", "quantity": 2},
    {"key": "product:stock:1002", "quantity": 1}
  ]
}
```

---

## 十、安全与权限控制

### 10.1 JWT 认证流程

1. 用户登录成功，`JwtUtil` 生成 Token（含 userId, roleId），有效期由 `jwt.expiration` 配置。
2. 前端请求时在 Header 中携带 `Authorization: Bearer {token}`
3. `JwtAuthInterceptor` 拦截所有非白名单请求，解析 Token 并提取用户信息存入 `request.setAttribute("currentUser", userDTO)`

### 10.2 角色权限控制

- `RoleInterceptor` 仅对 `/dashboard/**` 路径生效。
- 规则：访问 `/updateUsers` 或 `/delete` 需要 `roleId = 1`（管理员），否则抛出权限不足异常。

### 10.3 白名单配置

```java
registry.addInterceptor(jwtAuthInterceptor)
    .excludePathPatterns(
        "/users/login",
        "/users/register",
        "/doc.html",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        ...
    );
```

---

## 十一、配置管理汇总

### 11.1 主配置文件 `application.yml`

```yaml
server:
  port: 8080
spring:
  config:
    import: 
      - classpath:application-mapper.yml
      - classpath:application-springdoc.yml
      - classpath:application-data.yml
      - classpath:application-redis.yml
```

### 11.2 Redis 与脚本配置 `application-redis.yml`

```yaml
redis:
  redisson:
    config: |
      singleServerConfig:
        address: "redis://127.0.0.1:6379"
        database: 0
        connectionPoolSize: 64
      codec: !<org.redisson.codec.JsonJacksonCodec> {}
stock:
  script:
    timeout: 300
    path:
      batch-operation: lua/batch_deduct.lua
      rollback: lua/rollback.lua
      query: lua/query.lua
      cancel: lua/cancel.lua
    type:
      deduct: deduct
      add: add
  queue:
    batch-size: 50
    deduct-queue: async:queue:deduct
    add-queue: async:queue:add
    rollback-queue: async:queue:rollback
```

### 11.3 其他配置项（需自行补充）

```yaml
# application-data.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/stock_db?useSSL=false&serverTimezone=UTC
    username: root
    password: 123456
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: org.example.springbootdemo.entity

# jwt 配置
jwt:
  secret: your-256-bit-secret-key-here-must-be-long-enough
  expiration: 86400000  # 24小时

# md5 盐值
md5:
  salt: random-salt-string
```

---

## 十二、异常处理与统一响应

### 12.1 统一响应格式 `ApiResult<T>`

```java
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

| 状态码 | 含义                |
| ------ | ------------------- |
| 200    | 成功                |
| 400    | 参数错误、数据冲突  |
| 401    | 未认证 / Token 无效 |
| 403    | 权限不足            |
| 500    | 服务器内部错误      |

### 12.2 全局异常处理映射

| 异常类型                                  | 返回码  | 提示信息                      |
| ----------------------------------------- | ------- | ----------------------------- |
| `DuplicateKeyException`                   | 400     | 用户ID已存在 / 用户名已存在   |
| `DataIntegrityViolationException`         | 400     | 缺少必要参数 / 数据完整性错误 |
| `MethodArgumentNotValidException`         | 400     | 参数校验失败信息              |
| `MissingServletRequestParameterException` | 400     | 缺少必要参数：xxx             |
| `RuntimeException`（含认证/权限消息）     | 401/403 | 未登录 / 权限不足             |

---

## 十三、项目亮点与可扩展方向

### 13.1 亮点总结

- **Redis 主存储 + MySQL 备份**的混合架构，兼顾性能与可靠性。
- **Lua 脚本统一管理**，代码复用度高，原子性有保障。
- **轻量级消息队列**基于 Redis List，无额外中间件依赖。
- **幂等性设计**贯穿所有写操作，保证重复请求安全。
- **乐观锁 + 重试机制**确保备份表并发更新安全。
- **完善的异常处理与统一响应**，便于前端对接。

### 13.2 可扩展方向

1. **引入 Redisson 分布式锁**，用于防止同一商品并发冲突（当前 Lua 原子性已覆盖）。
2. **实现定时对账任务**，完善最终一致性闭环。
3. **增加库存预警**，当 Redis 库存低于阈值时主动通知。
4. **使用 Redis Stream** 替代 List 作为消息队列，支持消费者组和消息确认。
5. **集成 Sentinel** 进行流量控制和熔断降级。
6. **将消费者独立为微服务**，实现库存持久化服务的独立部署与扩容。

---

## 十四、总结

本项目完整实现了一套基于 Redis + MySQL 的电商库存防超卖系统。通过原子化 Lua 脚本、异步队列持久化、幂等控制、乐观锁等机制，有效解决了高并发下的库存准确性与数据持久化的矛盾。代码结构清晰，注释详尽，可作为毕业设计或生产环境的基础原型。

后续你可基于此文档撰写毕业设计报告、系统设计说明书，或在新的对话中引用各模块细节进行深入讨论。
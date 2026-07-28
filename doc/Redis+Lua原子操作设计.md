# Redis + Lua 原子操作设计

> 面试要点：为什么用 Lua 而不是先 GET 再 SET？Redis 单线程模型如何保证原子性？Lua 脚本失败怎么处理？

---

## 一、问题背景

### 1.1 传统方案的问题

在未使用 Lua 脚本时，典型的库存扣减流程是：

```
① GET product:stock:1 → 返回 100
② 判断: 100 >= 扣减数量(5)? → 是
③ SET product:stock:1 95
```

**问题**：步骤①②③之间存在时间窗口。如果线程 A 在步骤②之后、步骤③之前被线程 B 插入执行，线程 B 也读到 100，两个线程都认为库存充足 → **超卖**。

这就是经典的 **Check-Then-Act 竞态条件**。在数据库中用 `SELECT ... FOR UPDATE` 行锁可以解决，但 Redis 是单线程模型，需要用不同的方式。

### 1.2 为什么选 Redis + Lua 而不是 MySQL 行锁

| 方案 | 原理 | 并发能力 | 风险 |
|------|------|----------|------|
| MySQL 行锁 `FOR UPDATE` | 数据库层加排他锁 | 受限于数据库连接池和磁盘IO，通常 < 1000 QPS | 锁等待、死锁 |
| Redis 分布式锁（Redisson） | 加锁→操作→解锁 | 中等，仍有网络往返 | 锁超时、误删锁 |
| Redis + Lua 脚本 | 整个操作在 Redis 内原子执行 | 高，无额外网络开销，可达数万 QPS | 脚本需幂等设计 |

**选择 Redis + Lua 的原因**：
- Redis 单线程执行命令，Lua 脚本执行期间不会被其他命令打断
- 消除了"读→判断→写"之间的竞态窗口
- 无需额外的分布式锁资源

---

## 二、Redis 单线程模型原理

### 2.1 为什么 Redis 单线程还能这么快？

```
客户端A ──→ [请求队列] ──→ Redis主线程（串行执行）──→ [响应队列] ──→ 客户端A
客户端B ──→             ↗                              ↘ ──→ 客户端B
客户端C ──→            （IO多路复用 epoll）
```

1. **内存操作**：数据全在内存中，无磁盘 IO
2. **非阻塞 IO 多路复用**：单线程通过 epoll 同时监听多个连接
3. **避免上下文切换**：没有多线程竞争和锁开销

### 2.2 Lua 脚本的原子性保证

Redis 执行 Lua 脚本时：
- **整个脚本作为一个整体执行**，期间不会插入任何其他命令
- 相当于天然的事务（比 MULTI/EXEC 更强大，因为支持条件判断）
- **脚本必须是纯函数**：同样的输入总是得到同样的输出（我们自己保证幂等）

### 2.3 关键约束：Redis Lua 脚本中不要做非确定性操作

```
❌ 不要在 Lua 中调用 TIME、RANDOMKEY 等非确定性命令
❌ 不要在 Lua 中使用全局变量（集群/主从复制场景不兼容）
✅ 所有需要的数据通过 KEYS[] 和 ARGV[] 传入
```

本项目所有脚本严格遵循此约束。

---

## 三、本项目的 Lua 脚本设计

### 3.1 脚本清单

| 脚本 | 功能 | 关键设计 |
|------|------|----------|
| `batch_operation.lua` | 批量扣减/增加库存 | 同一脚本通过 `opType` 参数区分操作 |
| `rollback.lua` | 回滚操作（从快照恢复） | 读取 Redis 中保存的快照恢复原值 |
| `cancel.lua` | 取消订单恢复库存 | 直接增加，需幂等检查 |
| `query.lua` | 批量查询库存 | 一次性返回多个 Key 的值 |
| `replenish.lua` | 补货增加库存 | 无状态模式，天然防重 |

### 3.2 batch_operation.lua 执行流程

```
输入: KEYS = [product:stock:1, product:stock:2, ...]
      ARGV = [扣减量1, 扣减量2, ..., bizNo, platformId, timeout, opType]

流程:
  ┌─────────────────────────────────────────────┐
  │ ① 校验参数：KEYS 和数量对应，至少一个商品     │
  │ ② 幂等检查：GET biz:idempotent:{opType}:{bizNo}   │
  │    如果已存在 → 返回 "already_success"       │
  │ ③ 逐商品检查库存是否充足                     │
  │    任一不足 → 返回 "insufficient_value"       │
  │ ④ 执行扣减（INCRBY 负数）/ 增加（INCRBY 正数）│
  │ ⑤ 保存快照：SET biz:snapshot:{opType}:{bizNo}│
  │    快照内容 = {商品ID: 原始库存值}            │
  │ ⑥ 写幂等标记：SET biz:idempotent:{opType}:{bizNo} │
  │    TTL = 配置的超时时间                       │
  │ ⑦ 返回 [1, "success", bizNo, snapshotKey]    │
  └─────────────────────────────────────────────┘
```

### 3.3 快照机制的设计意图

扣减时保存快照 `biz:snapshot:deduct:{bizNo}` = `{1: 100, 2: 50}`：
- **回滚的依据**：取消订单时，Lua 脚本从快照读取原始值进行恢复
- **异常恢复**：即使订单取消了，也能通过快照知道当时扣了多少
- **快照的 TTL 与幂等 Key 一致**：避免永久占用内存

### 3.4 扣减与增加的代码复用

`executeBatchOperation()` 是核心方法，通过 `opType` 参数复用同一段代码：

```java
// 扣减调用
executeBatchOperation(bizNo, platformId, operations, "deduct");

// 增加调用（补货）
executeBatchOperation(bizNo, platformId, operations, "add");
```

好处：
- 扣减和增加共用同一份 Lua 脚本（`batch_operation.lua`）
- 只在 ARGV 中传不同的 opType，脚本内 switch 分支处理
- 减少脚本维护成本，降低 Bug 概率

---

## 四、关键细节

### 4.1 Script SHA 缓存 + 自动回退

```java
// 首次加载：SCRIPT LOAD → 返回 SHA
String sha = rScript.scriptLoad(content);

// 后续执行：EVALSHA sha （无需重复传输脚本内容，节省带宽）
rScript.evalSha(RScript.Mode.READ_WRITE, sha, returnType, keys, args);

// SHA 失效（Redis 重启后）→ 回退到 EVAL（直接传脚本内容）
rScript.eval(RScript.Mode.READ_WRITE, content, returnType, keys, args);
```

- 首次执行时 `SCRIPT LOAD` 上传脚本并缓存 SHA
- 后续用 `EVALSHA` 只需传输 40 字节的 SHA 值（对比脚本内容几百字节）
- 如果 SHA 失效（Redis 重启、主从切换），自动回退 `EVAL` 并重新缓存

### 4.2 StringCodec 的必要性

```java
// 使用 StringCodec 而非默认的 JsonJacksonCodec
RScript script = redissonClient.getScript(StringCodec.INSTANCE);
```

为什么？Redisson 默认使用 `JsonJacksonCodec`，它会尝试把 Redis 返回的字符串当作 JSON 解析。Lua 脚本返回 "success" 或 "already_success" 等普通字符串时，Jackson 会报错（非法 JSON）。

`StringCodec` 将返回值作为原始字符串处理，不做 JSON 解析，由 Java 代码手动解析。

### 4.3 指数退避重试

Lua 脚本执行如果失败（Redis 连接断开等），最多重试 3 次：

```
第1次失败 → 等待 100ms → 第2次 → 等待 200ms → 第3次 → 等待 300ms → 回退 EVAL
```

退避间隔逐次增加，避免瞬时故障时盲目重试加剧 Redis 压力。

---

## 五、面试常见追问

### Q1: Lua 脚本执行时间过长会怎样？

Redis 是单线程的，Lua 脚本执行期间会阻塞其他所有请求。所以：
- 脚本中不应该有循环遍历大量 Key
- 不应该在脚本中进行复杂的计算
- 本项目的脚本只操作传入的 KEYS 列表中的有限个 Key

### Q2: 如果扣减成功但幂等标记写入失败？

这是不可能的——Lua 脚本作为一个整体原子执行，要么全部成功，要么全部失败。Redis 保证脚本执行期间不会部分提交。

### Q3: 为什么不在业务代码中先查 Redis 再写？

那样就回到了 Check-Then-Act 的非原子模式。两个并发请求可能都读到库存=5，然后都判定充足，最终卖出10个 → 超卖。

### Q4: 和 SETNX / WATCH 等其他 Redis 原子方案对比？

| 方案 | 适用场景 | 本项目是否适用 |
|------|----------|----------------|
| SETNX 分布式锁 | 需要跨多个 Redis 命令的原子性 | ❌ 增加网络开销 |
| WATCH + MULTI/EXEC | 简单 CAS 操作 | ❌ 不支持批量操作的条件判断 |
| Lua 脚本 | 复杂的多步条件逻辑 | ✅ 完美匹配 |

---

## 六、关键文件

| 文件 | 说明 |
|------|------|
| `util/LuaScriptManager.java` | 脚本加载/缓存/SHA管理/执行/解析 |
| `resources/lua/batch_operation.lua` | 批量扣减/增加脚本 |
| `resources/lua/cancel.lua` | 取消订单恢复脚本 |
| `resources/lua/replenish.lua` | 补货脚本 |
| `resources/lua/rollback.lua` | 回滚脚本 |
| `resources/lua/query.lua` | 批量查询脚本 |
| `config/StockScriptProperties.java` | 脚本路径/操作类型配置 |

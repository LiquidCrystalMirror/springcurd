# Resilience4j 熔断限流设计

> 面试要点：熔断/限流/重试的区别？Resilience4j vs Hystrix vs Sentinel？滑动窗口 vs 固定窗口？

---

## 一、为什么需要熔断限流？

### 1.1 分布式系统中的雪崩效应

```
下单服务 → Redis（正常）
             ↓ Redis 开始变慢
下单服务 → Redis（慢，线程堆积）
             ↓ 线程池耗尽
下单服务 → Redis（超时）
             ↓ 下单服务不可用
其他服务 → 下单服务 → 连锁故障 💥
```

**熔断器**的作用：当 Redis 连续失败达到阈值 → 自动"跳闸"，快速失败而非等待超时，保护调用方的线程资源。

### 1.2 Resilience4j 的选择理由

| 框架 | 状态 | 特点 |
|------|------|------|
| Netflix Hystrix | 已停更 | 前辈，不再维护 |
| Alibaba Sentinel | 活跃 | 功能强大，但较重，依赖控制台 |
| **Resilience4j** | **活跃** | **轻量，函数式，Java 8+ 原生，Spring Boot 3 友好** |

选择 Resilience4j 的原因：
- 为 Java 8+ 函数式编程设计，和 Spring Boot 3 无缝集成
- 轻量级，无外部依赖（Sentinel 需要 Dashboard）
- 支持 Actuator 端点暴露运行状态

---

## 二、三层保护体系

### 2.1 架构概览

```
请求进入
  │
  ▼
┌─────────────────────────────────┐
│  第1层：RateLimiter（限流）       │
│  - orderCreate: 20 req/s        │
│  - loginRateLimit: 5 req/s      │
│  超过 → 返回 429                │
└──────────────┬──────────────────┘
               │ 通过
               ▼
┌─────────────────────────────────┐
│  第2层：CircuitBreaker（熔断）   │
│  - redisService: 失败率≥50%→熔断 │
│  - rabbitmqService: 静默跳过     │
│  熔断中 → 快速失败               │
└──────────────┬──────────────────┘
               │ 通过/熔断恢复
               ▼
┌─────────────────────────────────┐
│  第3层：Retry + TimeLimiter      │
│  - redisRetry: 最多3次           │
│  - dbRetry: 最多3次              │
│  - redisTimeLimiter: 3秒超时     │
└─────────────────────────────────┘
```

### 2.2 配置详情

```yaml
resilience4j:
  circuitbreaker:
    instances:
      redisService:
        sliding-window-size: 10          # 10次调用为一个统计窗口
        failure-rate-threshold: 50       # 失败率≥50%触发熔断
        wait-duration-in-open-state: 15s # 熔断15秒后尝试半开
        permitted-number-of-calls-in-half-open-state: 3  # 半开时允许3个探测请求

      rabbitmqService:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 15s
        # 降级：记录日志，不抛异常，不阻塞主流程

  ratelimiter:
    instances:
      orderCreate:
        limit-for-period: 20             # 每秒20个请求
        limit-refresh-period: 1s
        timeout-duration: 0              # 不等待，立即拒绝
      loginRateLimit:
        limit-for-period: 5              # 每秒5个请求（防暴力破解）

  retry:
    instances:
      redisRetry:
        max-attempts: 3
        wait-duration: 100ms             # 基础间隔
        exponential-backoff-multiplier: 2 # 100→200→400ms
      dbRetry:
        max-attempts: 3
        wait-duration: 100ms
        exponential-backoff-multiplier: 2

  timelimiter:
    instances:
      redisTimeLimiter:
        timeout-duration: 3s             # 超过3秒自动中断
```

---

## 三、熔断器三态模型

```
        ┌──────────┐
        │  CLOSED  │  ← 正常状态
        │（正常调用）│
        └────┬─────┘
             │ 失败率≥阈值（如 50%）
             ▼
        ┌──────────┐
        │   OPEN   │  ← 熔断状态
        │（快速失败）│     所有请求直接拒绝
        └────┬─────┘
             │ 等待 waitDuration（15s）
             ▼
        ┌──────────────┐
        │  HALF_OPEN   │  ← 半开探测
        │（允许少量请求）│
        └──┬────────┬──┘
           │        │
     探测成功      探测失败
           │        │
           ▼        ▼
       CLOSED     OPEN
```

### 3.1 滑动窗口统计

Resilience4j 使用**基于时间的滑动窗口**（而非固定窗口）：

```
时间轴: |----|----|----|----|----|----|----|----|----|----|
窗口:        [=========10次调用窗口=========]
                  ↓ 窗口滑动 →
                       [=========10次调用窗口=========]
```

对比固定窗口：
- 固定窗口：每个整秒统计一次，00:00:00-00:00:01 算一个窗口
  - 问题：00:00:00.900 → 00:00:01.100 的请求不会被同一个窗口捕获
- 滑动窗口：每次请求都以最近 N 次调用为窗口，实时性好

---

## 四、降级策略

### 4.1 Redis 熔断降级

```java
@CircuitBreaker(name = "redisService", fallbackMethod = "redisFallback")
public String executeRedis(Supplier<String> operation) {
    return operation.get();
}

// 降级方法
public String redisFallback(Throwable t) {
    log.warn("Redis熔断降级: {}", t.getMessage());
    return "Redis服务暂时不可用";
}
```

**降级行为**：返回提示信息，接口本身不报错。用户看到 "Redis服务暂时不可用" 而非 500 错误。

### 4.2 RabbitMQ 熔断降级（静默跳过）

```java
@CircuitBreaker(name = "rabbitmqService", fallbackMethod = "rabbitmqFallback")
public void sendRabbitMessage(Runnable operation) {
    operation.run();
}

public void rabbitmqFallback(Throwable t) {
    // 静默跳过：记录日志，不抛异常，不影响接口返回
    log.warn("RabbitMQ熔断降级（静默跳过）: {}", t.getMessage());
}
```

**为什么 RabbitMQ 是静默跳过？**
- MQ 发送是异步操作，不属于核心业务流程
- 即使 MQ 失败，订单已经创建成功
- 消息体已保存在 `rabbit_message_log` 表中，后续可补偿

---

## 五、面试常见追问

### Q1: Resilience4j 和 Sentinel 怎么选？

- Resilience4j：轻量，适合中小项目，代码侵入小（注解驱动）
- Sentinel：重量级，适合微服务体系，需要 Dashboard，支持复杂流控规则
- 本项目选 Resilience4j 因为 Spring Boot 3 原生支持，且单体项目中 Sentinel 过重

### Q2: 熔断和限流的触发顺序？

限流在前，熔断在后。限流保护系统不被流量冲垮，熔断保护系统不被故障组件拖垮。两者目的不同但互补。

### Q3: 熔断后如何自动恢复？

`waitDurationInOpenState = 15s`：熔断 15 秒后自动进入 HALF_OPEN 状态，允许最多 3 个请求通过（`permittedNumberOfCallsInHalfOpenState=3`）。如果这 3 个请求成功 → 恢复 CLOSED；如果失败 → 重新进入 OPEN，再等 15 秒。

### Q4: 为什么不用 @Retryable 而要单独配置 Resilience4j Retry？

Spring 的 `@Retryable` 也可以，但 Resilience4j 的 Retry 可以和其他组件（熔断器、限流器）统一配置、统一管理，Actuator 也能统一暴露状态。

---

## 六、关键文件

| 文件 | 说明 |
|------|------|
| `service/Resilience4jService.java` | 熔断/限流/重试包装服务 |
| `resources/application-resilience4j.yml` | 全部配置 |
| `pom.xml` | resilience4j-spring-boot3 依赖 |

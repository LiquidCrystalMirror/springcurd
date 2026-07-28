package org.example.springbootdemo.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.exception.ServiceUnavailableException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Resilience4j 熔断/降级/限流/重试 统一服务
 * <p>为关键业务操作提供保护层，防止级联故障</p>
 *
 * <p>面试亮点：</p>
 * <ul>
 *   <li>熔断(CircuitBreaker)：Redis/RabbitMQ 异常率超50%自动熔断，15s后半开探测</li>
 *   <li>降级(Fallback)：熔断后返回友好提示而非系统崩溃</li>
 *   <li>限流(RateLimiter)：订单接口每秒20次，登录接口每秒5次</li>
 *   <li>重试(Retry)：数据库操作失败自动重试3次，指数退避</li>
 * </ul>
 */
@Slf4j
@Service
public class Resilience4jService {

    /**
     * Redis 操作熔断 + 重试保护
     * <p>当 Redis 连续失败率超50%时熔断15秒，期间调用降级方法</p>
     */
    @CircuitBreaker(name = "redisService", fallbackMethod = "redisFallback")
    @Retry(name = "redisRetry", fallbackMethod = "redisRetryFallback")
    public <T> T executeRedis(Supplier<T> operation, String bizNo, String operationName) {
        log.debug("[Resilience4j] Redis操作开始, bizNo={}, operation={}", bizNo, operationName);
        return operation.get();
    }

    /** Redis 熔断降级：返回友好提示 */
    @SuppressWarnings("unused")
    private <T> T redisFallback(Supplier<T> operation, String bizNo, String operationName, Throwable t) {
        log.error("[Resilience4j] Redis熔断触发, bizNo={}, operation={}, reason={}",
                bizNo, operationName, t.getMessage());
        throw new ServiceUnavailableException("Redis服务暂时不可用，库存操作已熔断保护，请稍后重试");
    }

    /** Redis 重试耗尽降级 */
    @SuppressWarnings("unused")
    private <T> T redisRetryFallback(Supplier<T> operation, String bizNo, String operationName, Throwable t) {
        log.error("[Resilience4j] Redis重试耗尽, bizNo={}, operation={}", bizNo, operationName, t);
        throw new ServiceUnavailableException("Redis操作失败（已重试），请稍后重试");
    }

    // ==================== RabbitMQ 熔断 ====================

    /**
     * RabbitMQ 操作熔断保护
     * <p>RabbitMQ 发送失败不阻塞主流程，熔断时记录日志并降级</p>
     */
    @CircuitBreaker(name = "rabbitmqService", fallbackMethod = "rabbitmqFallback")
    public void sendRabbitMessage(Runnable operation, String bizNo, String operationName) {
        log.debug("[Resilience4j] RabbitMQ操作开始, bizNo={}, operation={}", bizNo, operationName);
        operation.run();
    }

    @SuppressWarnings("unused")
    private void rabbitmqFallback(Runnable operation, String bizNo, String operationName, Throwable t) {
        log.warn("[Resilience4j] RabbitMQ熔断降级, bizNo={}, operation={}, 消息发送跳过（不阻塞主流程）",
                bizNo, operationName);
        // RabbitMQ 降级策略：不抛出异常，记录日志，后续可通过定时任务补偿
    }

    // ==================== 限流保护 ====================

    /**
     * 订单创建接口限流：每秒最多20个请求
     */
    @RateLimiter(name = "orderCreate", fallbackMethod = "rateLimitFallback")
    public <T> T executeWithOrderRateLimit(Supplier<T> operation, String bizNo) {
        return operation.get();
    }

    /**
     * 登录接口限流：每秒最多5个请求（防暴力破解）
     */
    @RateLimiter(name = "loginRateLimit", fallbackMethod = "rateLimitFallback")
    public <T> T executeWithLoginRateLimit(Supplier<T> operation) {
        return operation.get();
    }

    @SuppressWarnings("unused")
    private <T> T rateLimitFallback(Supplier<T> operation, String bizNo, Throwable t) {
        log.warn("[Resilience4j] 限流触发, bizNo={}", bizNo);
        throw new ServiceUnavailableException("请求过于频繁，请稍后重试");
    }

    @SuppressWarnings("unused")
    private <T> T rateLimitFallback(Supplier<T> operation, Throwable t) {
        log.warn("[Resilience4j] 限流触发");
        throw new ServiceUnavailableException("请求过于频繁，请稍后重试");
    }

    // ==================== 数据库重试 ====================

    /**
     * 数据库操作重试保护（指数退避）
     */
    @Retry(name = "dbRetry", fallbackMethod = "dbRetryFallback")
    public <T> T executeDbWithRetry(Supplier<T> operation, String bizNo, String operationName) {
        log.debug("[Resilience4j] 数据库操作开始, bizNo={}, operation={}", bizNo, operationName);
        return operation.get();
    }

    @SuppressWarnings("unused")
    private <T> T dbRetryFallback(Supplier<T> operation, String bizNo, String operationName, Throwable t) {
        log.error("[Resilience4j] 数据库重试耗尽, bizNo={}, operation={}", bizNo, operationName, t);
        throw new ServiceUnavailableException("数据库操作失败（已重试），请稍后重试");
    }
}

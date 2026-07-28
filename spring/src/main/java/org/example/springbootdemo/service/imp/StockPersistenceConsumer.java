package org.example.springbootdemo.service.imp;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.config.StockScriptProperties;
import org.example.springbootdemo.util.DemoMessage;
import org.example.springbootdemo.entity.OrderDetail;
import org.example.springbootdemo.entity.ProductStock;
import org.example.springbootdemo.mapper.BizIdempotentMapper;
import org.example.springbootdemo.mapper.OrderDetailMapper;
import org.example.springbootdemo.mapper.ProductStockMapper;
import org.example.springbootdemo.util.StructuredLogger;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

import static org.example.springbootdemo.util.StructuredLogger.LogCategory.*;

/**
 * 库存操作异步持久化消费者（RabbitMQ 版）
 * 从 RabbitMQ 接收消息，将 Redis 库存变更持久化到 MySQL
 * <p>替代了原有的 Redis List 轮询模式，改为消息驱动</p>
 */
@Slf4j
@Component
public class StockPersistenceConsumer {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ProductStockMapper productStockMapper;

    @Resource
    private OrderDetailMapper orderDetailMapper;

    @Resource
    private BizIdempotentMapper bizIdempotentMapper;

    @Resource
    private StockScriptProperties scriptProperties;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== RabbitMQ 监听器 ====================

    /**
     * 监听扣减队列 —— 订单创建成功后异步持久化订单明细和库存
     */
    @RabbitListener(queues = "#{rabbitMQProperties.deductQueue}")
    public void onDeductMessage(DemoMessage message) {
        StructuredLogger.info(ORDER_MYSQL, message.getBizNo(),
                "📥 [RabbitMQ] 收到扣减消息，platformId={}, 商品种类={}",
                message.getPlatformId(),
                message.getOperations() != null ? message.getOperations().size() : 0);

        if (message.getOperations() == null || message.getOperations().isEmpty()) {
            StructuredLogger.warn(ORDER_MYSQL, message.getBizNo(), "扣减消息无商品信息，跳过");
            return;
        }
        persistDeductOrAdd(message.getBizNo(), message.getPlatformId(),
                message.getOperations(), scriptProperties.getType().getDeduct());
    }

    /**
     * 监听增加队列 —— 补货成功后异步同步库存备份表
     */
    @RabbitListener(queues = "#{rabbitMQProperties.addQueue}")
    public void onAddMessage(DemoMessage message) {
        StructuredLogger.info(REPLENISH_MYSQL, message.getBizNo(),
                "📥 [RabbitMQ] 收到增加消息，商品种类={}",
                message.getOperations() != null ? message.getOperations().size() : 0);

        if (message.getOperations() == null || message.getOperations().isEmpty()) {
            StructuredLogger.warn(REPLENISH_MYSQL, message.getBizNo(), "增加消息无商品信息，跳过");
            return;
        }
        persistDeductOrAdd(message.getBizNo(), message.getPlatformId(),
                message.getOperations(), scriptProperties.getType().getAdd());
    }

    /**
     * 监听回滚队列 —— 订单取消后同步库存备份表和订单状态
     */
    @RabbitListener(queues = "#{rabbitMQProperties.rollbackQueue}")
    public void onRollbackMessage(DemoMessage message) {
        StructuredLogger.info(ORDER_MYSQL, message.getBizNo(),
                "📥 [RabbitMQ] 收到回滚消息，platformId={}, 商品种类={}",
                message.getPlatformId(),
                message.getOperations() != null ? message.getOperations().size() : 0);

        if (message.getOperations() == null || message.getOperations().isEmpty()) {
            StructuredLogger.warn(ORDER_MYSQL, message.getBizNo(), "回滚消息无商品信息，跳过");
            return;
        }
        handleRollback(message.getBizNo(), message.getPlatformId(), message.getOperations());
    }

    // ==================== 幂等性控制的持久化逻辑 ====================

    /**
     * 扣减/增加持久化（带数据库幂等性控制）
     * @param bizNo      业务单号
     * @param platformId 平台ID
     * @param operations 商品操作映射：productId → quantity
     * @param opType     操作类型：deduct / add
     */
    private void persistDeductOrAdd(String bizNo, String platformId,
                                     Map<Long, Integer> operations, String opType) {

        // 1. 检查数据库幂等记录
        Integer dbStatus = bizIdempotentMapper.selectStatus(bizNo, opType, platformId);
        if (dbStatus != null) {
            if (dbStatus == 1) {
                StructuredLogger.info(ORDER_MYSQL, bizNo,
                        "数据库幂等检查命中，操作已成功过，跳过处理，opType={}", opType);
                return;
            } else if (dbStatus == 2) {
                StructuredLogger.warn(ORDER_MYSQL, bizNo,
                        "数据库幂等检查命中，操作曾失败，拒绝重试，opType={}", opType);
                return;
            } else if (dbStatus == 0) {
                StructuredLogger.warn(ORDER_MYSQL, bizNo,
                        "操作正在处理中，拒绝重复请求，opType={}", opType);
                return;
            }
        }

        // 2. 插入"处理中"状态（利用唯一索引防止并发重复）
        try {
            bizIdempotentMapper.insert(bizNo, opType, platformId, 0);
            StructuredLogger.debug(ORDER_MYSQL, bizNo,
                    "插入幂等记录（处理中），opType={}", opType);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发冲突：重新查询幂等状态
            dbStatus = bizIdempotentMapper.selectStatus(bizNo, opType, platformId);
            if (dbStatus != null && dbStatus == 1) {
                StructuredLogger.info(ORDER_MYSQL, bizNo, "并发幂等检查命中，跳过处理");
                return;
            }
            // status=0(处理中) 或 status=2(曾失败) 或查不到 → 已由其他线程抢占，拒绝重试
            StructuredLogger.warn(ORDER_MYSQL, bizNo,
                    "并发幂等检查命中但状态非成功(dbStatus={})，拒绝重试", dbStatus);
            return;
        }

        // 3. 执行业务逻辑
        boolean businessSuccess = false;
        try {
            processBusinessLogic(bizNo, platformId, operations, opType);
            businessSuccess = true;
        } finally {
            // 4. 更新最终状态
            try {
                int finalStatus = businessSuccess ? 1 : 2;
                bizIdempotentMapper.updateStatus(bizNo, opType, platformId, finalStatus);
                StructuredLogger.info(ORDER_MYSQL, bizNo,
                        "更新幂等记录状态为: {}", finalStatus == 1 ? "成功" : "失败");
            } catch (Exception e) {
                StructuredLogger.error(ORDER_MYSQL, bizNo, "更新幂等记录状态失败", e);
            }
        }

        // 5. 删除快照（持久化完成后清理 Redis 快照）
        String snapshotKey = "biz:snapshot:" + opType + ":" + bizNo;
        redissonClient.getBucket(snapshotKey, new StringCodec()).delete();
        StructuredLogger.debug(ORDER_MYSQL, bizNo,
                "持久化完成并删除快照: {}", snapshotKey);
    }

    /**
     * 处理业务逻辑：遍历商品，插入订单明细并同步库存备份表
     */
    private void processBusinessLogic(String bizNo, String platformId,
                                       Map<Long, Integer> operations, String opType) {
        for (Map.Entry<Long, Integer> entry : operations.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();
            String redisKey = "product:stock:" + productId;

            // 扣减操作：先插入订单明细（利用唯一索引做幂等性保护）
            if (opType.equals(scriptProperties.getType().getDeduct())) {
                try {
                    StructuredLogger.debug(ORDER_MYSQL, bizNo,
                            "开始插入订单明细，productId={}, quantity={}", productId, quantity);
                    insertOrderDetail(bizNo, platformId, productId, quantity);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    StructuredLogger.info(ORDER_MYSQL, bizNo,
                            "订单明细已存在，跳过库存更新（幂等性保护），productId={}", productId);
                    continue;
                }
            }

            // 获取当前 Redis 中的库存值（用于备份表同步）
            RBucket<Object> bucket = redissonClient.getBucket(redisKey, new StringCodec());
            Object stockValue = bucket.get();
            if (stockValue == null) {
                StructuredLogger.warn(ORDER_MYSQL, bizNo,
                        "Redis key 不存在，跳过: {}，可能原因：商品已被删除或key过期", redisKey);
                continue;
            }
            int currentStock = Integer.parseInt(stockValue.toString());

            // 更新 product_stock 备份表
            StructuredLogger.debug(ORDER_MYSQL, bizNo,
                    "开始更新库存备份表，productId={}, stock={}", productId, currentStock);
            updateProductStock(productId, currentStock);
        }
    }

    // ==================== 回滚处理 ====================

    /**
     * 处理回滚操作：同步库存备份表到当前 Redis 状态，并更新订单状态
     * 注意：此时 Redis 库存已由 cancel.lua 恢复，这里负责将 MySQL 与 Redis 对齐
     */
    private void handleRollback(String bizNo, String platformId, Map<Long, Integer> operations) {
        // 遍历每个商品，从 Redis 读取当前库存并同步到 MySQL
        for (Map.Entry<Long, Integer> entry : operations.entrySet()) {
            Long productId = entry.getKey();
            String redisKey = "product:stock:" + productId;

            // 读取 Redis 当前库存值（cancel.lua 已恢复）
            RBucket<Object> bucket = redissonClient.getBucket(redisKey, new StringCodec());
            Object stockValue = bucket.get();
            if (stockValue == null) {
                StructuredLogger.warn(ORDER_MYSQL, bizNo,
                        "Redis key 不存在，无法同步库存: {}", redisKey);
                continue;
            }
            int currentStock = Integer.parseInt(stockValue.toString());

            try {
                StructuredLogger.info(ORDER_MYSQL, bizNo,
                        "回滚同步商品库存至Redis当前值，productId={}, stock={}", productId, currentStock);
                updateProductStock(productId, currentStock);
            } catch (Exception e) {
                StructuredLogger.error(ORDER_MYSQL, bizNo,
                        "回滚恢复库存失败，productId={}，需人工介入", productId, e);
            }
        }

        // 更新订单状态为已回滚
        updateOrderDetailStatusToRollback(bizNo, platformId);
        StructuredLogger.info(ORDER_MYSQL, bizNo, "回滚操作完成，订单状态已更新为已回滚");
    }

    // ==================== 库存备份表更新（乐观锁） ====================

    private void updateProductStock(Long productId, int newStock) {
        ProductStock stock = productStockMapper.queryById(productId);
        if (stock == null) {
            StructuredLogger.info(REPLENISH_MYSQL, "SYSTEM",
                    "商品库存记录不存在，自动创建新记录，productId={}, stock={}", productId, newStock);
            stock = new ProductStock();
            stock.setProductId(productId);
            stock.setStock(newStock);
            stock.setVersion(0);
            productStockMapper.insert(stock);
            StructuredLogger.info(REPLENISH_MYSQL, "SYSTEM",
                    "商品库存记录创建成功，productId={}", productId);
            return;
        }

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            int currentVersion = stock.getVersion();
            int rows = productStockMapper.updateStockWithVersion(productId, newStock, currentVersion);
            if (rows > 0) {
                StructuredLogger.debug(ORDER_MYSQL, "SYSTEM",
                        "库存备份表更新成功，productId={}, version={}", productId, currentVersion);
                return;
            }

            StructuredLogger.warn(ORDER_MYSQL, "SYSTEM",
                    "乐观锁冲突，重试 {}/{}，productId={}，可能原因：并发更新导致版本不一致",
                    i + 1, maxRetries, productId);
            if (i < maxRetries - 1) {
                try {
                    Thread.sleep(10L * (i + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    StructuredLogger.error(ORDER_MYSQL, "SYSTEM",
                            "库存更新重试被中断，productId={}", productId, e);
                    throw new RuntimeException("重试中断", e);
                }
                stock = productStockMapper.queryById(productId);
                if (stock == null) {
                    StructuredLogger.error(ORDER_MYSQL, "SYSTEM",
                            "库存记录不存在，productId={}，可能原因：记录被其他事务删除", productId);
                    throw new RuntimeException("记录不存在: " + productId);
                }
            }
        }
        StructuredLogger.error(ORDER_MYSQL, "SYSTEM",
                "【严重错误-需人工介入】订单持久化时库存备份表更新最终失败（已重试{}次），productId={}，数据不一致风险！请立即检查并手动修复。可能原因：高并发场景下持续冲突",
                maxRetries, productId);
        throw new RuntimeException("乐观锁重试失败，productId=" + productId);
    }

    // ==================== 订单明细操作 ====================

    private void insertOrderDetail(String bizNo, String platformId, Long productId, int quantity) {
        OrderDetail detail = new OrderDetail();
        detail.setId(System.currentTimeMillis());
        detail.setOrderNo(bizNo);
        detail.setPlatformId(platformId);
        detail.setProductId(productId);
        detail.setQuantity(quantity);
        detail.setStatus(1);
        detail.setCreateTime(LocalDateTime.now());
        detail.setUpdateTime(LocalDateTime.now());

        try {
            orderDetailMapper.insert(detail);
            StructuredLogger.debug(ORDER_MYSQL, bizNo,
                    "订单明细插入成功，productId={}", productId);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            StructuredLogger.debug(ORDER_MYSQL, bizNo,
                    "订单明细已存在，跳过（幂等性保护），productId={}", productId);
        } catch (Exception e) {
            StructuredLogger.error(ORDER_MYSQL, bizNo,
                    "插入订单明细失败，可能原因：唯一约束冲突、外键约束或数据库连接异常", e);
            throw e;
        }
    }

    private void updateOrderDetailStatusToRollback(String bizNo, String platformId) {
        int rows = orderDetailMapper.updateStatusToRollback(bizNo, platformId);
        StructuredLogger.info(ORDER_MYSQL, bizNo,
                "回滚操作更新订单状态完成，影响行数: {}", rows);
    }
}

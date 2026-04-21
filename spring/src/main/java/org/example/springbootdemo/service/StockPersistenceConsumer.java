package org.example.springbootdemo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.config.QueueConsumerProperties;
import org.example.springbootdemo.config.StockScriptProperties;
import org.example.springbootdemo.entity.OrderDetail;
import org.example.springbootdemo.entity.ProductStock;
import org.example.springbootdemo.mapper.BizIdempotentMapper;
import org.example.springbootdemo.mapper.OrderDetailMapper;
import org.example.springbootdemo.mapper.ProductStockMapper;
import org.example.springbootdemo.util.StructuredLogger;
import org.redisson.api.RBucket;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.springbootdemo.util.StructuredLogger.LogCategory.*;


/**
 * 库存操作异步持久化消费者（修正版）
 * 从 Redis List 中拉取包含商品明细的消息，直接使用消息中的数量持久化到 MySQL
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
    private QueueConsumerProperties queueProperties;

    @Resource
    private StockScriptProperties scriptProperties;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(fixedDelay = 2000)
    public void consumeDeductQueue() {
        try {
            StructuredLogger.debug(ORDER_MYSQL, "SYSTEM", 
                    "开始消费扣减队列");
            consumeQueue(queueProperties.getDeductQueue(), scriptProperties.getType().getDeduct());
        } catch (Exception e) {
            StructuredLogger.error(ORDER_MYSQL, "SYSTEM", 
                    "扣减队列消费任务异常，可能原因：Redis连接断开或消息解析失败", e);
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void consumeAddQueue() {
        try {
            StructuredLogger.debug(REPLENISH_MYSQL, "SYSTEM", 
                    "开始消费增加队列");
            consumeQueue(queueProperties.getAddQueue(), scriptProperties.getType().getAdd());
        } catch (Exception e) {
            StructuredLogger.error(REPLENISH_MYSQL, "SYSTEM", 
                    "增加队列消费任务异常，可能原因：Redis连接断开或消息解析失败", e);
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void consumeRollbackQueue() {
        try {
            StructuredLogger.debug(ORDER_MYSQL, "SYSTEM", 
                    "开始消费回滚队列");
            consumeQueue(queueProperties.getRollbackQueue(), "rollback");
        } catch (Exception e) {
            StructuredLogger.error(ORDER_MYSQL, "SYSTEM", 
                    "回滚队列消费任务异常，可能原因：Redis连接断开或消息解析失败", e);
        }
    }

    private void consumeQueue(String queueName, String opType) {
        // 使用 StringCodec 避免 JsonJacksonCodec 反序列化标准 JSON 报错
        RQueue<String> queue = redissonClient.getQueue(queueName, new StringCodec());
        if (queue.isEmpty()) {
            return;
        }

        List<String> rawMessages = new ArrayList<>();
        for (int i = 0; i < queueProperties.getBatchSize(); i++) {
            String raw = queue.poll();
            if (raw == null) break;
            rawMessages.add(raw);
        }

        if (rawMessages.isEmpty()) return;

        StructuredLogger.info(ORDER_MYSQL, "SYSTEM", 
                "开始消费 {} 队列，拉取 {} 条消息", queueName, rawMessages.size());

        int successCount = 0, failCount = 0, discardCount = 0;

        for (String rawMsg : rawMessages) {
            try {
                QueueMessage msg = parseQueueMessage(rawMsg);
                if (msg == null) {
                    StructuredLogger.error(ORDER_MYSQL, "UNKNOWN", 
                            "队列消息格式错误，丢弃，可能原因：JSON格式不正确或字段缺失");
                    discardCount++;
                    continue;
                }
                persistBizNo(msg, opType);
                successCount++;
            } catch (Exception e) {
                failCount++;
                String bizNo = "UNKNOWN";
                try {
                    QueueMessage tempMsg = parseQueueMessage(rawMsg);
                    if (tempMsg != null) bizNo = tempMsg.getBizNo();
                } catch (Exception ignored) {}
                
                StructuredLogger.error(ORDER_MYSQL, bizNo, 
                        "持久化失败，消息重新入队，可能原因：数据库连接超时、唯一约束冲突或乐观锁失败", e);
                queue.add(rawMsg);
            }
        }

        StructuredLogger.info(ORDER_MYSQL, "SYSTEM", 
                "消费 {} 队列完成，成功: {}, 失败(重试): {}, 丢弃: {}",
                queueName, successCount, failCount, discardCount);
    }

    private void persistBizNo(QueueMessage msg, String opType) {
        String bizNo = msg.getBizNo();
        String platformId = msg.getPlatformId();
        List<QueueMessage.Item> items = msg.getItems();

        // 扣减/增加操作：先检查数据库幂等性，再处理业务
        if (opType.equals(scriptProperties.getType().getDeduct()) ||
                opType.equals(scriptProperties.getType().getAdd())) {

            // 1. 检查数据库幂等记录
            Integer dbStatus = bizIdempotentMapper.selectStatus(bizNo, opType, platformId);
            if (dbStatus != null) {
                if (dbStatus == 1) {
                    StructuredLogger.info(ORDER_MYSQL, bizNo, 
                            "数据库幂等检查命中，操作已成功过，跳过处理，opType={}", opType);
                    return; // 已成功，直接返回
                } else if (dbStatus == 2) {
                    StructuredLogger.warn(ORDER_MYSQL, bizNo, 
                            "数据库幂等检查命中，操作曾失败，拒绝重试，opType={}", opType);
                    return; // 曾失败，拒绝重试
                } else if (dbStatus == 0) {
                    // status == 0 表示处理中，可能是上次请求还在处理或崩溃了
                    StructuredLogger.warn(ORDER_MYSQL, bizNo, 
                            "操作正在处理中，拒绝重复请求，opType={}", opType);
                    return; // 处理中，拒绝重复请求
                }
            }

            // 2. 插入“处理中”状态（利用唯一索引防止并发重复）
            try {
                bizIdempotentMapper.insert(bizNo, opType, platformId, 0);
                StructuredLogger.debug(ORDER_MYSQL, bizNo, 
                        "插入幂等记录（处理中），opType={}", opType);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发情况下，另一个线程已经插入，再次查询状态
                dbStatus = bizIdempotentMapper.selectStatus(bizNo, opType, platformId);
                if (dbStatus != null && dbStatus == 1) {
                    StructuredLogger.info(ORDER_MYSQL, bizNo, 
                            "并发幂等检查命中，跳过处理");
                    return;
                }
                // 如果是其他状态，继续执行
            }

            // 3. 执行业务逻辑
            boolean businessSuccess = false;
            try {
                processBusinessLogic(bizNo, platformId, items, opType);
                businessSuccess = true;
            } finally {
                // 4. 更新最终状态
                try {
                    int finalStatus = businessSuccess ? 1 : 2;
                    bizIdempotentMapper.updateStatus(bizNo, opType, platformId, finalStatus);
                    StructuredLogger.info(ORDER_MYSQL, bizNo, 
                            "更新幂等记录状态为: {}", finalStatus == 1 ? "成功" : "失败");
                } catch (Exception e) {
                    StructuredLogger.error(ORDER_MYSQL, bizNo, 
                            "更新幂等记录状态失败", e);
                }
            }

        } else if ("rollback".equals(opType)) {
            // 回滚操作：恢复库存并更新订单状态
            StructuredLogger.info(ORDER_MYSQL, bizNo, 
                    "开始执行回滚操作，恢复库存并更新订单状态");
            handleRollback(bizNo, platformId, items);
        }

        // 删除快照（扣减/增加的快照key为 biz:snapshot:{opType}:{bizNo}）
        if (!"rollback".equals(opType)) {
            String snapshotKey = "biz:snapshot:" + opType + ":" + bizNo;
            redissonClient.getBucket(snapshotKey, new StringCodec()).delete();
            StructuredLogger.debug(ORDER_MYSQL, bizNo, 
                    "持久化完成并删除快照: {}", snapshotKey);
        }
    }

    /**
     * 处理业务逻辑（提取出来方便事务管理）
     */
    private void processBusinessLogic(String bizNo, String platformId, 
                                      List<QueueMessage.Item> items, String opType) {
        for (QueueMessage.Item item : items) {
            String redisKey = String.valueOf(item.getKey());
            int quantity = item.getQuantity();

            Long productId = extractProductId(redisKey);
            if (productId == null) {
                StructuredLogger.error(ORDER_MYSQL, bizNo, 
                        "无法从key中提取商品ID: {}，可能原因：key格式不符合预期", redisKey);
                continue;
            }

            // 扣减操作：先插入订单明细（利用唯一索引做幂等性保护）
            if (opType.equals(scriptProperties.getType().getDeduct())) {
                try {
                    StructuredLogger.debug(ORDER_MYSQL, bizNo, 
                            "开始插入订单明细，productId={}, quantity={}", productId, quantity);
                    insertOrderDetail(bizNo, platformId, productId, quantity);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    // 订单已存在，说明是重复请求，跳过后续处理
                    StructuredLogger.info(ORDER_MYSQL, bizNo, 
                            "订单明细已存在，跳过库存更新（幂等性保护），productId={}", productId);
                    continue; // 跳过该商品的库存更新
                }
            }

            // 获取当前 Redis 中的库存值（用于备份表）
            RBucket<Object> bucket = redissonClient.getBucket(redisKey, new StringCodec());
            Object stockValue = bucket.get();
            if (stockValue == null) {
                StructuredLogger.warn(ORDER_MYSQL, bizNo, 
                        "Redis key 不存在，跳过: {}，可能原因：商品已被删除或key过期", redisKey);
                continue;
            }
            int currentStock = Integer.parseInt(stockValue.toString());

            // 更新 product_stock 备份表（只有在订单明细插入成功后才执行）
            StructuredLogger.debug(ORDER_MYSQL, bizNo, 
                    "开始更新库存备份表，productId={}, stock={}", productId, currentStock);
            updateProductStock(productId, currentStock);
        }
    }

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
                try { Thread.sleep(10L * (i + 1)); } catch (InterruptedException e) {
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
                "乐观锁重试失败，productId={}，可能原因：高并发场景下持续冲突", productId);
        throw new RuntimeException("乐观锁重试失败，productId=" + productId);
    }

    private void insertOrderDetail(String bizNo, String platformId, Long productId, int quantity) {
        OrderDetail detail = new OrderDetail();
        detail.setId(System.currentTimeMillis()); // 使用时间戳作为ID
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

    /**
     * 处理回滚操作：恢复MySQL库存并更新订单状态
     * @param bizNo 业务单号
     * @param platformId 平台ID
     * @param items 回滚商品列表（包含回滚前的库存快照）
     */
    private void handleRollback(String bizNo, String platformId, List<QueueMessage.Item> items) {
        if (items == null || items.isEmpty()) {
            StructuredLogger.warn(ORDER_MYSQL, bizNo, 
                    "回滚消息中没有商品信息，尝试从Redis快照读取");
            // 如果消息中没有items，尝试从Redis快照读取
            items = loadItemsFromSnapshot(bizNo, "deduct");
            if (items == null || items.isEmpty()) {
                StructuredLogger.error(ORDER_MYSQL, bizNo, 
                        "无法获取回滚商品信息，回滚失败");
                return;
            }
        }

        // 遍历每个商品，恢复库存
        for (QueueMessage.Item item : items) {
            String redisKey = String.valueOf(item.getKey());
            Long productId = extractProductId(redisKey);
            if (productId == null) {
                StructuredLogger.error(ORDER_MYSQL, bizNo, 
                        "无法从key中提取商品ID: {}", redisKey);
                continue;
            }

            // item.getQuantity() 存储的是回滚前的库存值（原始值）
            int originalStock = item.getQuantity();
            
            try {
                // 恢复MySQL库存备份表到原始值
                StructuredLogger.info(ORDER_MYSQL, bizNo, 
                        "恢复商品库存，productId={}, 原始库存={}", productId, originalStock);
                updateProductStock(productId, originalStock);
            } catch (Exception e) {
                StructuredLogger.error(ORDER_MYSQL, bizNo, 
                        "恢复库存失败，productId={}，需人工介入", productId, e);
                // 继续处理其他商品，不因单个商品失败而中断
            }
        }

        // 所有商品库存恢复完成后，更新订单状态
        updateOrderDetailStatusToRollback(bizNo, platformId);
        StructuredLogger.info(ORDER_MYSQL, bizNo, 
                "回滚操作完成，订单状态已更新为已回滚");
    }

    /**
     * 从Redis快照加载商品信息
     * @param bizNo 业务单号
     * @param opType 操作类型
     * @return 商品列表
     */
    private List<QueueMessage.Item> loadItemsFromSnapshot(String bizNo, String opType) {
        try {
            String snapshotKey = "biz:snapshot:" + opType + ":" + bizNo;
            RBucket<String> bucket = redissonClient.getBucket(snapshotKey, new StringCodec());
            String snapshotJson = bucket.get();
            
            if (snapshotJson == null) {
                StructuredLogger.warn(ORDER_MYSQL, bizNo, 
                        "Redis快照不存在: {}", snapshotKey);
                return null;
            }

            // 解析快照JSON：{"product:stock:1": "590", "product:stock:2": "294"}
            Map<String, Object> snapshot = objectMapper.readValue(
                snapshotJson, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );

            List<QueueMessage.Item> items = new ArrayList<>();
            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                QueueMessage.Item item = new QueueMessage.Item();
                item.setKey(entry.getKey());
                // 将原始库存值存入quantity字段
                item.setQuantity(Integer.parseInt(entry.getValue().toString()));
                items.add(item);
            }

            StructuredLogger.info(ORDER_MYSQL, bizNo, 
                    "从快照加载商品信息成功，共{}个商品", items.size());
            return items;
        } catch (Exception e) {
            StructuredLogger.error(ORDER_MYSQL, bizNo, 
                    "解析快照失败", e);
            return null;
        }
    }

    private void updateOrderDetailStatusToRollback(String bizNo, String platformId) {
        int rows = orderDetailMapper.updateStatusToRollback(bizNo, platformId);
        StructuredLogger.info(ORDER_MYSQL, bizNo, 
                "回滚操作更新订单状态完成，影响行数: {}", rows);
    }

    private Long extractProductId(String redisKey) {
        try {
            String[] parts = redisKey.split(":");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 内部类 ----------
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class QueueMessage {
        private String bizNo;
        private String platformId;
        private List<Item> items;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        private static class Item {
            private Object key;  // Lua 脚本可能返回 String 或 Integer
            private int quantity;
        }
    }

    private QueueMessage parseQueueMessage(String json) {
        try {
            return objectMapper.readValue(json, QueueMessage.class);
        } catch (Exception e) {
            return null;
        }
    }
}
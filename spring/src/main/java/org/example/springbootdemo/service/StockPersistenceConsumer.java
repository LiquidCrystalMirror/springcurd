package org.example.springbootdemo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.config.QueueConsumerProperties;
import org.example.springbootdemo.config.StockScriptProperties;
import org.example.springbootdemo.entity.OrderDetail;
import org.example.springbootdemo.entity.ProductStock;
import org.example.springbootdemo.mapper.OrderDetailMapper;
import org.example.springbootdemo.mapper.ProductStockMapper;
import org.redisson.api.RList;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 库存操作异步持久化消费者
 * 从 Redis List 中拉取业务单号，将操作结果持久化到 MySQL
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
    private QueueConsumerProperties queueProperties;

    @Resource
    private StockScriptProperties scriptProperties;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 消费扣减队列（每2秒执行一次）
     */
    @Scheduled(fixedDelay = 2000)
    public void consumeDeductQueue() {
        consumeQueue(queueProperties.getDeductQueue(), scriptProperties.getType().getDeduct());
    }

    /**
     * 消费增加队列（每2秒执行一次）
     */
    @Scheduled(fixedDelay = 2000)
    public void consumeAddQueue() {
        consumeQueue(queueProperties.getAddQueue(), scriptProperties.getType().getAdd());
    }

    /**
     * 通用队列消费逻辑
     * @param queueName 队列名称
     * @param opType    操作类型（deduct / add）
     */
    private void consumeQueue(String queueName, String opType) {
        RQueue<String> queue = redissonClient.getQueue(queueName);  // 改用 getQueue
        if (queue.isEmpty()) {
            return;
        }

        List<String> bizNos = new ArrayList<>();
        for (int i = 0; i < queueProperties.getBatchSize(); i++) {
            String bizNo = queue.poll();
            if (bizNo == null) break;
            bizNos.add(bizNo);
        }

        if (bizNos.isEmpty()) return;

        log.info("开始消费 {} 队列，拉取 {} 条消息", queueName, bizNos.size());

        int successCount = 0;
        int failCount = 0;

        for (String bizNo : bizNos) {
            try {
                persistBizNo(bizNo, opType);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("持久化失败，bizNo: {}, opType: {}", bizNo, opType, e);
                // 重新放回队列尾部，等待重试（防止消息丢失）
                queue.add(bizNo);
            }
        }

        log.info("消费 {} 队列完成，成功: {}, 失败: {}", queueName, successCount, failCount);
    }

    /**
     * 持久化单个业务单号的操作
     * @param bizNo  业务单号
     * @param opType 操作类型
     */
    private void persistBizNo(String bizNo, String opType) {
        // 1. 构建快照key并获取快照内容
        String snapshotKey = "biz:snapshot:" + opType + ":" + bizNo;
        String snapshotJson = redissonClient.<String>getBucket(snapshotKey).get();
        if (snapshotJson == null) {
            log.warn("快照不存在，可能已过期或被删除，bizNo: {}, snapshotKey: {}", bizNo, snapshotKey);
            return;
        }

        // 2. 解析快照：Map<redisKey, 原始值>
        Map<String, String> snapshot;
        try {
            snapshot = objectMapper.readValue(snapshotJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("解析快照JSON失败，bizNo: {}, snapshotJson: {}", bizNo, snapshotJson, e);
            return;
        }

        // 3. 遍历每个商品，计算变化量并持久化
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            String redisKey = entry.getKey();
            int originalStock = Integer.parseInt(entry.getValue());

            // 获取当前Redis中的库存值
            String currentStockStr = redissonClient.<String>getBucket(redisKey).get();
            if (currentStockStr == null) {
                log.warn("Redis key 不存在，跳过处理: {}", redisKey);
                continue;
            }
            int currentStock = Integer.parseInt(currentStockStr);

            // 计算变化量（绝对值）
            int change = Math.abs(originalStock - currentStock);
            if (change == 0) {
                log.debug("库存无变化，跳过: {}, original: {}, current: {}", redisKey, originalStock, currentStock);
                continue;
            }

            // 提取商品ID（假设key格式为 product:stock:1001）
            Long productId = extractProductId(redisKey);
            if (productId == null) {
                log.warn("无法从key中提取商品ID: {}", redisKey);
                continue;
            }

            // 4. 更新 product_stock 表（备份值）
            updateProductStock(productId, currentStock);

            // 5. 插入订单明细（仅扣减操作需要记录订单）
            if (opType.equals(scriptProperties.getType().getDeduct())) {
                insertOrderDetail(bizNo, productId, change);
            }
            // 增加操作通常不记录订单明细，可根据业务需求决定
        }

        // 6. 持久化完成后删除快照（可选，节省Redis内存）
        redissonClient.getBucket(snapshotKey).delete();
        log.debug("持久化完成并删除快照: {}", snapshotKey);
    }

    /**
     * 更新商品库存备份表（带乐观锁重试）
     */
    private void updateProductStock(Long productId, int newStock) {
        // 先查询当前记录及版本号
        ProductStock stock = productStockMapper.queryById(productId);
        if (stock == null) {
            // 不存在则插入
            stock = new ProductStock();
            stock.setProductId(productId);
            stock.setStock(newStock);
            stock.setVersion(0);
            productStockMapper.insert(stock);
            return;
        }

        // 存在则使用乐观锁更新
        int currentVersion = stock.getVersion();
        int rows = productStockMapper.updateStockWithVersion(productId, newStock, currentVersion);
        if (rows == 0) {
            // 乐观锁冲突，重试一次
            log.warn("乐观锁冲突，重试更新 productId: {}", productId);
            stock = productStockMapper.queryById(productId);
            if (stock != null) {
                productStockMapper.updateStockWithVersion(productId, newStock, stock.getVersion());
            }
        }
    }

    /**
     * 插入订单明细
     */
    private void insertOrderDetail(String bizNo, Long productId, int quantity) {
        OrderDetail detail = new OrderDetail();
        detail.setOrderNo(bizNo);
        detail.setPlatformId(extractPlatformId(bizNo)); // 需根据业务规则实现
        detail.setProductId(productId);
        detail.setQuantity(quantity);
        detail.setStatus(1); // 正常
        detail.setCreateTime(LocalDateTime.now());
        detail.setUpdateTime(LocalDateTime.now());

        try {
            orderDetailMapper.insert(detail);
        } catch (Exception e) {
            // 可能因为唯一约束冲突（已存在记录），此时忽略
            log.warn("订单明细插入失败（可能已存在）: bizNo={}, productId={}", bizNo, productId, e);
        }
    }

    /**
     * 从Redis key中提取商品ID
     * 假设key格式为 "product:stock:1001"
     */
    private Long extractProductId(String redisKey) {
        try {
            String[] parts = redisKey.split(":");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从业务单号中提取平台标识
     * 示例：假设bizNo格式为 "ORDER_20250301_001"，可提取固定前缀
     */
    private String extractPlatformId(String bizNo) {
        // 简化处理，实际可根据业务规则解析
        return "DEFAULT_PLATFORM";
    }
}
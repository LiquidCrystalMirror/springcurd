package org.example.springbootdemo.service.imp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.config.RabbitMQProperties;
import org.example.springbootdemo.util.DemoMessage;
import org.example.springbootdemo.entity.RabbitMessageLog;
import org.example.springbootdemo.entity.RabbitMessageOrderLog;
import org.example.springbootdemo.mapper.RabbitMessageLogMapper;
import org.example.springbootdemo.mapper.RabbitMessageOrderLogMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * RabbitMQ 消息生产者
 * 流程：先写本地消息表（status=0） → 发送到 RabbitMQ → 更新状态为成功/失败
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMQDemoProducer {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties properties;
    private final RabbitMessageLogMapper messageLogMapper;
    private final RabbitMessageOrderLogMapper orderLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // ==================== 业务集成方法 ====================

    /** 发送扣减消息（订单创建成功后调用） */
    public void sendDeduct(String bizNo, String platformId, Map<Long, Integer> operations) {
        DemoMessage msg = new DemoMessage();
        msg.setBizNo(bizNo);
        msg.setOpType("deduct");
        msg.setPlatformId(platformId);
        msg.setOperations(operations);
        msg.setContent("订单扣减, 商品种类=" + operations.size());
        msg.setSendTime(LocalDateTime.now());
        send(msg, properties.getDeductRoutingKey());
    }

    /** 发送增加消息（补货成功后调用） */
    public void sendAdd(String batchId, Map<Long, Integer> operations) {
        DemoMessage msg = new DemoMessage();
        msg.setBizNo(batchId);
        msg.setOpType("add");
        msg.setOperations(operations);
        msg.setContent("补货增加, 商品种类=" + operations.size());
        msg.setSendTime(LocalDateTime.now());
        send(msg, properties.getAddRoutingKey());
    }

    /** 发送回滚消息（订单取消成功后调用） */
    public void sendRollback(String bizNo, String platformId, Long productId, Integer quantity) {
        DemoMessage msg = new DemoMessage();
        msg.setBizNo(bizNo);
        msg.setOpType("rollback");
        msg.setPlatformId(platformId);
        msg.setOperations(Map.of(productId, quantity));
        msg.setContent("订单回滚, productId=" + productId + ", quantity=" + quantity);
        msg.setSendTime(LocalDateTime.now());
        send(msg, properties.getRollbackRoutingKey());
    }

    // ==================== 核心发送逻辑 ====================

    private void send(DemoMessage message, String routingKey) {
        String messageId = UUID.randomUUID().toString();
        String messageBody;
        try {
            messageBody = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("❌ [RabbitMQ] 序列化失败, bizNo={}, error={}", message.getBizNo(), e.getMessage());
            return;
        }

        // 根据 opType 决定写入哪张消息表：订单消息（deduct/rollback）→ 订单专用表，其他 → 通用表
        boolean isOrderMsg = "deduct".equals(message.getOpType()) || "rollback".equals(message.getOpType());
        String msgType = isOrderMsg ? "order" : message.getOpType();

        // 1. 本地消息表（写入失败不阻塞发送，仅记日志）
        if (isOrderMsg) {
            RabbitMessageOrderLog logEntity = new RabbitMessageOrderLog();
            logEntity.setMessageId(messageId);
            logEntity.setMessageType(msgType);
            logEntity.setBizNo(message.getBizNo());
            logEntity.setExchange(properties.getExchange());
            logEntity.setRoutingKey(routingKey);
            logEntity.setMessageBody(messageBody);
            logEntity.setStatus(0);
            logEntity.setRetryCount(0);
            logEntity.setCreateTime(LocalDateTime.now());
            logEntity.setUpdateTime(LocalDateTime.now());
            try {
                orderLogMapper.insert(logEntity);
                log.info("📝 [RabbitMQ] 订单消息表写入, messageId={}, opType={}, bizNo={}",
                        messageId, message.getOpType(), message.getBizNo());
            } catch (Exception dbEx) {
                log.warn("⚠️ [RabbitMQ] 订单消息表写入失败（不影响发送）, messageId={}, error={}",
                        messageId, dbEx.getMessage());
            }
        } else {
            RabbitMessageLog logEntity = new RabbitMessageLog();
            logEntity.setMessageId(messageId);
            logEntity.setMessageType(msgType);
            logEntity.setBizNo(message.getBizNo());
            logEntity.setExchange(properties.getExchange());
            logEntity.setRoutingKey(routingKey);
            logEntity.setMessageBody(messageBody);
            logEntity.setStatus(0);
            logEntity.setRetryCount(0);
            logEntity.setCreateTime(LocalDateTime.now());
            logEntity.setUpdateTime(LocalDateTime.now());
            try {
                messageLogMapper.insert(logEntity);
                log.info("📝 [RabbitMQ] 通用消息表写入, messageId={}, opType={}, bizNo={}",
                        messageId, message.getOpType(), message.getBizNo());
            } catch (Exception dbEx) {
                log.warn("⚠️ [RabbitMQ] 通用消息表写入失败（不影响发送）, messageId={}, error={}",
                        messageId, dbEx.getMessage());
            }
        }

        // 2. 发送（携带 CorrelationData，Broker 异步确认后由 ConfirmCallback 更新 DB 状态）
        try {
            rabbitTemplate.convertAndSend(properties.getExchange(), routingKey, message,
                    new org.springframework.amqp.rabbit.connection.CorrelationData(messageId));
            log.info("📤 [RabbitMQ] 已投递, 等待Broker确认, messageId={}, exchange={}, routingKey={}, bizNo={}, content={}",
                    messageId, properties.getExchange(), routingKey, message.getBizNo(), message.getContent());
        } catch (Exception e) {
            // 投递阶段就失败（如连接断开），直接标记失败，不会触发 ConfirmCallback
            if (isOrderMsg) {
                orderLogMapper.updateFailed(messageId, 2, e.getMessage(), 1);
            } else {
                messageLogMapper.updateFailed(messageId, 2, e.getMessage(), 1);
            }
            log.error("❌ [RabbitMQ] 投递失败, messageId={}, bizNo={}, error={}",
                    messageId, message.getBizNo(), e.getMessage());
        }
    }

    // ==================== 演示用独立发送 ====================

    public void sendDeductMessage(String bizNo, String content) {
        DemoMessage msg = new DemoMessage();
        msg.setBizNo(bizNo);
        msg.setOpType("deduct");
        msg.setContent(content);
        msg.setSendTime(LocalDateTime.now());
        send(msg, properties.getDeductRoutingKey());
    }

    public void sendAddMessage(String bizNo, String content) {
        DemoMessage msg = new DemoMessage();
        msg.setBizNo(bizNo);
        msg.setOpType("add");
        msg.setContent(content);
        msg.setSendTime(LocalDateTime.now());
        send(msg, properties.getAddRoutingKey());
    }

    public void sendRollbackMessage(String bizNo, String content) {
        DemoMessage msg = new DemoMessage();
        msg.setBizNo(bizNo);
        msg.setOpType("rollback");
        msg.setContent(content);
        msg.setSendTime(LocalDateTime.now());
        send(msg, properties.getRollbackRoutingKey());
    }
}

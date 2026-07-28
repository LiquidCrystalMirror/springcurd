package org.example.springbootdemo.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.mapper.RabbitMessageLogMapper;
import org.example.springbootdemo.mapper.RabbitMessageOrderLogMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    private final ConnectionFactory connectionFactory;
    private final RabbitMQProperties rabbitMQProperties;
    private final RabbitMessageLogMapper messageLogMapper;
    private final RabbitMessageOrderLogMapper orderLogMapper;

    // ==================== 交换机 ====================

    @Bean
    public DirectExchange demoDirectExchange() {
        return new DirectExchange(rabbitMQProperties.getExchange(), true, false);
    }

    // ==================== 队列（持久化） ====================

    @Bean
    public Queue deductQueue() {
        return new Queue(rabbitMQProperties.getDeductQueue(), true, false, false);
    }

    @Bean
    public Queue addQueue() {
        return new Queue(rabbitMQProperties.getAddQueue(), true, false, false);
    }

    @Bean
    public Queue rollbackQueue() {
        return new Queue(rabbitMQProperties.getRollbackQueue(), true, false, false);
    }

    // ==================== 绑定 ====================

    @Bean
    public Binding deductBinding() {
        return BindingBuilder.bind(deductQueue())
                .to(demoDirectExchange())
                .with(rabbitMQProperties.getDeductRoutingKey());
    }

    @Bean
    public Binding addBinding() {
        return BindingBuilder.bind(addQueue())
                .to(demoDirectExchange())
                .with(rabbitMQProperties.getAddRoutingKey());
    }

    @Bean
    public Binding rollbackBinding() {
        return BindingBuilder.bind(rollbackQueue())
                .to(demoDirectExchange())
                .with(rabbitMQProperties.getRollbackRoutingKey());
    }

    // ==================== JSON 消息转换器 ====================

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter);

        // ===== Publisher Confirm：Broker 确认回调（异步写 DB，status=0→1 或 0→2） =====
        rabbitTemplate.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            if (correlationData == null) return;
            String messageId = correlationData.getId();
            if (ack) {
                // Broker 确认接收：更新两张表（messageId 为 UUID，仅一张表命中）
                messageLogMapper.updateSuccess(messageId, 1);
                orderLogMapper.updateSuccess(messageId, 1);
                log.info("✅ [Publisher Confirm] Broker 已确认接收, messageId={}", messageId);
            } else {
                // Broker 拒绝：更新两张表为失败（status=2）
                String errMsg = "Broker NACK: " + cause;
                messageLogMapper.updateFailed(messageId, 2, errMsg, 0);
                orderLogMapper.updateFailed(messageId, 2, errMsg, 0);
                log.error("❌ [Publisher Confirm] Broker 拒绝接收, messageId={}, cause={}", messageId, cause);
            }
        });

        // ===== Return Callback：消息无法路由到队列时回调 =====
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("❌ [Return Callback] 消息无法路由! exchange={}, routingKey={}, replyCode={}, replyText={}, body={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText(),
                    new String(returned.getMessage().getBody()));
        });

        return rabbitTemplate;
    }

    // ==================== 连接监听 ====================

    @Bean
    public ConnectionListener connectionListener() {
        return new ConnectionListener() {
            @Override
            public void onCreate(org.springframework.amqp.rabbit.connection.Connection connection) {
                log.info("✅ RabbitMQ 连接已建立: {}", connection);
            }

            @Override
            public void onClose(org.springframework.amqp.rabbit.connection.Connection connection) {
                log.warn("⚠️ RabbitMQ 连接已关闭: {}", connection);
            }
        };
    }

    @PostConstruct
    public void verifyConnection() {
        try {
            var conn = connectionFactory.createConnection();
            log.info("✅ RabbitMQ 连接验证成功");
            conn.close();
        } catch (Exception e) {
            log.error("❌ RabbitMQ 连接失败: {}", e.getMessage());
        }
    }
}

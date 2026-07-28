package org.example.springbootdemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 演示用配置属性
 * 命名遵循现有 async:queue:* 的模式
 */
@Data
@Component
@ConfigurationProperties(prefix = "demo.rabbitmq")
public class RabbitMQProperties {

    /** 直连交换机名称 */
    private String exchange = "demo.direct.exchange";

    /** 扣减队列 */
    private String deductQueue = "demo.queue.deduct";
    /** 扣减路由键 */
    private String deductRoutingKey = "demo.routing.deduct";

    /** 增加队列 */
    private String addQueue = "demo.queue.add";
    /** 增加路由键 */
    private String addRoutingKey = "demo.routing.add";

    /** 回滚队列 */
    private String rollbackQueue = "demo.queue.rollback";
    /** 回滚路由键 */
    private String rollbackRoutingKey = "demo.routing.rollback";
}

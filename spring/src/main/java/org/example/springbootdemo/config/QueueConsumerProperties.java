package org.example.springbootdemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 消息队列消费者配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "stock.queue")
public class QueueConsumerProperties {
    /**
     * 每次拉取的最大消息数量
     */
    private int batchSize = 100;

    /**
     * 扣减队列名称
     */
    private String deductQueue = "async:queue:deduct";

    /**
     * 增加队列名称
     */
    private String addQueue = "async:queue:add";

    /**
     * 回滚队列名称（Lua脚本中写死的，这里预留）
     */
    private String rollbackQueue = "async:queue:rollback";
}
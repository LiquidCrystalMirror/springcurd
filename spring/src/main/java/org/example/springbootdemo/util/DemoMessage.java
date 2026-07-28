package org.example.springbootdemo.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * RabbitMQ 消息体 (JSON)
 * 携带完整业务上下文，供消费者日志追踪
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemoMessage {

    /** 业务单号（订单号或批次号） */
    private String bizNo;

    /** 操作类型：deduct / add / rollback */
    private String opType;

    /** 平台ID（扣减/回滚时有值） */
    private String platformId;

    /** 商品操作明细：productId → quantity */
    private Map<Long, Integer> operations;

    /** 补充描述 */
    private String content;

    /** 发送时间 */
    private LocalDateTime sendTime;
}

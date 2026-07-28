package org.example.springbootdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表 —— RabbitMQ 消息发送记录
 * 用于可靠消息投递：先写表，再发送，发送成功后更新状态
 */
@Data
@TableName("rabbit_message_log")
public class RabbitMessageLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 消息唯一ID (UUID) */
    private String messageId;

    /** 消息类型：replenish=补货, stock=库存, user=用户 */
    private String messageType;

    /** 关联业务单号 */
    private String bizNo;

    /** 交换机名称 */
    private String exchange;

    /** 路由键 */
    private String routingKey;

    /** 消息体 (JSON) */
    private String messageBody;

    /**
     * 状态：
     * 0 - 待发送
     * 1 - 已发送成功
     * 2 - 发送失败
     */
    private Integer status;

    /** 重试次数 */
    private Integer retryCount;

    /** 错误信息 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

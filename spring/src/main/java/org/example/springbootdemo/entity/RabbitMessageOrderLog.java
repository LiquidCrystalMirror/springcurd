package org.example.springbootdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单消息日志表 —— 高流量订单消息独立存储
 * 与 rabbit_message_log（通用消息）分离，避免订单海量消息拖慢其他业务消息的查询
 */
@Data
@TableName("rabbit_message_order_log")
public class RabbitMessageOrderLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 消息唯一ID (UUID) */
    private String messageId;

    /** 消息类型（固定为 order） */
    private String messageType;

    /** 关联订单号(order_no) */
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
